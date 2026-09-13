package com.example.linehaul.service;

import com.example.linehaul.model.Driver;
import com.example.linehaul.model.Order;
import com.example.linehaul.model.Route;
import com.example.linehaul.model.Status;
import com.example.linehaul.model.Vehicle;
import com.example.linehaul.util.ParsedQuestion;
import com.example.linehaul.util.ParsedQuestion.Action;
import com.example.linehaul.util.ParsedQuestion.Focus;
import com.example.linehaul.util.QuestionParser;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

@Service
public class ChatService {

    private static final int MAX_LIST = 15;

    private static final String HELP = """
            I can help you with information about orders, routes, drivers and vehicles.

            Try asking:
            • How many routes are there?
            • Show available drivers.
            • How many vehicles are available?
            • How many READY orders are there?
            • What is the status of LH-1029?""";

    private static final String GREETING = """
            Hello! I can look up the current Linehaul data for you.

            Try asking:
            • How many routes are there?
            • How many trucks are free?
            • Show blocked routes.""";

    private final RouteService routeService;
    private final OrderService orderService;
    private final DriverService driverService;
    private final VehicleService vehicleService;

    public ChatService(RouteService routeService,
                       OrderService orderService,
                       DriverService driverService,
                       VehicleService vehicleService) {
        this.routeService = routeService;
        this.orderService = orderService;
        this.driverService = driverService;
        this.vehicleService = vehicleService;
    }

    public String answer(String question) {
        ParsedQuestion parsed = QuestionParser.parse(question);

        if (parsed.text().isEmpty()) {
            return HELP;
        }
        if (parsed.focus() == Focus.GREETING) {
            return GREETING;
        }
        if (parsed.focus() == Focus.HELP) {
            return HELP;
        }
        if (parsed.focus() == Focus.SUMMARY) {
            return summary();
        }

        if (parsed.id() != null) {
            String found = answerAboutId(parsed);
            if (found != null) {
                return found;
            }
        }
        String group = answerAboutGroup(parsed);
        if (group != null) {
            return group;
        }
        if (parsed.id() != null) {
            return "I could not find " + parsed.id() + " in the current Linehaul data. "
                    + "Please check the ID and try again.";
        }
        return HELP;
    }

     private String answerAboutId(ParsedQuestion parsed) {
        String id = parsed.id();
        Route route = findRoute(id);
        Order order = findOrder(id);

        if (order != null && (route == null || asksForOrder(parsed))) {
            return orderDetail(order);
        }
        if (route != null) {
            return routeAnswer(route, parsed);
        }
        Driver driver = findDriver(id);
        if (driver != null) {
            return driverDetail(driver);
        }
        Vehicle vehicle = findVehicle(id);
        if (vehicle != null) {
            return vehicleDetail(vehicle);
        }
        return null;
    }

    private boolean asksForOrder(ParsedQuestion parsed) {
        String id = parsed.id().toLowerCase();
        String plain = id.replace("-", "");
        for (String word : new String[]{"order ", "orders ", "shipment ", "shipments ", "order id "}) {
            if (parsed.text().contains(word + id) || parsed.text().contains(word + plain)) {
                return true;
            }
        }
        return false;
    }

    private String routeAnswer(Route route, ParsedQuestion parsed) {
        return switch (parsed.focus()) {
            case ETA -> routeEta(route);
            case CAPACITY -> routeCapacity(route);
            case DRIVER -> routeDriver(route);
            case TRUCK -> routeTruck(route);
            case ORDERS -> routeOrders(route, parsed.action());
            default -> routeDetail(route);
        };
    }

    private String routeEta(Route route) {
        if (isBlank(route.getEta())) {
            return "Route " + route.getRouteId() + " does not have an ETA yet.";
        }
        return "Route " + route.getRouteId() + " departs at " + route.getDepartureTime()
                + " and the ETA is " + route.getEta() + ".";
    }

