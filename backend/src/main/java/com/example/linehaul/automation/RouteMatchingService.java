package com.example.linehaul.automation;

import com.example.linehaul.dto.PipelineStep;
import com.example.linehaul.dto.RouteRejection;
import com.example.linehaul.dto.RouteRecommendation;
import com.example.linehaul.model.Order;
import com.example.linehaul.model.Route;
import com.example.linehaul.model.Vehicle;
import com.example.linehaul.model.Warehouse;
import com.example.linehaul.service.LinehaulUtil;
import com.example.linehaul.service.RouteService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The deterministic heart of the smart assignment feature.
 *
 * <p>For one order it walks the whole route network (every warehouse, not only the selected one) and
 * applies the filters in this order:</p>
 *
 * <ol>
 *   <li><b>route status</b> - a dispatched, in-transit or completed route is closed for changes;</li>
 *   <li><b>destination</b> - the route must actually get the freight where the order wants to go,
 *       either as its final destination (case A/B) or as a stop on the way (case C);</li>
 *   <li><b>current warehouse as a loading point</b> - the route must pass the depot that holds the
 *       order <em>before</em> the unloading stop, otherwise the freight can never be loaded (case D);</li>
 *   <li><b>route capacity</b> and <b>vehicle capacity</b> - the order may not overload anything;</li>
 *   <li><b>ETA</b> - estimated with the project's existing departure-time-plus-duration model.</li>
 * </ol>
 *
 * <p>Nothing here writes to the database and nothing here is invented: when a route has no departure
 * time or no truck, that is reported as unknown instead of being guessed.</p>
 */
@Service
public class RouteMatchingService {

    public static final String MATCH_EXACT = "EXACT";
    public static final String MATCH_DESTINATION = "DESTINATION";
    public static final String MATCH_STOP = "STOP";

    private final RouteService routeService;

    public RouteMatchingService(RouteService routeService) {
        this.routeService = routeService;
    }

    /**
     * @param depot    the warehouse the operator selected (may be null for the whole network)
     * @param routes   every decorated route of every warehouse
     * @param vehicles trucks keyed by truck id, used for the vehicle capacity check
     */
    public MatchResult evaluate(Order order, Warehouse depot, List<Route> routes, Map<String, Vehicle> vehicles) {
        MatchResult result = new MatchResult();
        result.setRoutesConsidered(routes.size());

        for (Route route : routes) {
            if (route == null
                    || (order.getRouteId() != null && route.getRouteId().equals(order.getRouteId()))) {
                continue;
            }
            String rejection = describeRejection(order, depot, route, vehicles, result);
            if (rejection == null) {
                result.addEligible(buildOutcome(order, depot, route, vehicles));
            }
        }

        int reaches = result.count("reachesDestination");
        int loads = result.count("validLoadingStop");
        int fits = result.eligible().size();
        int closed = result.count("closed");
        int viaStop = result.count("intermediateStop");
        String destination = label(order.getDestination());

        result.addStep(new PipelineStep("Read order", PipelineStep.INFO,
                order.getOrderId() + ": " + label(order.getOrigin()) + " to " + destination
                        + ", " + CapacityRules.number(order.getWeight()) + " kg over " + order.getPieces() + " pieces"));
        result.addStep(new PipelineStep("Inspect all routes", PipelineStep.INFO,
                routes.size() + " routes across every warehouse were inspected"
                        + (closed == 0 ? "" : ", " + closed + " of them closed for changes")));
        result.addStep(new PipelineStep("Destination compatibility",
                reaches > 0 ? PipelineStep.PASSED : PipelineStep.FAILED,
                reaches + " route(s) bring freight to " + destination));
        result.addStep(new PipelineStep("Current warehouse is a valid stop",
                loads > 0 ? PipelineStep.PASSED : PipelineStep.FAILED,
                depot == null
                        ? "No warehouse selected, so the order's own origin was used as the loading point"
                        : loads + " route(s) pass " + label(depot.getHubLocation())
                                + " before " + destination));
        result.addStep(new PipelineStep("Stop sequence", viaStop > 0 ? PipelineStep.INFO : PipelineStep.PASSED,
                viaStop + " of those reach " + destination + " as an intermediate stop instead of the end"));
        result.addStep(new PipelineStep("Capacity available",
                fits > 0 ? PipelineStep.PASSED : PipelineStep.FAILED,
                fits + " of " + routes.size() + " inspected route(s) can still carry "
                        + CapacityRules.number(order.getWeight()) + " kg"));
        result.addStep(new PipelineStep("Vehicle and route status", fits > 0 ? PipelineStep.PASSED : PipelineStep.INFO,
                "The truck on the route must be able to carry the new total load"));
        result.addStep(new PipelineStep("ETA compared", fits > 0 ? PipelineStep.PASSED : PipelineStep.INFO,
                "Arrival time estimated with the existing departure time + travel duration model"));
        return result;
    }

