package com.example.linehaul.service;

import com.example.linehaul.automation.LocationMatcher;
import com.example.linehaul.exception.BusinessException;
import com.example.linehaul.exception.NotFoundException;
import com.example.linehaul.model.Driver;
import com.example.linehaul.model.Order;
import com.example.linehaul.model.Route;
import com.example.linehaul.model.Status;
import com.example.linehaul.model.Vehicle;
import com.example.linehaul.repository.DriverRepository;
import com.example.linehaul.repository.OrderRepository;
import com.example.linehaul.repository.RouteRepository;
import com.example.linehaul.repository.VehicleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RouteService {

    private final RouteRepository routeRepository;
    private final OrderRepository orderRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final ResourceLookup lookup;

    /** Remembers how long each lane takes; null in the unit tests that do not need it. */
    private final LaneDurationService laneDurations;

    public RouteService(RouteRepository routeRepository,
                        OrderRepository orderRepository,
                        VehicleRepository vehicleRepository,
                        DriverRepository driverRepository,
                        ResourceLookup lookup) {
        this(routeRepository, orderRepository, vehicleRepository, driverRepository, lookup, null);
    }

    @Autowired
    public RouteService(RouteRepository routeRepository,
                        OrderRepository orderRepository,
                        VehicleRepository vehicleRepository,
                        DriverRepository driverRepository,
                        ResourceLookup lookup,
                        LaneDurationService laneDurations) {
        this.routeRepository = routeRepository;
        this.orderRepository = orderRepository;
        this.vehicleRepository = vehicleRepository;
        this.driverRepository = driverRepository;
        this.lookup = lookup;
        this.laneDurations = laneDurations;
    }


    public List<Route> findAll(String search) {
        return findAll(search, null);
    }

    /**
     * @param warehouseId when set, only routes owned by this warehouse are returned. The smart
     *                    assignment engine deliberately calls this with {@code null} so that it can
     *                    consider the whole network, including cross-warehouse routes.
     */
    public List<Route> findAll(String search, String warehouseId) {
        String needle = LinehaulUtil.clean(search).toLowerCase();
        String depot = LinehaulUtil.clean(warehouseId).toUpperCase();
        return routeRepository.findAll().stream()
                .filter(r -> needle.isEmpty() || r.getRouteId().toLowerCase().contains(needle))
                .filter(r -> depot.isEmpty() || depot.equalsIgnoreCase(LinehaulUtil.clean(r.getWarehouseId()).toUpperCase()))
                .sorted((a, b) -> a.getRouteId().compareTo(b.getRouteId()))
                .map(this::decorate)
                .toList();
    }

    /** Suggests the next free route id, e.g. {@code LH-4012}. */
    public String nextRouteId() {
        int highest = 4000;
        for (Route route : routeRepository.findAll()) {
            String digits = LinehaulUtil.clean(route.getRouteId()).replaceAll("\\D", "");
            if (digits.length() >= 4) {
                try {
                    highest = Math.max(highest, Integer.parseInt(digits));
                } catch (NumberFormatException ignored) {
                    // Route ids that are not numeric simply do not move the counter.
                }
            }
        }
        String candidate = "LH-" + (highest + 1);
        while (routeRepository.existsByRouteId(candidate)) {
            highest++;
            candidate = "LH-" + (highest + 1);
        }
        return candidate;
    }

    public Route findByRouteId(String routeId) {
        return decorate(routeRepository.findByRouteId(routeId)
                .orElseThrow(() -> new NotFoundException("Route not found.")));
    }

    public List<Order> findOrders(String routeId) {
        Route route = routeRepository.findByRouteId(routeId)
                .orElseThrow(() -> new NotFoundException("Route not found."));
        List<Order> orders = new ArrayList<>();
        for (String orderId : route.getOrderIds()) {
            orderRepository.findByOrderId(orderId).ifPresent(orders::add);
        }
        return orders;
    }

    public Route create(Route route) {
        return create(route, null);
    }

    /** @param warehouseId depot that owns the new route, when the body does not say so itself. */
    public Route create(Route route, String warehouseId) {
        route.setRouteId(LinehaulUtil.clean(route.getRouteId()));

        if (LinehaulUtil.clean(route.getWarehouseId()).isEmpty() && WarehouseScope.isActive(warehouseId)) {
            route.setWarehouseId(WarehouseScope.key(warehouseId));
        } else if (WarehouseScope.isActive(route.getWarehouseId())) {
            route.setWarehouseId(WarehouseScope.key(route.getWarehouseId()));
        }

        if (route.getRouteId().isEmpty()) {
            route.setRouteId(nextRouteId());
        }
        if (routeRepository.existsByRouteId(route.getRouteId())) {
            throw new BusinessException("Route ID " + route.getRouteId() + " already exists.");
        }
        if (route.getMaxCapacity() <= 0) {
            throw new BusinessException("Maximum capacity must be greater than zero.");
        }
        if (LinehaulUtil.clean(route.getDepartureTime()).isEmpty()) {
            route.setDepartureTime("20:00");
        }
        if (route.getStops() == null) {
            route.setStops(new ArrayList<>());
        }

        // No duration on the request? The network already knows how long this lane takes, so the
        // dispatcher does not have to type it again.
        if (route.getTravelDuration() <= 0 && laneDurations != null) {
            Integer known = laneDurations.durationFor(route.getOrigin(), route.getDestination());
            if (known != null && known > 0) {
                route.setTravelDuration(known);
            }
        }

        route.setOrderIds(new ArrayList<>());
        route.setCurrentWeight(0);
        route.setTruckId(null);
        route.setDriverId(null);
        route.setStatus(Status.DRAFT);
        route.setEta(LinehaulUtil.calculateEta(route.getDepartureTime(), route.getTravelDuration()));
        Route saved = decorate(routeRepository.save(route));
        rememberLane(saved);
        return saved;
    }

    public Route update(String routeId, Route changes) {
        Route route = routeRepository.findByRouteId(routeId)
                .orElseThrow(() -> new NotFoundException("Route not found."));

        if (changes.getOrigin() != null) route.setOrigin(changes.getOrigin());
        if (changes.getDestination() != null) route.setDestination(changes.getDestination());
        if (changes.getDepartureTime() != null && !changes.getDepartureTime().isBlank()) {
            route.setDepartureTime(changes.getDepartureTime());
        }
        if (changes.getTravelDuration() >= 0) {
            route.setTravelDuration(changes.getTravelDuration());
        }
        if (changes.getMaxCapacity() > 0) {
            if (changes.getMaxCapacity() < route.getCurrentWeight()) {
                throw new BusinessException("Maximum capacity cannot be lower than the current weight.");
            }
            route.setMaxCapacity(changes.getMaxCapacity());
        }
        if (changes.getStops() != null) {
            route.setStops(changes.getStops());
        }
        if (changes.getWarehouseId() != null && !changes.getWarehouseId().isBlank()) {
            route.setWarehouseId(changes.getWarehouseId());
        }

        route.setEta(LinehaulUtil.calculateEta(route.getDepartureTime(), route.getTravelDuration()));
        for (String orderId : route.getOrderIds()) {
            orderRepository.findByOrderId(orderId).ifPresent(order -> {
                order.setEta(route.getEta());
                orderRepository.save(order);
            });
        }
        Route saved = decorate(routeRepository.save(route));
        rememberLane(saved);
        return saved;
    }

    public Route assignOrder(String routeId, String orderId) {
        Route route = routeRepository.findByRouteId(routeId)
                .orElseThrow(() -> new NotFoundException("Route not found."));
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found."));

        requireEditable(route);

        if (order.getRouteId() != null && !order.getRouteId().isEmpty()) {
            if (order.getRouteId().equals(route.getRouteId())) {
                throw new BusinessException("Order " + orderId + " is already on route " + routeId + ".");
            }
            throw new BusinessException("Order " + orderId + " is already assigned to route "
                    + order.getRouteId() + ".");
        }

        requireDestinationMatch(route, order);

        int newWeight = route.getCurrentWeight() + order.getWeight();
        if (newWeight > route.getMaxCapacity()) {
            throw new BusinessException("Route capacity exceeded.");
        }

        route.getOrderIds().add(order.getOrderId());
        route.setCurrentWeight(newWeight);

     
        route.setEta(LinehaulUtil.calculateEta(route.getDepartureTime(), route.getTravelDuration()));
        routeRepository.save(route);

        order.setRouteId(route.getRouteId());
        order.setStatus(Status.ASSIGNED);
        order.setEta(route.getEta());
        orderRepository.save(order);

        return decorate(routeRepository.save(refreshStatus(route)));
    }

    public Route unassignOrder(String routeId, String orderId) {
        Route route = routeRepository.findByRouteId(routeId)
                .orElseThrow(() -> new NotFoundException("Route not found."));
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found."));

        requireEditable(route);

        if (!route.getOrderIds().contains(order.getOrderId())) {
            throw new BusinessException("Order " + orderId + " is not on route " + routeId + ".");
        }

        route.getOrderIds().remove(order.getOrderId());
        route.setCurrentWeight(Math.max(0, route.getCurrentWeight() - order.getWeight()));
        routeRepository.save(route);

        order.setRouteId(null);
        order.setEta(null);
        order.setStatus(Status.READY);
        orderRepository.save(order);

        return decorate(routeRepository.save(refreshStatus(route)));
    }

    public Route assignTruck(String routeId, String truckId) {
        Route route = routeRepository.findByRouteId(routeId)
                .orElseThrow(() -> new NotFoundException("Route not found."));
        Vehicle truck = vehicleRepository.findByTruckId(truckId)
                .orElseThrow(() -> new NotFoundException("Truck not found."));

        requireEditable(route);

        String truckRoute = truck.getRouteId();
        if (truckRoute != null && !truckRoute.isEmpty() && !truckRoute.equals(route.getRouteId())) {
            throw new BusinessException("Truck is already assigned.");
        }
        if (!Status.AVAILABLE.equalsIgnoreCase(truck.getStatus())) {
            throw new BusinessException("Truck is not available.");
        }
        if (truck.getCapacity() < route.getCurrentWeight()) {
            throw new BusinessException("Truck capacity is smaller than the route weight.");
        }

        route.setTruckId(truck.getTruckId());
        truck.setRouteId(route.getRouteId());
        truck.setStatus(Status.ASSIGNED);
        vehicleRepository.save(truck);

        return decorate(routeRepository.save(refreshStatus(route)));
    }

    public Route unassignTruck(String routeId) {
        Route route = routeRepository.findByRouteId(routeId)
                .orElseThrow(() -> new NotFoundException("Route not found."));
        requireEditable(route);

        if (route.getTruckId() != null) {
            lookup.findTruck(route.getTruckId()).ifPresent(truck -> {
                truck.setRouteId(null);
                truck.setStatus(Status.AVAILABLE);
                vehicleRepository.save(truck);
            });
        }
        route.setTruckId(null);
        return decorate(routeRepository.save(refreshStatus(route)));
    }

    public Route assignDriver(String routeId, String driverId) {
        Route route = routeRepository.findByRouteId(routeId)
                .orElseThrow(() -> new NotFoundException("Route not found."));
        Driver driver = driverRepository.findByDriverId(driverId)
                .orElseThrow(() -> new NotFoundException("Driver not found."));

        requireEditable(route);

        String driverRoute = driver.getRouteId();
        if (driverRoute != null && !driverRoute.isEmpty() && !driverRoute.equals(route.getRouteId())) {
            throw new BusinessException("Driver is already assigned.");
        }
        if (!Status.AVAILABLE.equalsIgnoreCase(driver.getStatus())) {
            throw new BusinessException("Driver is not available.");
        }

        route.setDriverId(driver.getDriverId());
        driver.setRouteId(route.getRouteId());
        driver.setStatus(Status.ASSIGNED);
        driverRepository.save(driver);

        return decorate(routeRepository.save(refreshStatus(route)));
    }

    public Route unassignDriver(String routeId) {
        Route route = routeRepository.findByRouteId(routeId)
                .orElseThrow(() -> new NotFoundException("Route not found."));
        requireEditable(route);

        if (route.getDriverId() != null) {
            lookup.findDriver(route.getDriverId()).ifPresent(driver -> {
                driver.setRouteId(null);
                driver.setStatus(Status.AVAILABLE);
                driverRepository.save(driver);
            });
        }
        route.setDriverId(null);
        return decorate(routeRepository.save(refreshStatus(route)));
    }

    public Route dispatch(String routeId) {
        Route route = routeRepository.findByRouteId(routeId)
                .orElseThrow(() -> new NotFoundException("Route not found."));

        if (Status.DISPATCHED.equalsIgnoreCase(route.getStatus())) {
            throw new BusinessException("Route is already dispatched.");
        }
        if (Status.IN_TRANSIT.equalsIgnoreCase(route.getStatus())
                || Status.COMPLETED.equalsIgnoreCase(route.getStatus())) {
            throw new BusinessException("Route cannot be dispatched.");
        }

        if (route.getOrderIds().isEmpty()) {
            throw new BusinessException("Route cannot be dispatched. Reason: no orders assigned.");
        }
        if (route.getTruckId() == null || route.getTruckId().isBlank()) {
            throw new BusinessException("Route cannot be dispatched. Reason: truck not assigned.");
        }
        if (route.getDriverId() == null || route.getDriverId().isBlank()) {
            throw new BusinessException("Route cannot be dispatched. Reason: driver not assigned.");
        }
        if (route.getCurrentWeight() > route.getMaxCapacity()) {
            throw new BusinessException("Route cannot be dispatched. Reason: route capacity exceeded.");
        }

        route.setStatus(Status.DISPATCHED);
        routeRepository.save(route);

        for (String orderId : route.getOrderIds()) {
            orderRepository.findByOrderId(orderId).ifPresent(order -> {
                order.setStatus(Status.DISPATCHED);
                orderRepository.save(order);
            });
        }

        lookup.findTruck(route.getTruckId()).ifPresent(truck -> {
            truck.setStatus(Status.IN_TRANSIT);
            vehicleRepository.save(truck);
        });
        lookup.findDriver(route.getDriverId()).ifPresent(driver -> {
            driver.setStatus(Status.IN_TRANSIT);
            driverRepository.save(driver);
        });

        return decorate(route);
    }

    /**
     * A route may only carry an order it can actually deliver.
     *
     * <p>This is the rule behind the drag-and-drop board: dropping a Vizag order on a route that runs
     * to Chennai is refused here, in the service, so it is refused no matter where the drop came from
     * (the board, the "assign to route" select on the order modal, or a direct API call). The route
     * qualifies when the order's destination is its final stop <em>or</em> one of its intermediate
     * stops - the very same check {@code RouteMatchingService} uses for the smart recommendations.</p>
     */
    private void requireDestinationMatch(Route route, Order order) {
        String destination = LinehaulUtil.clean(order.getDestination());
        if (destination.isEmpty()) {
            return;
        }
        List<String> sequence = stopSequenceOf(route);
        if (LocationMatcher.reaches(sequence, destination)) {
            return;
        }
        throw new BusinessException("Destination mismatch: order " + order.getOrderId() + " is going to "
                + destination + ", but route " + route.getRouteId() + " runs "
                + String.join(" > ", sequence) + " and never reaches " + destination + ".");
    }

    /** Origin, intermediate stops and destination of a route, in travel order. */
    private List<String> stopSequenceOf(Route route) {
        List<String> sequence = new ArrayList<>();
        String origin = LinehaulUtil.clean(route.getOrigin());
        if (!origin.isEmpty()) {
            sequence.add(origin);
        }
        if (route.getStops() != null) {
            for (String stop : route.getStops()) {
                if (stop != null && !stop.isBlank()) {
                    sequence.add(stop.trim());
                }
            }
        }
        String destination = LinehaulUtil.clean(route.getDestination());
        if (!destination.isEmpty()) {
            sequence.add(destination);
        }
        return sequence;
    }

    /** Teaches the lane memory what this route says about its own origin-to-destination duration. */
    private void rememberLane(Route route) {
        if (laneDurations == null) {
            return;
        }
        try {
            laneDurations.remember(route.getOrigin(), route.getDestination(), route.getTravelDuration());
        } catch (RuntimeException ignored) {
            // Learning a lane is a convenience, never a reason to fail saving a route.
        }
    }

    private void requireEditable(Route route) {
        if (!isEditable(route)) {
            String status = route.getStatus() == null ? "" : route.getStatus().toUpperCase();
            throw new BusinessException("Route " + route.getRouteId()
                    + " is already " + status.replace('_', ' ').toLowerCase()
                    + " and cannot be changed.");
        }
    }

    /**
     * Single source of truth for "can this route still take changes?". The smart assignment engine
     * uses the very same rule, so a route can never be recommended here and rejected there.
     */
    public boolean isEditable(Route route) {
        String status = route.getStatus() == null ? "" : route.getStatus().toUpperCase();
        return !status.equals(Status.DISPATCHED) && !status.equals(Status.IN_TRANSIT)
                && !status.equals(Status.COMPLETED);
    }

 
    private Route refreshStatus(Route route) {
        route.setEta(LinehaulUtil.calculateEta(route.getDepartureTime(), route.getTravelDuration()));
        decorate(route);

        String status = route.getStatus() == null ? "" : route.getStatus().toUpperCase();
        if (status.equals(Status.DISPATCHED) || status.equals(Status.IN_TRANSIT)
                || status.equals(Status.COMPLETED)) {
            return route;
        }
        route.setStatus(route.getReadiness().equals(Status.READY) ? Status.READY : Status.BLOCKED);
        return route;
    }

    private Route decorate(Route route) {
        if (route.getOrderIds() == null) {
            route.setOrderIds(new ArrayList<>());
        }
        route.setEta(LinehaulUtil.calculateEta(route.getDepartureTime(), route.getTravelDuration()));
        route.setCapacityPercent(LinehaulUtil.capacityPercent(route.getCurrentWeight(), route.getMaxCapacity()));
        route.setAvailableCapacity(Math.max(0, route.getMaxCapacity() - route.getCurrentWeight()));

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
        route.setStopSequence(sequence);

        boolean hasOrders = !route.getOrderIds().isEmpty();
        boolean hasTruck = route.getTruckId() != null && !route.getTruckId().isBlank();
        boolean hasDriver = route.getDriverId() != null && !route.getDriverId().isBlank();
        boolean capacityOk = route.getCurrentWeight() <= route.getMaxCapacity();

        route.setHasOrders(hasOrders);
        route.setHasTruck(hasTruck);
        route.setHasDriver(hasDriver);
        route.setCapacityOk(capacityOk);

        String status = route.getStatus() == null ? "" : route.getStatus().toUpperCase();
        if (status.equals(Status.DISPATCHED) || status.equals(Status.IN_TRANSIT)
                || status.equals(Status.COMPLETED)) {
            route.setReadiness(status);
            route.setReadinessReason("Route " + status.replace('_', ' ').toLowerCase() + ".");
            return route;
        }

        if (hasOrders && hasTruck && hasDriver && capacityOk) {
            route.setReadiness(Status.READY);
            route.setReadinessReason("Ready to dispatch");
        } else {
            route.setReadiness(Status.BLOCKED);
            if (!hasOrders) route.setReadinessReason("No orders assigned");
            else if (!hasTruck) route.setReadinessReason("Truck not assigned");
            else if (!hasDriver) route.setReadinessReason("Driver not assigned");
            else route.setReadinessReason("Route capacity exceeded");
        }
        return route;
    }
}