    private String routeCapacity(Route route) {
        return "Route " + route.getRouteId() + " is carrying " + kg(route.getCurrentWeight())
                + " of its " + kg(route.getMaxCapacity()) + " capacity ("
                + route.getCapacityPercent() + "% full).";
    }

    private String routeDriver(Route route) {
        if (!route.isHasDriver()) {
            return "Route " + route.getRouteId() + " does not have a driver assigned yet.";
        }
        return "Route " + route.getRouteId() + " is assigned to driver " + driverName(route.getDriverId()) + ".";
    }

    private String routeTruck(Route route) {
        if (!route.isHasTruck()) {
            return "Route " + route.getRouteId() + " does not have a truck assigned yet.";
        }
        return "Route " + route.getRouteId() + " is assigned to truck " + route.getTruckId() + ".";
    }

    private String routeOrders(Route route, Action action) {
        List<Order> orders = routeService.findOrders(route.getRouteId());
        if (action != Action.LIST) {
            return "Route " + route.getRouteId() + " has " + orders.size()
                    + (orders.size() == 1 ? " order" : " orders") + " assigned ("
                    + kg(route.getCurrentWeight()) + " of " + kg(route.getMaxCapacity()) + ").";
        }
        return listBlock("Orders on route " + route.getRouteId(), texts(orders, this::orderLine),
                "Route " + route.getRouteId() + " does not have any orders yet.");
    }

    private String routeDetail(Route route) {
        StringBuilder text = new StringBuilder();
        text.append("Route ").append(route.getRouteId()).append(" is ").append(up(route.getStatus())).append(".");
        text.append("\n• Lane: ").append(route.getOrigin()).append(" to ").append(route.getDestination());
        text.append("\n• Orders: ").append(route.getOrderIds().size());
        text.append("\n• Truck: ").append(route.isHasTruck() ? route.getTruckId() : "not assigned");
        text.append("\n• Driver: ").append(route.isHasDriver() ? driverName(route.getDriverId()) : "not assigned");
        text.append("\n• Capacity: ").append(kg(route.getCurrentWeight())).append(" of ")
                .append(kg(route.getMaxCapacity())).append(" (").append(route.getCapacityPercent()).append("% full)");
        text.append("\n• Departure: ").append(route.getDepartureTime());
        if (!isBlank(route.getEta())) {
            text.append("\n• ETA: ").append(route.getEta());
        }
        if (Status.READY.equals(up(route.getReadiness()))) {
            text.append("\n• Ready to dispatch.");
        } else if (Status.BLOCKED.equals(up(route.getReadiness()))) {
            text.append("\n• Not ready: ").append(route.getReadinessReason());
        } else if (!isBlank(route.getReadinessReason())) {
            text.append("\n• ").append(route.getReadinessReason());
        }
        return text.toString();
    }

    private String orderDetail(Order order) {
        StringBuilder text = new StringBuilder();
        text.append("Order ").append(order.getOrderId()).append(" is ").append(up(order.getStatus())).append(".");
        text.append("\n• Customer: ").append(order.getCustomer());
        text.append("\n• Lane: ").append(order.getOrigin()).append(" to ").append(order.getDestination());
        text.append("\n• Weight: ").append(kg(order.getWeight())).append(" in ").append(order.getPieces())
                .append(order.getPieces() == 1 ? " piece" : " pieces");
        if (!isBlank(order.getServiceDate())) {
            text.append("\n• Service date: ").append(order.getServiceDate());
        }
        text.append("\n• Route: ").append(isBlank(order.getRouteId()) ? "not assigned" : order.getRouteId());
        if (!isBlank(order.getEta())) {
            text.append("\n• ETA: ").append(order.getEta());
        }
        return text.toString();
    }

    private String driverDetail(Driver driver) {
        String where = isBlank(driver.getRouteId())
                ? " and is not assigned to a route."
                : " on route " + driver.getRouteId() + ".";
        return "Driver " + driver.getDriverId() + " (" + driver.getName() + ") is "
                + up(driver.getStatus()) + where;
    }