    /**
     * @return null when this route may carry the order, otherwise the human readable reason it may not.
     *         Rejections are pushed into {@code result} so the UI can show why a route was dropped.
     */
    private String describeRejection(Order order, Warehouse depot, Route route,
                                     Map<String, Vehicle> vehicles, MatchResult result) {
        List<String> sequence = sequenceOf(route);

        if (!routeService.isEditable(route)) {
            result.note("closed");
            reject(result, route, "ROUTE_STATUS", "Route is " + label(route.getStatus())
                    + " and is closed for changes.");
            return "closed";
        }

        int unload = unloadIndex(sequence, order.getDestination());
        if (unload < 0) {
            reject(result, route, "DESTINATION", "This route never reaches " + label(order.getDestination())
                    + " (it runs " + label(route.getOrigin()) + " to " + label(route.getDestination()) + ").");
            return "destination";
        }
        result.note("reachesDestination");
        if (unload != sequence.size() - 1) {
            result.note("intermediateStop");
        }

        int load = loadIndex(sequence, order, depot, route);
        if (load < 0) {
            String where = depot == null ? label(order.getOrigin()) : label(depot.getHubLocation());
            reject(result, route, "WAREHOUSE_STOP", "This route does not pass through " + where
                    + ", so the order cannot be loaded onto it.");
            return "pickup";
        }
        if (load >= unload) {
            reject(result, route, "STOP_ORDER", "This route passes the loading point after "
                    + label(order.getDestination()) + ", so the freight would be carried past its own destination.");
            return "order";
        }
        result.note("validLoadingStop");

        String capacityProblem = CapacityRules.rejectRouteCapacity(route, order.getWeight());
        if (capacityProblem != null) {
            reject(result, route, "ROUTE_CAPACITY", capacityProblem);
            return "routeCapacity";
        }

        Vehicle truck = truckFor(route, vehicles);
        if (truck != null) {
            String vehicleProblem = CapacityRules.rejectVehicleCapacity(route, order.getWeight(), truck.getCapacity());
            if (vehicleProblem != null) {
                reject(result, route, "VEHICLE_CAPACITY", vehicleProblem);
                return "vehicleCapacity";
            }
        }
        return null;
    }

    /**
     * Same checks as {@link #describeRejection} but without the reporting, used right before a write so
     * that a stale UI tab can never create an invalid assignment.
     *
     * @return null when the assignment is valid, otherwise the reason it is not
     */
    public String validate(Order order, Warehouse depot, Route route, Map<String, Vehicle> vehicles) {
        return describeRejection(order, depot, route, vehicles, new MatchResult());
    }

