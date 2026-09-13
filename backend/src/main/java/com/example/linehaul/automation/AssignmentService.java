package com.example.linehaul.automation;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.linehaul.automation.ai.AiAdvisor;
import com.example.linehaul.dto.AssignRequest;
import com.example.linehaul.dto.AssignmentAdvice;
import com.example.linehaul.dto.AssignmentResult;
import com.example.linehaul.dto.BatchAssignmentReport;
import com.example.linehaul.dto.CreateAndAssignRequest;
import com.example.linehaul.dto.NewRouteAdvice;
import com.example.linehaul.dto.PipelineStep;
import com.example.linehaul.dto.ResourceOption;
import com.example.linehaul.dto.RouteRecommendation;
import com.example.linehaul.dto.RouteRejection;
import com.example.linehaul.exception.BusinessException;
import com.example.linehaul.model.Driver;
import com.example.linehaul.model.Order;
import com.example.linehaul.model.Route;
import com.example.linehaul.model.Vehicle;
import com.example.linehaul.model.Warehouse;
import com.example.linehaul.service.DriverService;
import com.example.linehaul.service.LaneDurationService;
import com.example.linehaul.service.LinehaulUtil;
import com.example.linehaul.service.OrderService;
import com.example.linehaul.service.RouteService;
import com.example.linehaul.service.VehicleService;
import com.example.linehaul.service.WarehouseScope;
import com.example.linehaul.service.WarehouseService;

/**
 * The use case layer of the smart assignment feature.
 *
 * <p>Recommendations are read-only. The two writing methods re-run every hard rule against freshly
 * loaded database rows before they touch anything, and the writes themselves are delegated to the
 * existing {@link RouteService} methods, so the classic rules (editable route, capacity, "truck is
 * already assigned", readiness refresh) can never be bypassed - not by a stale browser tab, and not
 * by the AI.</p>
 */
@Service
public class AssignmentService {

    private static final Logger log = LoggerFactory.getLogger(AssignmentService.class);
    private static final int MAX_REJECTIONS = 12;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM HH:mm");
    private static final String AVAILABLE = "AVAILABLE";

    private final OrderService orderService;
    private final RouteService routeService;
    private final DriverService driverService;
    private final VehicleService vehicleService;
    private final WarehouseService warehouseService;
    private final RouteMatchingService matching;
    private final RouteScoringService scoring;
    private final AiAdvisor ai;

    /** Remembered travel times, so a proposed route already knows how long its lane takes. */
    private final LaneDurationService laneDurations;

    public AssignmentService(OrderService orderService,
                             RouteService routeService,
                             DriverService driverService,
                             VehicleService vehicleService,
                             WarehouseService warehouseService,
                             RouteMatchingService matching,
                             RouteScoringService scoring,
                             AiAdvisor ai) {
        this(orderService, routeService, driverService, vehicleService, warehouseService, matching,
                scoring, ai, null);
    }

    @Autowired
    public AssignmentService(OrderService orderService,
                             RouteService routeService,
                             DriverService driverService,
                             VehicleService vehicleService,
                             WarehouseService warehouseService,
                             RouteMatchingService matching,
                             RouteScoringService scoring,
                             AiAdvisor ai,
                             LaneDurationService laneDurations) {
        this.laneDurations = laneDurations;
        this.orderService = orderService;
        this.routeService = routeService;
        this.driverService = driverService;
        this.vehicleService = vehicleService;
        this.warehouseService = warehouseService;
        this.matching = matching;
        this.scoring = scoring;
        this.ai = ai;
    }

    // ------------------------------------------------------------ recommendation

