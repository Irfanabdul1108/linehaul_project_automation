package com.example.linehaul.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.stereotype.Component;

import com.example.linehaul.exception.BusinessException;
import com.example.linehaul.model.Driver;
import com.example.linehaul.model.Order;
import com.example.linehaul.model.Route;
import com.example.linehaul.model.Status;
import com.example.linehaul.model.Vehicle;
import com.example.linehaul.model.Warehouse;
import com.example.linehaul.repository.DriverRepository;
import com.example.linehaul.repository.OrderRepository;
import com.example.linehaul.repository.RouteRepository;
import com.example.linehaul.repository.VehicleRepository;
import com.example.linehaul.repository.LaneDurationRepository;
import com.example.linehaul.repository.WarehouseRepository;
import com.example.linehaul.service.LaneDurationService;
import com.example.linehaul.service.LinehaulUtil;
/**
 * Prepares MongoDB on startup.
 *
 * <p>Three things happen, in this order, and none of them destroys data unless you explicitly ask for
 * it (see {@code app.demo.reset} in {@code application.properties}):</p>
 * <ol>
 *   <li>the four warehouses are created if they do not exist yet;</li>
 *   <li>an <b>empty</b> operational database is filled with {@link DemoNetwork}, the four-depot demo
 *       network that contains one example of every assignment case;</li>
 *   <li>existing documents that pre-date multi-warehouse support are migrated: they get the first
 *       warehouse as their home, so nothing is lost and nothing is invented.</li>
 * </ol>
 *
 * <p>Every field the engine relies on - route load, order list, ETA, and the route id stored back on
 * the truck and the driver - is computed from the seeded rows, so the demo data can never disagree
 * with itself.</p>
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    /** Depot that legacy documents are attributed to during the migration. */
    private static final String LEGACY_WAREHOUSE = "WH-A";

    private final OrderRepository orderRepository;
    private final RouteRepository routeRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final WarehouseRepository warehouseRepository;
    private final LaneDurationRepository laneDurationRepository;
    private final LaneDurationService laneDurationService;
    private final boolean resetForDemo;