    private String vehicleDetail(Vehicle vehicle) {
        String where = isBlank(vehicle.getRouteId())
                ? " and is not assigned to a route."
                : " on route " + vehicle.getRouteId() + ".";
        return "Truck " + vehicle.getTruckId() + " is a " + vehicle.getType() + " with a capacity of "
                + kg(vehicle.getCapacity()) + ". It is " + up(vehicle.getStatus()) + where;
    }

    private String answerAboutGroup(ParsedQuestion parsed) {
        return switch (parsed.focus()) {
            case HIGHEST_CAPACITY -> highestCapacityRoute();
            case MOST_ORDERS -> mostOrdersRoute();
            case NO_DRIVER -> routesWithout("a driver", route -> !route.isHasDriver());
            case NO_TRUCK -> routesWithout("a truck", route -> !route.isHasTruck());
            case NO_ORDERS -> routesWithout("any orders", route -> !route.isHasOrders());
            case ORDERS_PER_ROUTE -> ordersPerRoute();
            default -> entityGroup(parsed);
        };
    }

    private String entityGroup(ParsedQuestion parsed) {
        if (parsed.entity() == null) {
            return null;
        }
        String status = parsed.status();
        boolean count = parsed.action() == Action.COUNT;
        return switch (parsed.entity()) {
            case ROUTE -> group(keep(routes(), route -> routeMatches(route, status)),
                    count, status, "route", this::routeLine);
            case ORDER -> group(keep(orders(), order -> orderMatches(order, status)),
                    count, status, "order", this::orderLine);
            case DRIVER -> group(keep(drivers(), driver -> driverMatches(driver, status)),
                    count, status, "driver", this::driverLine);
            case VEHICLE -> group(keep(vehicles(), vehicle -> vehicleMatches(vehicle, status)),
                    count, status, "vehicle", this::vehicleLine);
        };
    }

    private <T> String group(List<T> found, boolean count, String status, String noun, Function<T, String> line) {
        String label = label(status, noun);
        if (count) {
            return countSentence(found.size(), label);
        }
        return listBlock(capitalize(label + "s"), texts(found, line),
                "There are no " + label + "s in the current Linehaul data.");
    }

    private String highestCapacityRoute() {
        Route best = null;
        for (Route route : routes()) {
            if (best == null || route.getMaxCapacity() > best.getMaxCapacity()) {
                best = route;
            }
        }
        if (best == null) {
            return "There are no routes in the current Linehaul data.";
        }
        return "Route " + best.getRouteId() + " has the highest capacity: " + kg(best.getMaxCapacity())
                + " (" + kg(best.getCurrentWeight()) + " used, " + best.getCapacityPercent() + "% full).";
    }

    private String mostOrdersRoute() {
        Route best = null;
        for (Route route : routes()) {
            if (best == null || route.getOrderIds().size() > best.getOrderIds().size()) {
                best = route;
            }
        }
        if (best == null) {
            return "There are no routes in the current Linehaul data.";
        }
        if (best.getOrderIds().isEmpty()) {
            return "No route has any orders assigned yet.";
        }
        return "Route " + best.getRouteId() + " has the most orders: " + best.getOrderIds().size()
                + (best.getOrderIds().size() == 1 ? " order (" : " orders (") + kg(best.getCurrentWeight()) + ").";
    }

    private String routesWithout(String what, Predicate<Route> missing) {
        List<Route> found = keep(routes(), missing);
        return listBlock("Routes without " + what, texts(found, this::routeLine),
                "There are no routes without " + what + ".");
    }