    /** Analyses one order against the whole network and returns up to three validated routes. */
    public AssignmentAdvice advise(String orderId, String warehouseId, boolean useAi) {
        Order order = orderService.findByOrderId(orderId);
        Warehouse depot = resolveDepot(order, warehouseId);

        AssignmentAdvice advice = describe(order, depot);
        if (!LinehaulUtil.clean(order.getRouteId()).isEmpty()) {
            advice.setAlreadyAssigned(true);
            advice.setCurrentRouteId(order.getRouteId());
            advice.setMessage("Order " + order.getOrderId() + " is already on route " + order.getRouteId() + ".");
            advice.addStep(new PipelineStep("Order already assigned", PipelineStep.INFO, advice.getMessage()));
            return advice;
        }

        Map<String, Vehicle> vehicles = vehicleIndex();
        MatchResult result = matching.evaluate(order, depot, routeService.findAll(null), vehicles);
        List<RouteRecommendation> ranked = scoring.rank(result.eligible());

        AiAdvisor.StatusHolder status = new AiAdvisor.StatusHolder();
        if (useAi && ai.isEnabled()) {
            ai.rank(order, ranked, status).ifPresent(ranking -> applyRanking(ranked, ranking));
        } else {
            status.set(ai.isEnabled() ? AiAdvisor.Status.SKIPPED : AiAdvisor.Status.DISABLED);
        }

        List<RouteRecommendation> top = ranked.stream()
                .limit(RouteScoringService.TOP_N)
                .toList();
        nameWarehouses(top);
        advice.setCandidates(new ArrayList<>(top));
        advice.setRejected(limitRejections(result.rejected()));
        advice.setEligibleCount(ranked.size());
        advice.setRoutesConsidered(result.routesConsidered());
        advice.setSteps(result.steps());
        advice.addStep(new PipelineStep("Rank routes", top.isEmpty() ? PipelineStep.FAILED : PipelineStep.PASSED,
                ranked.size() + " eligible route(s); showing the best " + top.size()
                        + (top.isEmpty() ? "" : " (top score " + top.get(0).getScore() + "/100)")));

        if (top.isEmpty()) {
            NewRouteAdvice newRoute = newRouteAdvice(order, depot, result.rejected());
            advice.setNewRouteRequired(true);
            advice.setNewRoute(newRoute);
            advice.addStep(new PipelineStep("No existing route fits", PipelineStep.FAILED, newRoute.getMessage()));
            advice.setHeadline("No existing route can carry this order");
            advice.setMessage(newRoute.getMessage());
        } else {
            RouteRecommendation best = top.get(0);
            advice.setHeadline(best.getRouteId() + " is the best fit for " + order.getOrderId());
            advice.setMessage(aiStatusMessage(status, best));
            if (best.getAiReason() != null) {
                advice.setAiReasoning(best.getAiReason());
            }
        }
        advice.setAiStatus(status.label());
        advice.setAiUsed(status.get() == AiAdvisor.Status.APPLIED);
        return advice;
    }

    private String aiStatusMessage(AiAdvisor.StatusHolder status, RouteRecommendation best) {
        return switch (status.get()) {
            case APPLIED -> "Candidates ranked by " + ai.modelName() + " on top of the routes the engine validated.";
            case UNAVAILABLE -> "AI recommendation is temporarily unavailable - the order of the list comes from "
                    + "the deterministic rules, and " + label(best.getRouteId()) + " still leads on score.";
            default -> "Ranking comes from the deterministic rules: destination, loading stop, capacity, "
                    + "vehicle, route status and ETA.";
        };
    }

    /** Applies the model's ordering, but only to the route ids the engine already approved. */
    private void applyRanking(List<RouteRecommendation> ranked, AiAdvisor.Ranking ranking) {
        Map<String, RouteRecommendation> byId = new LinkedHashMap<>();
        for (RouteRecommendation item : ranked) {
            byId.put(item.getRouteId(), item);
        }

        List<RouteRecommendation> reordered = new ArrayList<>();
        for (String routeId : ranking.orderedRouteIds()) {
            RouteRecommendation item = byId.remove(routeId);
            if (item == null) {
                continue;
            }
            if (ranking.reasons().containsKey(routeId)) {
                item.setAiReason(ranking.reasons().get(routeId));
            }
            String risk = ranking.risks().get(routeId);
            if (risk != null && !risk.isBlank() && !item.getWarnings().contains(risk)) {
                List<String> warnings = new ArrayList<>(item.getWarnings());
                warnings.add("AI note: " + risk);
                item.setWarnings(warnings);
            }
            reordered.add(item);
        }
        reordered.addAll(byId.values());

        ranked.clear();
        ranked.addAll(reordered);
        for (int i = 0; i < ranked.size(); i++) {
            ranked.get(i).setRank(i + 1);
        }
    }

