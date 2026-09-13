package com.example.linehaul.service;

import com.example.linehaul.model.Order;
import com.example.linehaul.model.Route;
import com.example.linehaul.model.Status;
import com.example.linehaul.model.Warehouse;
import com.example.linehaul.repository.DriverRepository;
import com.example.linehaul.repository.OrderRepository;
import com.example.linehaul.repository.VehicleRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DashboardService {

    private final OrderRepository orderRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final RouteService routeService;
    private final OrderService orderService;
    private final DriverService driverService;
    private final VehicleService vehicleService;
    private final WarehouseService warehouseService;

    public DashboardService(OrderRepository orderRepository,
                            VehicleRepository vehicleRepository,
                            DriverRepository driverRepository,
                            RouteService routeService,
                            OrderService orderService,
                            DriverService driverService,
                            VehicleService vehicleService,
                            WarehouseService warehouseService) {
        this.orderRepository = orderRepository;
        this.vehicleRepository = vehicleRepository;
        this.driverRepository = driverRepository;
        this.routeService = routeService;
        this.orderService = orderService;
        this.driverService = driverService;
        this.vehicleService = vehicleService;
        this.warehouseService = warehouseService;
    }

    /** Whole network view, used by the rule based chat assistant. */
    public Summary summary() {
        return summary(null);
    }

    /**
     * @param warehouseId the depot the operator selected. Operational numbers are scoped to it, while
     *                    {@code networkRoutes} always reports the whole linehaul network so that
     *                    cross-warehouse capacity is visible.
     */
    public Summary summary(String warehouseId) {
        String depot = WarehouseScope.key(warehouseId);
        List<Route> routes = depot.isEmpty()
                ? routeService.findAll(null)
                : routeService.findAll(null, depot);

        long readyRoutes = routes.stream().filter(r -> Status.READY.equals(r.getReadiness())).count();
        long blockedRoutes = routes.stream().filter(r -> Status.BLOCKED.equals(r.getReadiness())).count();
        long activeRoutes = routes.stream()
                .filter(r -> Status.DISPATCHED.equalsIgnoreCase(r.getStatus())
                        || Status.IN_TRANSIT.equalsIgnoreCase(r.getStatus()))
                .count();

        List<Route> activeRouteList = routes.stream()
                .filter(r -> Status.DISPATCHED.equalsIgnoreCase(r.getStatus())
                        || Status.IN_TRANSIT.equalsIgnoreCase(r.getStatus())
                        || Status.READY.equalsIgnoreCase(r.getStatus()))
                .toList();

        List<Order> orders = orderService.findAll(null, null, depot);
        List<Order> unassigned = orderService.findUnassigned(depot);

        List<Order> toDispatch = orders.stream()
                .filter(o -> List.of(Status.CREATED, Status.READY, Status.MANIFESTED, Status.ASSIGNED)
                        .contains(LinehaulUtil.clean(o.getStatus()).toUpperCase()))
                .sorted((a, b) -> a.getOrderId().compareTo(b.getOrderId()))
                .toList();

        int capacity = routes.stream().mapToInt(Route::getMaxCapacity).sum();
        int loaded = routes.stream().mapToInt(Route::getCurrentWeight).sum();

        Summary summary = new Summary();
        summary.setWarehouseId(depot);
        summary.setWarehouseName(warehouseName(depot));
        summary.setWarehouseDesignation(warehouseDesignation(depot));
        summary.setTotalOrders(orders.size());
        summary.setUnassignedOrders(unassigned.size());
        summary.setAssignedOrders((int) orders.stream()
                .filter(o -> !LinehaulUtil.clean(o.getRouteId()).isEmpty()).count());
        summary.setTotalRoutes(routes.size());
        summary.setActiveRoutes((int) activeRoutes);
        summary.setReadyRoutes((int) readyRoutes);
        summary.setBlockedRoutes((int) blockedRoutes);
        summary.setTotalVehicles(depot.isEmpty()
                ? (int) vehicleRepository.count() : vehicleService.findAll(depot).size());
        summary.setAvailableVehicles(depot.isEmpty()
                ? (int) vehicleRepository.findAll().stream().filter(v -> Status.AVAILABLE.equalsIgnoreCase(v.getStatus())).count()
                : vehicleService.findAvailable(0, depot).size());
        summary.setTotalDrivers(depot.isEmpty()
                ? (int) driverRepository.count() : driverService.findAll(depot).size());
        summary.setAvailableDrivers(depot.isEmpty()
                ? (int) driverRepository.findAll().stream().filter(d -> Status.AVAILABLE.equalsIgnoreCase(d.getStatus())).count()
                : driverService.findAvailable(depot).size());
        summary.setTotalCapacity(capacity);
        summary.setLoadedWeight(loaded);
        summary.setCapacityPercent(LinehaulUtil.capacityPercent(loaded, capacity));
        summary.setNetworkRoutes(routeService.findAll(null).size());
        summary.setNetworkWarehouses(warehouseService.findAll().size());
        summary.setOrdersToDispatch(toDispatch);
        summary.setActiveRouteList(activeRouteList);
        summary.setUnassignedOrderList(unassigned);
        return summary;
    }

    /**
     * How the depot is named on the screens: by its PLACE ("Bengaluru"), because that is the token
     * its orders and routes are written with. The "Warehouse A" designation belongs to the warehouse
     * switcher and is reported separately as {@code warehouseDesignation}.
     */
    private String warehouseName(String depot) {
        if (depot.isEmpty()) {
            return "All warehouses";
        }
        return warehouseService.find(depot)
                .map(DashboardService::placeOf)
                .orElse(depot);
    }

    private String warehouseDesignation(String depot) {
        if (depot.isEmpty()) {
            return "All warehouses";
        }
        return warehouseService.find(depot).map(Warehouse::getName).orElse(depot);
    }

    /** The city of a depot, falling back to its hub location and then to its designation. */
    static String placeOf(Warehouse warehouse) {
        String city = LinehaulUtil.clean(warehouse.getCity());
        if (!city.isEmpty()) {
            return city;
        }
        String hub = LinehaulUtil.clean(warehouse.getHubLocation());
        return hub.isEmpty() ? LinehaulUtil.clean(warehouse.getName()) : hub;
    }

    public static class Summary {
        private String warehouseId;
        private String warehouseName;
        /** The "Warehouse A" style designation, for the switching interface only. */
        private String warehouseDesignation;
        private int totalOrders;
        private int unassignedOrders;
        private int assignedOrders;
        private int totalRoutes;
        private int activeRoutes;
        private int readyRoutes;
        private int blockedRoutes;
        private int totalVehicles;
        private int availableVehicles;
        private int totalDrivers;
        private int availableDrivers;
        private int totalCapacity;
        private int loadedWeight;
        private int capacityPercent;
        private int networkRoutes;
        private int networkWarehouses;
        private List<Order> ordersToDispatch;
        private List<Route> activeRouteList;
        private List<Order> unassignedOrderList;

        public String getWarehouseId() {
            return warehouseId;
        }

        public void setWarehouseId(String warehouseId) {
            this.warehouseId = warehouseId;
        }

        public String getWarehouseName() {
            return warehouseName;
        }

        public void setWarehouseName(String warehouseName) {
            this.warehouseName = warehouseName;
        }

        public String getWarehouseDesignation() {
            return warehouseDesignation;
        }

        public void setWarehouseDesignation(String warehouseDesignation) {
            this.warehouseDesignation = warehouseDesignation;
        }

        public int getTotalOrders() {
            return totalOrders;
        }

        public void setTotalOrders(int totalOrders) {
            this.totalOrders = totalOrders;
        }

        public int getUnassignedOrders() {
            return unassignedOrders;
        }

        public void setUnassignedOrders(int unassignedOrders) {
            this.unassignedOrders = unassignedOrders;
        }

        public int getAssignedOrders() {
            return assignedOrders;
        }

        public void setAssignedOrders(int assignedOrders) {
            this.assignedOrders = assignedOrders;
        }

        public int getTotalRoutes() {
            return totalRoutes;
        }

        public void setTotalRoutes(int totalRoutes) {
            this.totalRoutes = totalRoutes;
        }

        public int getActiveRoutes() {
            return activeRoutes;
        }

        public void setActiveRoutes(int activeRoutes) {
            this.activeRoutes = activeRoutes;
        }

        public int getReadyRoutes() {
            return readyRoutes;
        }

        public void setReadyRoutes(int readyRoutes) {
            this.readyRoutes = readyRoutes;
        }

        public int getBlockedRoutes() {
            return blockedRoutes;
        }

        public void setBlockedRoutes(int blockedRoutes) {
            this.blockedRoutes = blockedRoutes;
        }

        public int getTotalVehicles() {
            return totalVehicles;
        }

        public void setTotalVehicles(int totalVehicles) {
            this.totalVehicles = totalVehicles;
        }

        public int getAvailableVehicles() {
            return availableVehicles;
        }

        public void setAvailableVehicles(int availableVehicles) {
            this.availableVehicles = availableVehicles;
        }

        public int getTotalDrivers() {
            return totalDrivers;
        }

        public void setTotalDrivers(int totalDrivers) {
            this.totalDrivers = totalDrivers;
        }

        public int getAvailableDrivers() {
            return availableDrivers;
        }

        public void setAvailableDrivers(int availableDrivers) {
            this.availableDrivers = availableDrivers;
        }

        public int getTotalCapacity() {
            return totalCapacity;
        }

        public void setTotalCapacity(int totalCapacity) {
            this.totalCapacity = totalCapacity;
        }

        public int getLoadedWeight() {
            return loadedWeight;
        }

        public void setLoadedWeight(int loadedWeight) {
            this.loadedWeight = loadedWeight;
        }

        public int getCapacityPercent() {
            return capacityPercent;
        }

        public void setCapacityPercent(int capacityPercent) {
            this.capacityPercent = capacityPercent;
        }

        public int getNetworkRoutes() {
            return networkRoutes;
        }

        public void setNetworkRoutes(int networkRoutes) {
            this.networkRoutes = networkRoutes;
        }

        public int getNetworkWarehouses() {
            return networkWarehouses;
        }

        public void setNetworkWarehouses(int networkWarehouses) {
            this.networkWarehouses = networkWarehouses;
        }

        public List<Order> getOrdersToDispatch() {
            return ordersToDispatch;
        }

        public void setOrdersToDispatch(List<Order> ordersToDispatch) {
            this.ordersToDispatch = ordersToDispatch;
        }

        public List<Route> getActiveRouteList() {
            return activeRouteList;
        }

        public void setActiveRouteList(List<Route> activeRouteList) {
            this.activeRouteList = activeRouteList;
        }

        public List<Order> getUnassignedOrderList() {
            return unassignedOrderList;
        }

        public void setUnassignedOrderList(List<Order> unassignedOrderList) {
            this.unassignedOrderList = unassignedOrderList;
        }
    }
}