    private String ordersPerRoute() {
        List<Route> routes = routes();
        if (routes.isEmpty()) {
            return "There are no routes in the current Linehaul data.";
        }
        List<String> lines = texts(routes, route -> route.getRouteId() + " - " + route.getOrderIds().size()
                + (route.getOrderIds().size() == 1 ? " order" : " orders"));
        long unassigned = orders().stream().filter(order -> isBlank(order.getRouteId())).count();
        return listBlock("Orders assigned to each route", lines, "")
                + "\nUnassigned orders: " + unassigned;
    }

    private String summary() {
        List<Route> routes = routes();
        List<Order> orders = orders();
        return "Current Linehaul data:"
                + "\n• Routes: " + routes.size()
                + " (" + count(routes, route -> Status.READY.equals(up(route.getReadiness()))) + " ready, "
                + count(routes, route -> Status.BLOCKED.equals(up(route.getReadiness()))) + " blocked, "
                + count(routes, route -> routeMatches(route, "ACTIVE")) + " active)"
                + "\n• Orders: " + orders.size()
                + " (" + count(orders, order -> !isBlank(order.getRouteId())) + " assigned, "
                + count(orders, order -> isBlank(order.getRouteId())) + " unassigned)"
                + "\n• Drivers: " + drivers().size()
                + " (" + count(drivers(), driver -> driverMatches(driver, Status.AVAILABLE)) + " available)"
                + "\n• Vehicles: " + vehicles().size()
                + " (" + count(vehicles(), vehicle -> vehicleMatches(vehicle, Status.AVAILABLE)) + " available)";
    }

   
    private boolean routeMatches(Route route, String status) {
        if (status == null) {
            return true;
        }
        return switch (status) {
            case "ACTIVE" -> Status.DISPATCHED.equals(up(route.getStatus()))
                    || Status.IN_TRANSIT.equals(up(route.getStatus()));
            case "UNASSIGNED" -> !route.isHasTruck() || !route.isHasDriver();
            case "ASSIGNED" -> route.isHasTruck() && route.isHasDriver();
            case Status.READY, Status.BLOCKED -> status.equals(up(route.getStatus()))
                    || status.equals(up(route.getReadiness()));
            default -> status.equals(up(route.getStatus()));
        };
    }

    private boolean orderMatches(Order order, String status) {
        if (status == null) {
            return true;
        }
        return switch (status) {
            case "UNASSIGNED" -> isBlank(order.getRouteId());
            case Status.ASSIGNED -> !isBlank(order.getRouteId()) || Status.ASSIGNED.equals(up(order.getStatus()));
            case "ACTIVE" -> Status.DISPATCHED.equals(up(order.getStatus()))
                    || Status.IN_TRANSIT.equals(up(order.getStatus()));
            default -> status.equals(up(order.getStatus()));
        };
    }

    private boolean driverMatches(Driver driver, String status) {
        if (status == null) {
            return true;
        }
        return switch (status) {
            case "UNASSIGNED" -> isBlank(driver.getRouteId());
            case Status.ASSIGNED -> !isBlank(driver.getRouteId()) || Status.ASSIGNED.equals(up(driver.getStatus()));
            case "ACTIVE" -> Status.IN_TRANSIT.equals(up(driver.getStatus()))
                    || Status.DISPATCHED.equals(up(driver.getStatus()));
            default -> status.equals(up(driver.getStatus()));
        };
    }

    private boolean vehicleMatches(Vehicle vehicle, String status) {
        if (status == null) {
            return true;
        }
        return switch (status) {
            case "UNASSIGNED" -> isBlank(vehicle.getRouteId());
            case Status.ASSIGNED -> !isBlank(vehicle.getRouteId()) || Status.ASSIGNED.equals(up(vehicle.getStatus()));
            case "ACTIVE" -> Status.IN_TRANSIT.equals(up(vehicle.getStatus()))
                    || Status.DISPATCHED.equals(up(vehicle.getStatus()));
            default -> status.equals(up(vehicle.getStatus()));
        };
    }

    private List<Route> routes() {
        return routeService.findAll(null);
    }