    private AssignmentAdvice describe(Order order, Warehouse depot) {
        AssignmentAdvice advice = new AssignmentAdvice();
        advice.setOrderId(order.getOrderId());
        advice.setCustomer(order.getCustomer());
        advice.setOrigin(order.getOrigin());
        advice.setDestination(order.getDestination());
        advice.setWeight(order.getWeight());
        advice.setPieces(order.getPieces());
        advice.setServiceDate(order.getServiceDate());
        advice.setStatus(order.getStatus());
        advice.setGeneratedAt(LocalDateTime.now().format(STAMP));
        if (depot != null) {
            advice.setWarehouseId(depot.getWarehouseId());
            // the place, not the "Warehouse A" designation: it is the name the lanes are written with
            advice.setWarehouseName(placeOf(depot));
            advice.setWarehouseCity(depot.getCity());
        }
        return advice;
    }

    /** Builds the "create a new route" recommendation, including what is available to run it. */
    NewRouteAdvice newRouteAdvice(Order order, Warehouse depot, List<RouteRejection> rejected) {
        NewRouteAdvice advice = new NewRouteAdvice();
        advice.setRequired(true);
        advice.setOrigin(depot == null ? order.getOrigin() : depot.getHubLocation());
        advice.setDestination(order.getDestination());
        advice.setRequiredCapacity(order.getWeight());
        advice.setSuggestedRouteId(routeService.nextRouteId());

        String depotKey = depot == null ? "" : depot.getWarehouseId();
        List<Driver> freeDrivers = depotKey.isEmpty() ? List.of() : driverService.findAvailable(depotKey);
        List<Vehicle> freeTrucks = depotKey.isEmpty() ? List.of() : vehicleService.findAvailable(order.getWeight(), depotKey);

        List<ResourceOption> drivers = new ArrayList<>();
        for (Driver driver : freeDrivers) {
            ResourceOption option = new ResourceOption();
            option.setId(driver.getDriverId());
            option.setName(driver.getName());
            option.setStatus(driver.getStatus());
            option.setLabel(driver.getDriverId() + " - " + driver.getName());
            option.setNote("Free at " + label(driver.getWarehouseId()));
            drivers.add(option);
        }
        List<ResourceOption> vehicles = new ArrayList<>();
        int biggest = 0;
        for (Vehicle truck : freeTrucks) {
            ResourceOption option = new ResourceOption();
            option.setId(truck.getTruckId());
            option.setName(truck.getType());
            option.setType(truck.getType());
            option.setCapacity(truck.getCapacity());
            option.setStatus(truck.getStatus());
            option.setLabel(truck.getTruckId() + " - " + truck.getType() + " - "
                    + CapacityRules.number(truck.getCapacity()) + " kg");
            option.setNote("Carries up to " + CapacityRules.number(truck.getCapacity()) + " kg");
            vehicles.add(option);
            biggest = Math.max(biggest, truck.getCapacity());
        }
        advice.setDrivers(drivers);
        advice.setVehicles(vehicles);
        advice.setSuggestedMaxCapacity(biggest > 0 ? biggest : Math.max(order.getWeight(), 1000));
        advice.setMissingChecks(missingChecks(rejected));
        advice.setCanCreate(!depotKey.isEmpty() && !drivers.isEmpty() && !vehicles.isEmpty());

        // The network already knows this lane: propose its remembered duration instead of the default.
        Integer knownHours = laneDurations == null ? null
                : laneDurations.durationFor(advice.getOrigin(), advice.getDestination());
        if (knownHours != null && knownHours > 0) {
            advice.setSuggestedTravelDuration(knownHours);
            advice.setDurationKnown(true);
        }

        if (depotKey.isEmpty()) {
            advice.setMessage("Select a warehouse first: a new route has to start somewhere, and its driver and "
                    + "truck must come from that depot.");
        } else if (drivers.isEmpty() || vehicles.isEmpty()) {
            advice.setMessage("No suitable driver/vehicle is currently available in this warehouse."
                    + (drivers.isEmpty() ? " Free drivers at " + label(depotKey) + ": 0." : "")
                    + (vehicles.isEmpty() ? " No free truck can carry "
                    + CapacityRules.number(order.getWeight()) + " kg." : ""));
        } else {
            advice.setMessage("No existing route can carry this order, so a new route from "
                    + label(advice.getOrigin()) + " to " + label(advice.getDestination()) + " is recommended. "
                    + drivers.size() + " driver(s) and " + vehicles.size()
                    + " truck(s) at this warehouse are free.");
        }
        return advice;
    }