private final MongoDatabaseFactory mongoDatabaseFactory;
    public DataSeeder(OrderRepository orderRepository,
                      RouteRepository routeRepository,
                      VehicleRepository vehicleRepository,
                      DriverRepository driverRepository,
                      WarehouseRepository warehouseRepository,
                      LaneDurationRepository laneDurationRepository,
                      LaneDurationService laneDurationService,
                     MongoDatabaseFactory mongoDatabaseFactory,
                      @Value("${app.demo.reset:false}") boolean resetForDemo) {
                        this.mongoDatabaseFactory = mongoDatabaseFactory;
        this.orderRepository = orderRepository;
        this.routeRepository = routeRepository;
        this.vehicleRepository = vehicleRepository;
        this.driverRepository = driverRepository;
        this.warehouseRepository = warehouseRepository;
        this.laneDurationRepository = laneDurationRepository;
        this.laneDurationService = laneDurationService;
        this.resetForDemo = resetForDemo;
    }

    @Override
    public void run(String... args) {
        log.info("==============================================");
log.info("MongoDB database being used: {}", 
        mongoDatabaseFactory.getMongoDatabase().getName());
log.info("Orders found: {}", orderRepository.count());
log.info("Routes found: {}", routeRepository.count());
log.info("Vehicles found: {}", vehicleRepository.count());
log.info("Drivers found: {}", driverRepository.count());
log.info("Warehouses found: {}", warehouseRepository.count());
log.info("==============================================");
        if (resetForDemo) {
            log.warn("app.demo.reset is on - dropping the operational collections and reloading the demo network.");
            orderRepository.deleteAll();
            routeRepository.deleteAll();
            vehicleRepository.deleteAll();
            driverRepository.deleteAll();
            warehouseRepository.deleteAll();
            laneDurationRepository.deleteAll();
        }

        int createdWarehouses = ensureWarehouses();
        int renamedHubs = ensureCityHubLocations();

        boolean hasOperationalData = orderRepository.count() > 0 || routeRepository.count() > 0;
        if (!hasOperationalData) {
            seedDemoNetwork();
            return;
        }

        int renamedLanes = renameDesignationLanes();
        if (renamedLanes > 0 || renamedHubs > 0) {
            log.info("Terminology migration: {} warehouse hub location(s) and {} lane(s) now name the "
                    + "city instead of the \"Warehouse X\" designation.", renamedHubs, renamedLanes);
        }
        seedLaneDurations();
        learnLanesFromRoutes();

        if (createdWarehouses > 0) {
            int stamped = migrateLegacyDocuments();
            log.info("Kept the existing data and put {} document(s) that had no warehouse on {}. "
                    + "Set app.demo.reset=true to load the four-warehouse demo network instead.",
                    stamped, LEGACY_WAREHOUSE);
        } else {
            log.info("Database already holds linehaul data - nothing seeded.");
        }
    }

    /**
     * Existing installations stored "Warehouse A" as the hub location. The whole application now
     * names the city, so the stored value is corrected in place - the designation stays available as
     * {@link Warehouse#getName()} for the warehouse switcher.
     */
    private int ensureCityHubLocations() {
        int changed = 0;
        for (Warehouse warehouse : warehouseRepository.findAll()) {
            String city = LinehaulUtil.clean(warehouse.getCity());
            if (city.isEmpty() || city.equalsIgnoreCase(LinehaulUtil.clean(warehouse.getHubLocation()))) {
                continue;
            }
            warehouse.setHubLocation(city);
            warehouseRepository.save(warehouse);
            changed++;
        }
        return changed;
    }

    /**
     * Rewrites lanes that were written with a depot designation ("Warehouse A") to that depot's city,
     * on orders and on routes, including intermediate stops. Only exact designation matches are
     * touched, so a place that merely contains the word is left alone.
     */
    private int renameDesignationLanes() {
        Map<String, String> cityOf = new LinkedHashMap<>();
        for (Warehouse warehouse : warehouseRepository.findAll()) {
            String city = LinehaulUtil.clean(warehouse.getCity());
            if (city.isEmpty()) {
                continue;
            }
            for (String alias : List.of(LinehaulUtil.clean(warehouse.getName()),
                    LinehaulUtil.clean(warehouse.getWarehouseId()), LinehaulUtil.clean(warehouse.getCode()))) {
                if (!alias.isEmpty() && !alias.equalsIgnoreCase(city)) {
                    cityOf.put(alias.toLowerCase(), city);
                }
            }
        }
        if (cityOf.isEmpty()) {
            return 0;
        }

        int changed = 0;
        for (Order order : orderRepository.findAll()) {
            String origin = rename(order.getOrigin(), cityOf);
            String destination = rename(order.getDestination(), cityOf);
            if (origin == null && destination == null) {
                continue;
            }
            if (origin != null) order.setOrigin(origin);
            if (destination != null) order.setDestination(destination);
            orderRepository.save(order);
            changed++;
        }
        for (Route route : routeRepository.findAll()) {
            boolean touched = false;
            String origin = rename(route.getOrigin(), cityOf);
            if (origin != null) {
                route.setOrigin(origin);
                touched = true;
            }
            String destination = rename(route.getDestination(), cityOf);
            if (destination != null) {
                route.setDestination(destination);
                touched = true;
            }
            List<String> stops = new ArrayList<>();
            for (String stop : route.getStops() == null ? List.<String>of() : route.getStops()) {
                String renamed = rename(stop, cityOf);
                if (renamed != null) {
                    touched = true;
                }
                String value = renamed == null ? LinehaulUtil.clean(stop) : renamed;
                // A stop that has become the origin or the destination is no longer an intermediate stop.
                if (!value.isEmpty() && !value.equalsIgnoreCase(LinehaulUtil.clean(route.getOrigin()))
                        && !value.equalsIgnoreCase(LinehaulUtil.clean(route.getDestination()))) {
                    stops.add(value);
                } else if (!value.isEmpty()) {
                    touched = true;
                }
            }
            if (touched) {
                route.setStops(stops);
                routeRepository.save(route);
                changed++;
            }
        }
        return changed;
    }

    /** @return the city this place should be called, or null when it is not a depot designation. */
    private String rename(String place, Map<String, String> cityOf) {
        String cleaned = LinehaulUtil.clean(place);
        if (cleaned.isEmpty()) {
            return null;
        }
        return cityOf.get(cleaned.toLowerCase());
    }

    /** Loads the reference travel times, without overwriting what real routes already taught us. */
    private void seedLaneDurations() {
        for (String row : DemoNetwork.LANE_DURATIONS) {
            String[] parts = row.split("\\|", -1);
            if (parts.length < 3) {
                continue;
            }
            try {
                laneDurationService.rememberIfUnknown(parts[0].trim(), parts[1].trim(),
                        Integer.parseInt(parts[2].trim()));
            } catch (NumberFormatException malformed) {
                log.warn("Lane duration row '{}' is not a number and was skipped.", row);
            }
        }
    }

    /** Every route that exists is itself an observation of how long its lane takes. */
    private void learnLanesFromRoutes() {
        for (Route route : routeRepository.findAll()) {
            laneDurationService.remember(route.getOrigin(), route.getDestination(), route.getTravelDuration());
        }
    }

    private int ensureWarehouses() {
        int created = 0;
        for (String row : DemoNetwork.WAREHOUSES) {
            String[] parts = row.split("\\|", -1);
            String warehouseId = parts[0].trim();
            if (warehouseRepository.existsByWarehouseId(warehouseId)) {
                continue;
            }
            // The hub location is the CITY, not the "Warehouse X" designation: lanes on orders and
            // routes name the place a truck actually drives to, and the designation is only used by
            // the warehouse switcher.
            Warehouse warehouse = new Warehouse(warehouseId, parts[1].trim(), parts[0].trim(),
                    parts[2].trim(), parts[2].trim(), parts[3].trim(), parts[4].trim());
            warehouseRepository.save(warehouse);
            created++;
        }
        if (created > 0) {
            log.info("Created {} predefined warehouse(s).", created);
        }
        return created;
    }

    private void seedDemoNetwork() {
        log.info("Database is empty - loading the four-warehouse demo network...");

        Map<String, Vehicle> trucks = new LinkedHashMap<>();
        for (String row : DemoNetwork.TRUCKS) {
            String[] parts = row.split("\\|", -1);
            Vehicle vehicle = new Vehicle();
            vehicle.setTruckId(parts[0].trim());
            vehicle.setType(parts[1].trim());
            vehicle.setCapacity(Integer.parseInt(parts[2].trim()));
            vehicle.setStatus(parts[3].trim());
            vehicle.setWarehouseId(parts[4].trim());
            trucks.put(vehicle.getTruckId(), vehicle);
        }
        vehicleRepository.saveAll(trucks.values());

        Map<String, Driver> drivers = new LinkedHashMap<>();
        for (String row : DemoNetwork.DRIVERS) {
            String[] parts = row.split("\\|", -1);
            Driver driver = new Driver();
            driver.setDriverId(parts[0].trim());
            driver.setName(parts[1].trim());
            driver.setStatus(parts[2].trim());
            driver.setWarehouseId(parts[3].trim());
            drivers.put(driver.getDriverId(), driver);
        }
        driverRepository.saveAll(drivers.values());

        List<Route> routes = new ArrayList<>();
        for (String row : DemoNetwork.ROUTES) {
            routes.add(parseRoute(row, trucks, drivers));
        }
        routeRepository.saveAll(routes);

        List<Order> orders = new ArrayList<>();
        for (String row : DemoNetwork.ORDERS) {
            orders.add(parseOrder(row));
        }
        orderRepository.saveAll(orders);

        balanceRoutes(trucks, drivers);
        seedLaneDurations();
        learnLanesFromRoutes();

        log.info("Demo network ready: {} warehouses, {} orders, {} routes, {} trucks, {} drivers, "
                        + "{} known lane duration(s).",
                warehouseRepository.count(), orderRepository.count(), routeRepository.count(),
                vehicleRepository.count(), driverRepository.count(), laneDurationRepository.count());
    }

    /** id|origin|destination|stops|departure|hours|capacity|truck|driver|warehouse[|status] */
    private Route parseRoute(String row, Map<String, Vehicle> trucks, Map<String, Driver> drivers) {
        String[] parts = row.split("\\|", -1);
        Route route = new Route();
        route.setRouteId(parts[0].trim());
        route.setOrigin(parts[1].trim());
        route.setDestination(parts[2].trim());
        List<String> stops = new ArrayList<>();
        for (String stop : parts[3].split(",")) {
            if (!stop.isBlank()) {
                stops.add(stop.trim());
            }
        }
        route.setStops(stops);
        route.setDepartureTime(parts[4].trim());
        route.setTravelDuration(Integer.parseInt(parts[5].trim()));
        route.setMaxCapacity(Integer.parseInt(parts[6].trim()));

        String truckId = parts[7].trim();
        if (!truckId.isEmpty()) {
            if (!trucks.containsKey(truckId)) {
                throw new BusinessException("Demo route " + route.getRouteId() + " names an unknown truck " + truckId + ".");
            }
            route.setTruckId(truckId);
        }
        String driverId = parts[8].trim();
        if (!driverId.isEmpty()) {
            if (!drivers.containsKey(driverId)) {
                throw new BusinessException("Demo route " + route.getRouteId() + " names an unknown driver " + driverId + ".");
            }
            route.setDriverId(driverId);
        }
        route.setWarehouseId(parts[9].trim());
        route.setOrderIds(new ArrayList<>());
        route.setCurrentWeight(0);
        route.setStatus(parts.length > 10 && !parts[10].isBlank()
                ? parts[10].trim() : deriveStatus(route));
        route.setEta(LinehaulUtil.calculateEta(route.getDepartureTime(), route.getTravelDuration()));
        return route;
    }

    private String deriveStatus(Route route) {
        boolean hasTruck = !LinehaulUtil.clean(route.getTruckId()).isEmpty();
        boolean hasDriver = !LinehaulUtil.clean(route.getDriverId()).isEmpty();
        if (!hasTruck && !hasDriver) {
            return Status.DRAFT;
        }
        return hasTruck && hasDriver ? Status.READY : Status.BLOCKED;
    }

    /** id|customer|origin|destination|weight|pieces|serviceDate|status|route[|warehouse] */
    private Order parseOrder(String row) {
        String[] parts = row.split("\\|", -1);
        Order order = new Order();
        order.setOrderId(parts[0].trim());
        order.setCustomer(parts[1].trim());
        order.setOrigin(parts[2].trim());
        order.setDestination(parts[3].trim());
        order.setWeight(Integer.parseInt(parts[4].trim()));
        order.setPieces(Integer.parseInt(parts[5].trim()));
        order.setServiceDate(parts[6].trim());
        order.setStatus(parts[7].trim());
        order.setRouteId(parts[8].trim().isEmpty() ? null : parts[8].trim());
        order.setWarehouseId(parts.length > 9 && !parts[9].isBlank() ? parts[9].trim() : LEGACY_WAREHOUSE);
        return order;
    }

    /**
     * Second pass over the freshly written rows: the numbers that must agree with each other (load per
     * route, order list, ETA on the order, and the route id on the truck and driver) are derived from
     * the orders themselves, never typed twice.
     */
    private void balanceRoutes(Map<String, Vehicle> trucks, Map<String, Driver> drivers) {
        for (Route route : routeRepository.findAll()) {
            List<Order> onRoute = orderRepository.findByRouteId(route.getRouteId());
            List<String> ids = new ArrayList<>();
            int weight = 0;
            for (Order order : onRoute) {
                ids.add(order.getOrderId());
                weight += order.getWeight();
            }
            route.setOrderIds(ids);
            route.setCurrentWeight(weight);
            route.setEta(LinehaulUtil.calculateEta(route.getDepartureTime(), route.getTravelDuration()));
            routeRepository.save(route);

            for (Order order : onRoute) {
                order.setEta(route.getEta());
                orderRepository.save(order);
            }

            link(route.getTruckId(), trucks, route.getRouteId());
            linkDriver(route.getDriverId(), drivers, route.getRouteId());
        }
        vehicleRepository.saveAll(trucks.values());
        driverRepository.saveAll(drivers.values());
    }

    private void link(String truckId, Map<String, Vehicle> trucks, String routeId) {
        Vehicle vehicle = truckId == null ? null : trucks.get(truckId);
        if (vehicle != null) {
            vehicle.setRouteId(routeId);
        }
    }

    private void linkDriver(String driverId, Map<String, Driver> drivers, String routeId) {
        Driver driver = driverId == null ? null : drivers.get(driverId);
        if (driver != null) {
            driver.setRouteId(routeId);
        }
    }

    /**
     * Adds the warehouse id to documents that were created before multi-warehouse support. Their
     * content is left untouched, so an existing installation keeps all of its orders and routes.
     */
    private int migrateLegacyDocuments() {
        int stamped = 0;
        for (Order order : orderRepository.findAll()) {
            if (LinehaulUtil.clean(order.getWarehouseId()).isEmpty()) {
                order.setWarehouseId(LEGACY_WAREHOUSE);
                orderRepository.save(order);
                stamped++;
            }
        }
        for (Route route : routeRepository.findAll()) {
            if (LinehaulUtil.clean(route.getWarehouseId()).isEmpty()) {
                route.setWarehouseId(LEGACY_WAREHOUSE);
                routeRepository.save(route);
                stamped++;
            }
        }
        for (Vehicle vehicle : vehicleRepository.findAll()) {
            if (LinehaulUtil.clean(vehicle.getWarehouseId()).isEmpty()) {
                vehicle.setWarehouseId(LEGACY_WAREHOUSE);
                vehicleRepository.save(vehicle);
                stamped++;
            }
        }
        for (Driver driver : driverRepository.findAll()) {
            if (LinehaulUtil.clean(driver.getWarehouseId()).isEmpty()) {
                driver.setWarehouseId(LEGACY_WAREHOUSE);
                driverRepository.save(driver);
                stamped++;
            }
        }
        return stamped;
    }
}