    private MatchOutcome buildOutcome(Order order, Warehouse depot, Route route, Map<String, Vehicle> vehicles) {
        List<String> sequence = sequenceOf(route);
        int load = loadIndex(sequence, order, depot, route);
        int unload = unloadIndex(sequence, order.getDestination());

        RouteRecommendation item = new RouteRecommendation();
        item.setRouteId(route.getRouteId());
        item.setWarehouseId(LinehaulUtil.clean(route.getWarehouseId()));
        item.setOrigin(route.getOrigin());
        item.setDestination(route.getDestination());
        item.setStops(route.getStops() == null ? new ArrayList<>() : new ArrayList<>(route.getStops()));
        item.setStopSequence(new ArrayList<>(sequence));
        item.setPickupIndex(load);
        item.setDropoffIndex(unload);
        item.setPickupLabel(sequence.get(load));
        item.setMatchType(unload != sequence.size() - 1 ? MATCH_STOP
                : sameLane(order, depot, route, load) ? MATCH_EXACT : MATCH_DESTINATION);
        item.setMatchLabel(matchLabel(item.getMatchType(), order, route));
        item.setOrderWeight(order.getWeight());
        item.setCurrentWeight(route.getCurrentWeight());
        item.setMaxCapacity(route.getMaxCapacity());
        item.setCapacityPercent(route.getCapacityPercent());
        item.setStatus(route.getStatus());
        item.setReadiness(route.getReadiness());
        item.setReadinessReason(route.getReadinessReason());
        item.setDepartureTime(route.getDepartureTime());
        item.setRouteEta(route.getEta());
        item.setDriverId(route.getDriverId());
        item.setTruckId(route.getTruckId());

        Vehicle truck = truckFor(route, vehicles);
        if (truck != null) {
            item.setTruckType(truck.getType());
            item.setTruckCapacity(truck.getCapacity() > 0 ? truck.getCapacity() : null);
        }

        boolean ownDepot = depot != null && LinehaulUtil.clean(route.getWarehouseId()).toUpperCase()
                .equals(depot.getWarehouseId().toUpperCase());
        item.setCrossWarehouse(!ownDepot);
        item.setNeedsDriver(route.getDriverId() == null || route.getDriverId().isBlank());
        item.setNeedsTruck(route.getTruckId() == null || route.getTruckId().isBlank());

        EtaEstimator.Estimate eta = EtaEstimator.estimate(route, sequence, load, unload);
        item.setEtaKnown(eta.isKnown());
        item.setTravelHours(eta.getTravelHours());
        item.setTravelTime(eta.getDurationText());
        item.setOrderEta(eta.isKnown() ? eta.getArrivalClock() : null);

        CapacityRules.applyCapacity(route, order.getWeight(), item);

        List<String> reasons = new ArrayList<>();
        reasons.add(item.getMatchLabel());
        reasons.add(ownDepot
                ? "Route is owned by " + label(depot == null ? "this warehouse" : depot.getHubLocation()) + "."
                : "Cross-warehouse route with a valid stop at " + label(load < sequence.size() ? sequence.get(load) : "-") + ".");
        reasons.add("Fits the remaining capacity: " + CapacityRules.number(order.getWeight()) + " kg of "
                + CapacityRules.number(Math.max(0, route.getMaxCapacity() - route.getCurrentWeight())) + " kg free.");
        reasons.add(eta.isKnown() ? "Estimated " + eta.getDurationText() + " on board, arriving " + eta.getArrivalClock() + "."
                : "No ETA on this route (departure time is not set).");
        item.setReasons(reasons);

        List<String> warnings = new ArrayList<>();
        if (truck == null && item.isNeedsTruck()) {
            warnings.add("No truck assigned yet - pick one from this warehouse to complete the route.");
        }
        if (truck != null && (truck.getCapacity() <= 0)) {
            warnings.add("Capacity of truck " + route.getTruckId() + " is not recorded, so the vehicle limit could not be checked.");
        }
        if (item.isNeedsDriver()) {
            warnings.add("No driver assigned yet - pick one from this warehouse to complete the route.");
        }
        if (!eta.isKnown()) {
            warnings.add("ETA could not be calculated because the departure time is missing.");
        }
        if (item.isCrossWarehouse()) {
            warnings.add("This route originates in " + label(route.getWarehouseId())
                    + ". The order will be loaded at " + label(item.getPickupLabel())
                    + " when the route reaches that stop, and the driver and truck of that route are used.");
        }
        item.setWarnings(warnings);
        return new MatchOutcome(route, item);
    }

