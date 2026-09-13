package com.example.linehaul.service;

import com.example.linehaul.model.Driver;
import com.example.linehaul.model.Order;
import com.example.linehaul.model.Route;
import com.example.linehaul.model.Vehicle;
import com.example.linehaul.repository.DriverRepository;
import com.example.linehaul.repository.OrderRepository;
import com.example.linehaul.repository.RouteRepository;
import com.example.linehaul.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatServiceTest {

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        List<Route> routes = List.of(
                route("LH-2001", "EXPINTL", "ATLTEST", "DISPATCHED", 10000, 4000, "T-100", "D-100", "LH-3001"),
                route("LH-2002", "ATLTEST", "MEMTEST", "DRAFT", 20000, 0, null, null),
                route("LH-2003", "MEMTEST", "EXPINTL", "BLOCKED", 15000, 3000, "T-300", null, "LH-3003", "LH-3004"));

        List<Order> orders = List.of(
                order("LH-2001", "Same Id Co", "BLOCKED", 500, null),
                order("LH-3001", "Acme", "READY", 4000, "LH-2001"),
                order("LH-3002", "Globex", "READY", 1200, null),
                order("LH-3003", "Initech", "DISPATCHED", 1000, "LH-2003"),
                order("LH-3004", "Umbrella", "READY", 2000, "LH-2003"));

        List<Driver> drivers = List.of(
                driver("D-100", "Ann Lee", "ASSIGNED", "LH-2001"),
                driver("D-200", "Ben Cole", "AVAILABLE", null));

        List<Vehicle> vehicles = List.of(
                vehicle("T-100", 10000, "IN_TRANSIT", "LH-2001"),
                vehicle("T-200", 12000, "AVAILABLE", null),
                vehicle("T-300", 15000, "MAINTENANCE", "LH-2003"));

        RouteRepository routeRepository = repo(RouteRepository.class, routes, Route::getRouteId);
        OrderRepository orderRepository = repo(OrderRepository.class, orders, Order::getOrderId);
        DriverRepository driverRepository = repo(DriverRepository.class, drivers, Driver::getDriverId);
        VehicleRepository vehicleRepository = repo(VehicleRepository.class, vehicles, Vehicle::getTruckId);

        chatService = new ChatService(
                new RouteService(routeRepository, orderRepository, vehicleRepository, driverRepository,
                        new ResourceLookup(vehicleRepository, driverRepository)),
                new OrderService(orderRepository),
                new DriverService(driverRepository),
                new VehicleService(vehicleRepository));
    }

    @Test
    void countsRoutes() {
        assertEquals("There are currently 3 routes.", chatService.answer("How many routes are there?"));
    }

    @Test
    void countsAvailableDriversAndVehicles() {
        assertEquals("There is currently 1 available driver.",
                chatService.answer("How many drivers are available?"));
        assertEquals("There is currently 1 available vehicle.",
                chatService.answer("How many vehicles are available?"));
        assertEquals("There is currently 1 available vehicle.",
                chatService.answer("How many trucks are free?"));
    }

    @Test
    void countsOrdersByStatus() {
        assertEquals("There are currently 3 ready orders.",
                chatService.answer("How many READY orders are there?"));
        assertEquals("There are currently 5 orders.", chatService.answer("How many orders are there?"));
        assertEquals("There are currently 2 unassigned orders.",
                chatService.answer("How many orders are not assigned?"));
    }

    @Test
    void listsBlockedRoutes() {
        String answer = chatService.answer("Show blocked routes.");
        assertTrue(answer.startsWith("Blocked routes (2):"), answer);
        assertTrue(answer.contains("LH-2002"), answer);
        assertTrue(answer.contains("LH-2003"), answer);
    }

    @Test
    void listsRoutesWithoutADriver() {
        String answer = chatService.answer("Which routes don't have a driver?");
        assertTrue(answer.startsWith("Routes without a driver (2):"), answer);
        assertTrue(answer.contains("LH-2002"), answer);
        assertTrue(answer.contains("LH-2003"), answer);
    }

    @Test
    void findsHighestCapacityAndMostOrders() {
        assertTrue(chatService.answer("Which route has the highest capacity?")
                .startsWith("Route LH-2002 has the highest capacity: 20,000 kg"));
        assertTrue(chatService.answer("Which route has the most orders?")
                .startsWith("Route LH-2003 has the most orders: 2 orders"));
    }

    @Test
    void showsOrdersPerRoute() {
        String answer = chatService.answer("How many orders are assigned to each route?");
        assertTrue(answer.contains("LH-2001 - 1 order"), answer);
        assertTrue(answer.contains("LH-2002 - 0 orders"), answer);
        assertTrue(answer.contains("LH-2003 - 2 orders"), answer);
        assertTrue(answer.endsWith("Unassigned orders: 2"), answer);
    }

    @Test
    void answersAboutOneRoute() {
        String detail = chatService.answer("What is the status of LH-2001?");
        assertTrue(detail.startsWith("Route LH-2001 is DISPATCHED."), detail);
        assertTrue(detail.contains("• Truck: T-100"), detail);
        assertTrue(detail.contains("• Driver: D-100 (Ann Lee)"), detail);

        assertEquals("Route LH-2001 is assigned to driver D-100 (Ann Lee).",
                chatService.answer("Who is assigned to LH-2001?"));
        assertEquals("Route LH-2001 is assigned to truck T-100.",
                chatService.answer("Which truck is assigned to LH-2001?"));
        assertEquals("Route LH-2001 has 1 order assigned (4,000 kg of 10,000 kg).",
                chatService.answer("How many orders are on LH-2001?"));
        assertEquals("Route LH-2001 is carrying 4,000 kg of its 10,000 kg capacity (40% full).",
                chatService.answer("What is the capacity of LH-2001?"));
        assertTrue(chatService.answer("What is the ETA of LH-2001?")
                .startsWith("Route LH-2001 departs at 20:00 and the ETA is "));
    }

    @Test
    void answersAboutOrdersDriversAndVehicles() {
        String order = chatService.answer("Show me order LH-2001");
        assertTrue(order.startsWith("Order LH-2001 is BLOCKED."), order);
        assertTrue(order.contains("• Customer: Same Id Co"), order);
        assertTrue(order.contains("• Route: not assigned"), order);

        assertTrue(chatService.answer("Tell me about LH-3004").startsWith("Order LH-3004 is READY."));
        assertEquals("Driver D-200 (Ben Cole) is AVAILABLE and is not assigned to a route.",
                chatService.answer("Tell me about D-200"));
        assertEquals("Truck T-300 is a Truck with a capacity of 15,000 kg. It is MAINTENANCE on route LH-2003.",
                chatService.answer("What about T-300?"));
    }

    @Test
    void explainsUnknownId() {
        assertTrue(chatService.answer("What is the status of LH-9999?")
                .startsWith("I could not find LH-9999 in the current Linehaul data."));
    }

   
    @Test
    void fallsBackToHelp() {
        assertTrue(chatService.answer("what is the weather in memphis")
                .startsWith("I can help you with information about orders, routes, drivers and vehicles."));
        assertTrue(chatService.answer("").startsWith("I can help you with information"));
        assertTrue(chatService.answer(null).startsWith("I can help you with information"));
        assertTrue(chatService.answer("help").startsWith("I can help you with information"));
    }

    @Test
    void greets() {
        assertTrue(chatService.answer("hello").startsWith("Hello! I can look up the current Linehaul data"));
    }

    @Test
    void summarises() {
        String answer = chatService.answer("give me an overview");
        assertTrue(answer.startsWith("Current Linehaul data:"), answer);
        assertTrue(answer.contains("• Routes: 3 (0 ready, 2 blocked, 1 active)"), answer);
        assertTrue(answer.contains("• Orders: 5 (3 assigned, 2 unassigned)"), answer);
        assertTrue(answer.contains("• Drivers: 2 (1 available)"), answer);
        assertTrue(answer.contains("• Vehicles: 3 (1 available)"), answer);
    }

    @Test
    void neverWritesToTheDatabase() {
        List<String> questions = List.of(
                "How many routes are there?", "Show blocked routes.", "How many drivers are available?",
                "How many vehicles are available?", "How many READY orders are there?",
                "What is the status of LH-2001?", "Who is assigned to LH-2001?",
                "Which routes don't have a truck?", "Which route has the most orders?",
                "How many orders are assigned to each route?", "give me an overview",
                "delete all orders", "dispatch LH-2001", "update driver D-100");

        for (String question : questions) {
            String answer = chatService.answer(question);
            assertTrue(answer != null && !answer.isBlank(), question);
        }
        assertTrue(writeAttempts.isEmpty(), "chat tried to write: " + writeAttempts);
    }

    private final List<String> writeAttempts = new ArrayList<>();

    private <R, T> R repo(Class<R> type, List<T> rows, Function<T, String> idOf) {
        Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (target, method, args) -> {
                    String name = method.getName();
                    if (name.startsWith("save") || name.startsWith("insert")
                            || name.startsWith("delete") || name.startsWith("remove")) {
                        writeAttempts.add(name);
                        throw new AssertionError("The chat must not write to MongoDB, but called " + name);
                    }
                    if (method.getReturnType() == Optional.class) {
                        return rows.stream()
                                .filter(row -> idOf.apply(row).equalsIgnoreCase((String) args[0]))
                                .findFirst();
                    }
                    if (method.getReturnType() == List.class) {
                        return new ArrayList<>(rows);
                    }
                    if (name.equals("toString")) {
                        return type.getSimpleName();
                    }
                    throw new UnsupportedOperationException(name);
                });
        return type.cast(proxy);
    }

    private static Route route(String routeId, String origin, String destination, String status,
                               int maxCapacity, int currentWeight, String truckId, String driverId,
                               String... orderIds) {
        Route route = new Route();
        route.setRouteId(routeId);
        route.setOrigin(origin);
        route.setDestination(destination);
        route.setDepartureTime("20:00");
        route.setTravelDuration(9);
        route.setStatus(status);
        route.setMaxCapacity(maxCapacity);
        route.setCurrentWeight(currentWeight);
        route.setTruckId(truckId);
        route.setDriverId(driverId);
        route.setOrderIds(new ArrayList<>(List.of(orderIds)));
        return route;
    }

    private static Order order(String orderId, String customer, String status, int weight, String routeId) {
        Order order = new Order();
        order.setOrderId(orderId);
        order.setCustomer(customer);
        order.setOrigin("EXPINTL");
        order.setDestination("ATLTEST");
        order.setWeight(weight);
        order.setPieces(2);
        order.setServiceDate("2026-01-15");
        order.setStatus(status);
        order.setRouteId(routeId);
        return order;
    }

    private static Driver driver(String driverId, String name, String status, String routeId) {
        Driver driver = new Driver();
        driver.setDriverId(driverId);
        driver.setName(name);
        driver.setStatus(status);
        driver.setRouteId(routeId);
        return driver;
    }

    private static Vehicle vehicle(String truckId, int capacity, String status, String routeId) {
        Vehicle vehicle = new Vehicle();
        vehicle.setTruckId(truckId);
        vehicle.setType("Truck");
        vehicle.setCapacity(capacity);
        vehicle.setStatus(status);
        vehicle.setRouteId(routeId);
        return vehicle;
    }
}