    private List<Order> orders() {
        return orderService.findAll(null, null);
    }

    private List<Driver> drivers() {
        return driverService.findAll();
    }

    private List<Vehicle> vehicles() {
        return vehicleService.findAll();
    }

    private Route findRoute(String id) {
        return first(routes(), route -> id.equalsIgnoreCase(route.getRouteId()));
    }

    private Order findOrder(String id) {
        return first(orders(), order -> id.equalsIgnoreCase(order.getOrderId()));
    }

    private Driver findDriver(String id) {
        return first(drivers(), driver -> id.equalsIgnoreCase(driver.getDriverId()));
    }

    private Vehicle findVehicle(String id) {
        return first(vehicles(), vehicle -> id.equalsIgnoreCase(vehicle.getTruckId()));
    }

    private String driverName(String driverId) {
        Driver driver = findDriver(driverId);
        return driver == null ? driverId : driverId + " (" + driver.getName() + ")";
    }

    private String routeLine(Route route) {
        String line = route.getRouteId() + " - " + route.getOrigin() + " to " + route.getDestination()
                + " (" + up(route.getStatus()) + ", " + route.getOrderIds().size() + " orders, "
                + route.getCapacityPercent() + "% full)";
        if (Status.BLOCKED.equals(up(route.getReadiness())) && !isBlank(route.getReadinessReason())) {
            return line + " - " + route.getReadinessReason();
        }
        return line;
    }

    private String orderLine(Order order) {
        return order.getOrderId() + " - " + order.getCustomer() + ", " + kg(order.getWeight())
                + " (" + up(order.getStatus())
                + (isBlank(order.getRouteId()) ? ", no route" : ", route " + order.getRouteId()) + ")";
    }

    private String driverLine(Driver driver) {
        return driver.getDriverId() + " - " + driver.getName() + " (" + up(driver.getStatus())
                + (isBlank(driver.getRouteId()) ? "" : ", route " + driver.getRouteId()) + ")";
    }

    private String vehicleLine(Vehicle vehicle) {
        return vehicle.getTruckId() + " - " + vehicle.getType() + ", " + kg(vehicle.getCapacity())
                + " (" + up(vehicle.getStatus())
                + (isBlank(vehicle.getRouteId()) ? "" : ", route " + vehicle.getRouteId()) + ")";
    }

    private static <T> List<T> keep(List<T> items, Predicate<T> wanted) {
        return items.stream().filter(wanted).toList();
    }

    private static <T> List<String> texts(List<T> items, Function<T, String> line) {
        return items.stream().map(line).toList();
    }

    private static <T> long count(List<T> items, Predicate<T> wanted) {
        return items.stream().filter(wanted).count();
    }

    private static <T> T first(List<T> items, Predicate<T> wanted) {
        return items.stream().filter(wanted).findFirst().orElse(null);
    }

    private static String countSentence(int found, String noun) {
        return "There " + (found == 1 ? "is" : "are") + " currently " + found + " "
                + (found == 1 ? noun : noun + "s") + ".";
    }

    private static String listBlock(String header, List<String> lines, String emptyText) {
        if (lines.isEmpty()) {
            return emptyText;
        }
        StringBuilder text = new StringBuilder(header + " (" + lines.size() + "):");
        int shown = Math.min(lines.size(), MAX_LIST);
        for (int i = 0; i < shown; i++) {
            text.append("\n• ").append(lines.get(i));
        }
        if (lines.size() > shown) {
            text.append("\n... and ").append(lines.size() - shown).append(" more.");
        }
        return text.toString();
    }

    private static String label(String status, String noun) {
        if (status == null) {
            return noun;
        }
        return status.replace('_', ' ').toLowerCase() + " " + noun;
    }

    private static String capitalize(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String kg(int value) {
        return String.format(Locale.ROOT, "%,d kg", value);
    }

    private static String up(String value) {
        return value == null ? "" : value.toUpperCase();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