    /** A route that starts at our depot and ends in the order's destination is the strongest match. */
    private boolean sameLane(Order order, Warehouse depot, Route route, int load) {
        if (load != 0) {
            return false;
        }
        boolean sameDepot = depot == null
                || LinehaulUtil.clean(route.getWarehouseId()).toUpperCase().equals(depot.getWarehouseId().toUpperCase());
        return sameDepot && (LocationMatcher.same(route.getOrigin(), order.getOrigin())
                || LocationMatcher.isThisWarehouse(route.getOrigin(), depot));
    }

    private String matchLabel(String matchType, Order order, Route route) {
        return switch (matchType) {
            case MATCH_EXACT -> "Exact lane match: " + label(route.getOrigin()) + " to " + label(route.getDestination())
                    + " is the same lane as the order.";
            case MATCH_DESTINATION -> "Route ends in " + label(order.getDestination())
                    + ", so the order is delivered at the final stop.";
            default -> label(order.getDestination()) + " is a scheduled stop on this route ("
                    + label(route.getOrigin()) + " to " + label(route.getDestination()) + ").";
        };
    }

    /**
     * Index of the point where the order is unloaded: the final destination, else the first matching
     * stop. Delegates to {@link LocationMatcher#unloadIndex(List, String)} so that the manual
     * drag-and-drop in {@code RouteService} applies exactly the same rule.
     */
    private int unloadIndex(List<String> sequence, String destination) {
        return LocationMatcher.unloadIndex(sequence, destination);
    }

    /** Index of the point where the order can be picked up, or -1 when the route never passes it. */
    private int loadIndex(List<String> sequence, Order order, Warehouse depot, Route route) {
        if (sequence.isEmpty()) {
            return -1;
        }
        if (depot != null && LinehaulUtil.clean(route.getWarehouseId()).toUpperCase()
                .equals(depot.getWarehouseId().toUpperCase())) {
            // A route owned by this depot starts there, but if the order says it sits somewhere else on
            // the same run (odd master data), believe the order rather than silently mis-loading it.
            int byOrderOrigin = LocationMatcher.indexOf(sequence, order.getOrigin());
            return byOrderOrigin > 0 ? byOrderOrigin : 0;
        }
        int byOrderOrigin = LocationMatcher.indexOf(sequence, order.getOrigin());
        if (byOrderOrigin >= 0) {
            return byOrderOrigin;
        }
        for (int i = 0; i < sequence.size(); i++) {
            if (LocationMatcher.matchesAny(sequence.get(i), LocationMatcher.namesOf(depot))) {
                return i;
            }
        }
        return -1;
    }

    private List<String> sequenceOf(Route route) {
        List<String> decorated = route.getStopSequence();
        if (decorated != null && !decorated.isEmpty()) {
            return decorated;
        }
        List<String> sequence = new ArrayList<>();
        sequence.add(route.getOrigin());
        if (route.getStops() != null) {
            for (String stop : route.getStops()) {
                if (stop != null && !stop.isBlank()) {
                    sequence.add(stop.trim());
                }
            }
        }
        sequence.add(route.getDestination());
        return sequence;
    }

    private Vehicle truckFor(Route route, Map<String, Vehicle> vehicles) {
        String truckId = LinehaulUtil.clean(route.getTruckId());
        if (truckId.isEmpty() || vehicles == null) {
            return null;
        }
        return vehicles.get(truckId.toUpperCase(Locale.ROOT));
    }

    private void reject(MatchResult result, Route route, String stage, String reason) {
        result.addRejected(new RouteRejection(route.getRouteId(), LinehaulUtil.clean(route.getWarehouseId()),
                route.getOrigin(), route.getDestination(), stage, reason));
    }

    static String label(String value) {
        String cleaned = LinehaulUtil.clean(value);
        return cleaned.isEmpty() ? "unknown" : cleaned;
    }
}