    private List<String> missingChecks(List<RouteRejection> rejected) {
        Map<String, Integer> byStage = new LinkedHashMap<>();
        for (RouteRejection rejection : rejected) {
            byStage.merge(rejection.getStage(), 1, Integer::sum);
        }
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : byStage.entrySet()) {
            lines.add(entry.getKey() + ": " + entry.getValue() + " route(s) failed this check");
        }
        if (lines.isEmpty()) {
            lines.add("There are no routes in the network at all.");
        }
        return lines;
    }

    // ------------------------------------------------------------ writes

    /**
     * Assigns one order to one route. Every hard rule is checked again here on freshly loaded rows and
     * the writes go through {@link RouteService}, so the AI or a stale screen cannot create bad data.
     */
    public AssignmentResult assign(String orderId, AssignRequest request) {
        Order order = orderService.findByOrderId(orderId);
        if (!LinehaulUtil.clean(order.getRouteId()).isEmpty()) {
            throw new BusinessException("Order " + orderId + " is already assigned to route "
                    + order.getRouteId() + ".");
        }
        String routeId = LinehaulUtil.clean(request.getRouteId());
        if (routeId.isEmpty()) {
            throw new BusinessException("Choose a route first.");
        }
        Route route = routeService.findByRouteId(routeId);
        Warehouse depot = resolveDepot(order, request.getWarehouseId());

        String problem = matching.validate(order, depot, route, vehicleIndex());
        if (problem != null) {
            throw new BusinessException("Assignment refused - " + problem);
        }

        boolean foreignDepot = depot != null && !LinehaulUtil.clean(route.getWarehouseId()).toUpperCase()
                .equals(depot.getWarehouseId().toUpperCase());
        String driverId = LinehaulUtil.clean(request.getDriverId());
        String truckId = LinehaulUtil.clean(request.getTruckId());
        if (foreignDepot && (!driverId.isEmpty() || !truckId.isEmpty())) {
            throw new BusinessException("Route " + routeId + " belongs to " + label(route.getWarehouseId())
                    + ", which already assigned its driver and truck. You can add your order to that route, "
                    + "but its crew is managed at " + label(route.getWarehouseId()) + ".");
        }

        if (!driverId.isEmpty() && !driverId.equals(LinehaulUtil.clean(route.getDriverId()))) {
            requireFreeDriver(driverId, depot);
            route = routeService.assignDriver(routeId, driverId);
        }
        if (!truckId.isEmpty() && !truckId.equals(LinehaulUtil.clean(route.getTruckId()))) {
            requireFreeTruck(truckId, depot, route, order.getWeight());
            route = routeService.assignTruck(routeId, truckId);
        }

        Route updated = routeService.assignOrder(routeId, orderId);
        Order saved = orderService.findByOrderId(orderId);

        AssignmentResult result = new AssignmentResult();
        result.setOrderId(order.getOrderId());
        result.setRouteId(routeId);
        result.setOrder(saved);
        result.setRoute(updated);
        result.setDriverId(route.getDriverId());
        result.setTruckId(route.getTruckId());
        result.setMessage("Order " + order.getOrderId() + " assigned to route " + routeId + " ("
                + lane(updated) + "), load now " + CapacityRules.number(updated.getCurrentWeight()) + " of "
                + CapacityRules.number(updated.getMaxCapacity()) + " kg"
                + (foreignDepot ? ". It is loaded at " + label(pickupName(order, depot, updated))
                        + " when the route reaches that stop." : "."));
        log.info("Order {} assigned to route {} by the smart assignment panel", order.getOrderId(), routeId);
        return result;
    }

    /** Creates the recommended route, puts a truck and driver on it, then loads the order. */
    public AssignmentResult createAndAssign(CreateAndAssignRequest request) {
        Order order = orderService.findByOrderId(request.getOrderId());
        if (!LinehaulUtil.clean(order.getRouteId()).isEmpty()) {
            throw new BusinessException("Order " + order.getOrderId() + " is already assigned to route "
                    + order.getRouteId() + ".");
        }
        Warehouse depot = resolveDepot(order, request.getWarehouseId());
        if (depot == null) {
            throw new BusinessException("Select a warehouse before creating a new route.");
        }

        Driver driver = driverService.findByDriverId(request.getDriverId());
        Vehicle truck = vehicleService.findByTruckId(request.getTruckId());
        requireFreeDriver(driver.getDriverId(), depot);
        requireFreeTruck(truck.getTruckId(), depot, null, order.getWeight());

        int capacity = request.getMaxCapacity() == null || request.getMaxCapacity() <= 0
                ? Math.max(truck.getCapacity(), order.getWeight())
                : request.getMaxCapacity();
        if (capacity < order.getWeight()) {
            throw new BusinessException("The route capacity (" + CapacityRules.number(capacity)
                    + " kg) is smaller than this order (" + CapacityRules.number(order.getWeight()) + " kg).");
        }

        Route draft = new Route();
        draft.setRouteId("");
        draft.setWarehouseId(depot.getWarehouseId());
        draft.setOrigin(preferred(depot.getHubLocation(), order.getOrigin()));
        draft.setDestination(order.getDestination());
        draft.setStops(new ArrayList<>());
        draft.setMaxCapacity(capacity);
        draft.setDepartureTime(LinehaulUtil.clean(request.getDepartureTime()).isEmpty()
                ? "20:00" : request.getDepartureTime().trim());
        draft.setTravelDuration(request.getTravelDuration() == null || request.getTravelDuration() <= 0
                ? rememberedDuration(draft.getOrigin(), draft.getDestination())
                : request.getTravelDuration());

        Route created = routeService.create(draft);
        created = routeService.assignTruck(created.getRouteId(), truck.getTruckId());
        created = routeService.assignDriver(created.getRouteId(), driver.getDriverId());
        Route finished = routeService.assignOrder(created.getRouteId(), order.getOrderId());

        AssignmentResult result = new AssignmentResult();
        result.setOrderId(order.getOrderId());
        result.setRouteId(finished.getRouteId());
        result.setNewRouteCreated(true);
        result.setDriverId(driver.getDriverId());
        result.setTruckId(truck.getTruckId());
        result.setOrder(orderService.findByOrderId(order.getOrderId()));
        result.setRoute(finished);
        result.setMessage("New route " + finished.getRouteId() + " created and " + order.getOrderId()
                + " assigned successfully.");
        log.info("Route {} created for order {} with driver {} and truck {}",
                finished.getRouteId(), order.getOrderId(), driver.getDriverId(), truck.getTruckId());
        return result;
    }

    /** The remembered hours of a lane, or the project default when this lane is new. */
    private int rememberedDuration(String origin, String destination) {
        Integer known = laneDurations == null ? null : laneDurations.durationFor(origin, destination);
        return known != null && known > 0 ? known : 9;
    }

    private void requireFreeDriver(String driverId, Warehouse depot) {
        Driver driver = driverService.findByDriverId(driverId);
        if (!AVAILABLE.equalsIgnoreCase(LinehaulUtil.clean(driver.getStatus()))) {
            throw new BusinessException("Driver " + driverId + " is " + label(driver.getStatus())
                    + ", not available.");
        }
        if (!LinehaulUtil.clean(driver.getRouteId()).isEmpty()) {
            throw new BusinessException("Driver " + driverId + " is already on route " + driver.getRouteId() + ".");
        }
        if (depot != null && !LinehaulUtil.clean(driver.getWarehouseId()).isEmpty()
                && !driver.getWarehouseId().equalsIgnoreCase(depot.getWarehouseId())) {
            throw new BusinessException("Driver " + driverId + " belongs to " + label(driver.getWarehouseId())
                    + ", not to " + label(depot.getWarehouseId()) + ".");
        }
    }

    private void requireFreeTruck(String truckId, Warehouse depot, Route route, int orderWeight) {
        Vehicle truck = vehicleService.findByTruckId(truckId);
        if (!AVAILABLE.equalsIgnoreCase(LinehaulUtil.clean(truck.getStatus()))) {
            throw new BusinessException("Truck " + truckId + " is " + label(truck.getStatus()) + ", not available.");
        }
        if (!LinehaulUtil.clean(truck.getRouteId()).isEmpty()) {
            throw new BusinessException("Truck " + truckId + " is already on route " + truck.getRouteId() + ".");
        }
        if (truck.getCapacity() <= 0) {
            throw new BusinessException("Truck " + truckId + " has no capacity on record, so it cannot be used.");
        }
        int expectedLoad = (route == null ? 0 : route.getCurrentWeight()) + orderWeight;
        if (expectedLoad > truck.getCapacity()) {
            throw new BusinessException("Truck " + truckId + " carries " + CapacityRules.number(truck.getCapacity())
                    + " kg, but the route would need " + CapacityRules.number(expectedLoad) + " kg.");
        }
        if (depot != null && !LinehaulUtil.clean(truck.getWarehouseId()).isEmpty()
                && !truck.getWarehouseId().equalsIgnoreCase(depot.getWarehouseId())) {
            throw new BusinessException("Truck " + truckId + " belongs to " + label(truck.getWarehouseId())
                    + ", not to " + label(depot.getWarehouseId()) + ".");
        }
    }

    private String pickupName(Order order, Warehouse depot, Route route) {
        List<String> sequence = route.getStopSequence();
        if (sequence == null || sequence.isEmpty()) {
            return depot == null ? order.getOrigin() : depot.getHubLocation();
        }
        int index = depot == null
                ? LocationMatcher.indexOf(sequence, order.getOrigin())
                : LocationMatcher.indexOfWarehouse(sequence, depot);
        if (index < 0) {
            return depot == null ? order.getOrigin() : depot.getHubLocation();
        }
        return sequence.get(index);
    }

    // ------------------------------------------------------------ assign all

    /**
     * Runs the same pipeline over every unassigned order of the depot, one order at a time, so each
     * following order sees the capacity the previous one used. Nothing is forced: an order without a
     * qualifying route is reported instead of squeezed in.
     */
    public BatchAssignmentReport assignAll(String warehouseId, boolean useAi) {
        String depotKey = WarehouseScope.key(warehouseId);
        Warehouse depot = depotKey.isEmpty() ? null : warehouseService.findByWarehouseId(depotKey);
        List<Order> orders = orderService.findUnassigned(depotKey).stream()
                .sorted(Comparator.comparing(Order::getOrderId))
                .toList();

        BatchAssignmentReport report = new BatchAssignmentReport();
        report.setWarehouseId(depotKey);
        report.setWarehouseName(depot == null ? "All warehouses" : depot.getName());
        report.setTotalOrders(orders.size());

        for (Order order : orders) {
            BatchAssignmentReport.Outcome outcome = new BatchAssignmentReport.Outcome();
            outcome.setOrderId(order.getOrderId());
            outcome.setOrigin(order.getOrigin());
            outcome.setDestination(order.getDestination());
            outcome.setWeight(order.getWeight());
            try {
                assignOne(order, depot, outcome);
            } catch (BusinessException rejected) {
                outcome.setAction("NEEDS_REVIEW");
                outcome.setRouteId(null);
                outcome.setReason(rejected.getMessage());
            } catch (Exception failure) {
                log.warn("Auto assignment of {} could not finish", order.getOrderId(), failure);
                outcome.setAction("NEEDS_REVIEW");
                outcome.setReason("The order could not be processed (" + failure.getClass().getSimpleName()
                        + "). Nothing was changed for it.");
            }
            switch (outcome.getAction() == null ? "NEEDS_REVIEW" : outcome.getAction()) {
                case "ASSIGNED" -> report.setAssigned(report.getAssigned() + 1);
                case "NEW_ROUTE_REQUIRED" -> report.setNewRouteRequired(report.getNewRouteRequired() + 1);
                default -> report.setNeedsReview(report.getNeedsReview() + 1);
            }
            report.getOutcomes().add(outcome);
        }

        report.setMessage(report.getAssigned() == 0 && report.getNewRouteRequired() == 0
                ? "No order could be assigned automatically. Each reason is listed below."
                : report.getAssigned() + " of " + report.getTotalOrders()
                        + " order(s) were put on a route; " + report.getNewRouteRequired()
                        + " need a new route and " + report.getNeedsReview() + " need a dispatcher.");

        if (useAi && ai.isEnabled()) {
            ai.batchSummary(report).ifPresent(text -> {
                report.setAiSummary(text);
                report.setAiUsed(true);
            });
        }
        return report;
    }

    /**
     * The "Assign All Orders" progress list calls this once per order, so the dispatcher sees each
     * result as it happens. It is the very same code path the batch run uses - one implementation, no
     * second set of rules to keep in sync.
     */
    public BatchAssignmentReport.Outcome autoAssignOne(String orderId, String warehouseId) {
        Order order = orderService.findByOrderId(orderId);
        Warehouse depot = resolveDepot(order, warehouseId);

        BatchAssignmentReport.Outcome outcome = new BatchAssignmentReport.Outcome();
        outcome.setOrderId(order.getOrderId());
        outcome.setOrigin(order.getOrigin());
        outcome.setDestination(order.getDestination());
        outcome.setWeight(order.getWeight());
        if (!LinehaulUtil.clean(order.getRouteId()).isEmpty()) {
            outcome.setAction("SKIPPED");
            outcome.setReason("Already on route " + order.getRouteId() + ".");
            return outcome;
        }
        try {
            assignOne(order, depot, outcome);
        } catch (BusinessException rejected) {
            outcome.setAction("NEEDS_REVIEW");
            outcome.setRouteId(null);
            outcome.setReason(rejected.getMessage());
        }
        return outcome;
    }

    /**
     * One order of a batch run. Fills the outcome in place; writes only happen through
     * {@link #assign(String, AssignRequest)}, so the batch uses exactly the same validations.
     */
    private void assignOne(Order order, Warehouse depot, BatchAssignmentReport.Outcome outcome) {
        MatchResult result = matching.evaluate(order, depot, routeService.findAll(null), vehicleIndex());
        List<RouteRecommendation> ranked = scoring.rank(result.eligible());

        if (ranked.isEmpty()) {
            NewRouteAdvice advice = newRouteAdvice(order, depot, result.rejected());
            if (advice.isCanCreate()) {
                outcome.setAction("NEW_ROUTE_REQUIRED");
                outcome.setRouteId(advice.getSuggestedRouteId());
                outcome.setRouteLane(advice.getOrigin() + " > " + advice.getDestination());
                outcome.setAvailableCapacity(advice.getSuggestedMaxCapacity() - order.getWeight());
                outcome.setReason("No existing route can carry this order, so " + advice.getSuggestedRouteId()
                        + " from " + label(advice.getOrigin()) + " to " + label(advice.getDestination())
                        + " can be created with " + advice.getDrivers().size() + " free driver(s) and "
                        + advice.getVehicles().size() + " free truck(s). Open the order to confirm it.");
            } else {
                outcome.setAction("NEEDS_REVIEW");
                outcome.setReason(rejectedSummary(result, order, advice));
            }
            return;
        }

        RouteRecommendation best = ranked.get(0);
        outcome.setRouteId(best.getRouteId());
        outcome.setRouteLane(lane(best));
        outcome.setScore(best.getScore());
        outcome.setEta(best.isEtaKnown() ? best.getOrderEta() : null);
        outcome.setAvailableCapacity(best.getAvailableCapacity());
        outcome.setDriverId(best.getDriverId());
        outcome.setTruckId(best.getTruckId());

        if (best.getScore() < RouteScoringService.AUTO_ASSIGN_THRESHOLD) {
            outcome.setAction("NEEDS_REVIEW");
            outcome.setReason("The best route " + best.getRouteId() + " scored only " + best.getScore()
                    + "/100, below the " + RouteScoringService.AUTO_ASSIGN_THRESHOLD
                    + " confidence threshold for automatic assignment. " + firstReason(best));
            return;
        }

        AssignRequest request = new AssignRequest();
        request.setRouteId(best.getRouteId());
        request.setWarehouseId(depot == null ? null : depot.getWarehouseId());
        assign(order.getOrderId(), request);

        outcome.setAction("ASSIGNED");
        outcome.setReason("Matched on " + label(best.getMatchType()) + " with "
                + CapacityRules.number(best.getAvailableCapacity()) + " kg free"
                + (best.isEtaKnown() ? " and arrival " + best.getOrderEta() : ", ETA unknown") + "."
                + (best.isNeedsDriver() || best.isNeedsTruck()
                ? " Route " + best.getRouteId() + " still needs a "
                + (best.isNeedsDriver() ? "driver" : "")
                + (best.isNeedsDriver() && best.isNeedsTruck() ? " and a " : "")
                + (best.isNeedsTruck() ? "truck" : "") + " before it can be dispatched."
                : ""));
    }

    private String rejectedSummary(MatchResult result, Order order, NewRouteAdvice advice) {
        if (!result.rejected().isEmpty()) {
            RouteRejection first = result.rejected().get(0);
            return "No route fits and a new one cannot be created. " + first.getRouteId() + ": "
                    + first.getReason() + " " + advice.getMessage();
        }
        return "No route fits and a new one cannot be created: the network has " + result.routesConsidered()
                + " route(s) and none runs to " + label(order.getDestination()) + ". " + advice.getMessage();
    }

    private String firstReason(RouteRecommendation best) {
        if (!best.getReasons().isEmpty()) {
            return best.getReasons().get(0);
        }
        return best.getMatchLabel() == null ? "no further explanation recorded" : best.getMatchLabel();
    }

    private String lane(Route route) {
        List<String> sequence = route.getStopSequence();
        if (sequence == null || sequence.isEmpty()) {
            return route.getOrigin() + " > " + route.getDestination();
        }
        return String.join(" > ", sequence);
    }

    private String lane(RouteRecommendation best) {
        if (best.getStopSequence() != null && !best.getStopSequence().isEmpty()) {
            return String.join(" > ", best.getStopSequence());
        }
        return best.getOrigin() + " > " + best.getDestination();
    }

    private List<RouteRejection> limitRejections(List<RouteRejection> rejected) {
        if (rejected.size() <= MAX_REJECTIONS) {
            return new ArrayList<>(rejected);
        }
        return new ArrayList<>(rejected.subList(0, MAX_REJECTIONS));
    }

    private void nameWarehouses(List<RouteRecommendation> candidates) {
        if (candidates.isEmpty()) {
            return;
        }
        Map<String, String> names = new LinkedHashMap<>();
        for (Warehouse warehouse : warehouseService.findAll()) {
            // a cross-warehouse route is described by the PLACE it comes from, e.g. "Chennai route"
            names.put(warehouse.getWarehouseId().toUpperCase(Locale.ROOT), placeOf(warehouse));
        }
        for (RouteRecommendation candidate : candidates) {
            String key = LinehaulUtil.clean(candidate.getWarehouseId()).toUpperCase(Locale.ROOT);
            candidate.setWarehouseName(names.getOrDefault(key, key));
        }
    }

    private Map<String, Vehicle> vehicleIndex() {
        Map<String, Vehicle> index = new LinkedHashMap<>();
        for (Vehicle vehicle : vehicleService.findAll()) {
            index.put(LinehaulUtil.clean(vehicle.getTruckId()).toUpperCase(Locale.ROOT), vehicle);
        }
        return index;
    }

    private Warehouse resolveDepot(Order order, String requested) {
        String key = WarehouseScope.key(requested);
        if (key.isEmpty()) {
            key = WarehouseScope.key(order.getWarehouseId());
        }
        if (key.isEmpty()) {
            return null;
        }
        final String warehouseKey = key;
        return warehouseService.find(warehouseKey)
                .orElseThrow(() -> new BusinessException("Warehouse " + warehouseKey + " does not exist."));
    }

    /** The place a depot is: its city, then its hub location, then its designation. */
    static String placeOf(Warehouse warehouse) {
        if (warehouse == null) {
            return "";
        }
        String city = LinehaulUtil.clean(warehouse.getCity());
        if (!city.isEmpty()) {
            return city;
        }
        String hub = LinehaulUtil.clean(warehouse.getHubLocation());
        return hub.isEmpty() ? LinehaulUtil.clean(warehouse.getName()) : hub;
    }

    private static String preferred(String first, String second) {
        String clean = LinehaulUtil.clean(first);
        return clean.isEmpty() ? LinehaulUtil.clean(second) : clean;
    }

    private static String label(String value) {
        return RouteMatchingService.label(value);
    }
}
