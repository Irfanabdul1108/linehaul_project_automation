package com.example.linehaul.automation;

import com.example.linehaul.automation.ai.AiAdvisor;
import com.example.linehaul.dto.AssignRequest;
import com.example.linehaul.dto.AssignmentAdvice;
import com.example.linehaul.dto.AssignmentResult;
import com.example.linehaul.dto.BatchAssignmentReport;
import com.example.linehaul.dto.CreateAndAssignRequest;
import com.example.linehaul.exception.BusinessException;
import com.example.linehaul.model.Driver;
import com.example.linehaul.model.Order;
import com.example.linehaul.model.Route;
import com.example.linehaul.model.Vehicle;
import com.example.linehaul.model.Warehouse;
import com.example.linehaul.service.DriverService;
import com.example.linehaul.service.OrderService;
import com.example.linehaul.service.RouteService;
import com.example.linehaul.service.VehicleService;
import com.example.linehaul.service.WarehouseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The promise that makes the AI layer safe to have in the loop: a recommendation is only a suggestion,
 * and nothing is written unless the deterministic rules still hold on freshly loaded rows. The writes
 * themselves are the existing {@link RouteService} methods, which this test verifies are used.
 */
class AssignmentServiceTest {

    private OrderService orderService;
    private RouteService routeService;
    private DriverService driverService;
    private VehicleService vehicleService;
    private WarehouseService warehouseService;
    private AiAdvisor ai;

    private AssignmentService service;
    private Warehouse depotA;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        routeService = mock(RouteService.class);
        driverService = mock(DriverService.class);
        vehicleService = mock(VehicleService.class);
        warehouseService = mock(WarehouseService.class);
        ai = mock(AiAdvisor.class);

        depotA = new Warehouse("WH-A", "Bengaluru", "WH-A", "Bengaluru", "Bengaluru", "Karnataka", "");

        // the real editability rule, so the test cannot accidentally rely on a Mockito default
        when(routeService.isEditable(any(Route.class))).thenAnswer(call -> {
            String status = ((Route) call.getArgument(0)).getStatus();
            return !"DISPATCHED".equals(status) && !"IN_TRANSIT".equals(status) && !"COMPLETED".equals(status);
        });
        when(vehicleService.findAll()).thenReturn(List.of(truck("T-101", 10000, "ASSIGNED", "WH-A")));
        when(warehouseService.find("WH-A")).thenReturn(Optional.of(depotA));
        when(warehouseService.findAll()).thenReturn(List.of(depotA));
        when(ai.isEnabled()).thenReturn(false);

        service = new AssignmentService(orderService, routeService, driverService, vehicleService,
                warehouseService, new RouteMatchingService(routeService), new RouteScoringService(), ai);
    }

    @Test
    void aValidAssignmentGoesThroughTheExistingRouteService() {
        Order order = order("LH-5001", "Bengaluru", "Hyderabad", 1500, null);
        Route route = route("LH-1029", "WH-A", "Bengaluru", "Hyderabad", List.of("Vijayawada"),
                "20:00", 9, 10000, 6200, "T-101", "D-103", "READY");
        when(orderService.findByOrderId("LH-5001")).thenReturn(order);
        when(routeService.findByRouteId("LH-1029")).thenReturn(route);
        when(routeService.assignOrder("LH-1029", "LH-5001")).thenReturn(
                route("LH-1029", "WH-A", "Bengaluru", "Hyderabad", List.of("Vijayawada"),
                        "20:00", 9, 10000, 7700, "T-101", "D-103", "READY"));

        AssignmentResult result = service.assign("LH-5001", request("LH-1029", null, null));

        verify(routeService).assignOrder("LH-1029", "LH-5001");
        assertEquals("LH-1029", result.getRouteId());
        assertTrue(result.getMessage().contains("assigned to route LH-1029"), result.getMessage());
        assertTrue(result.getMessage().contains("7,700 of 10,000 kg"), result.getMessage());
    }

    @Test
    void aRouteThatWouldBeOverloadedIsRefusedBeforeAnythingIsWritten() {
        Order order = order("LH-5002", "Bengaluru", "Chennai", 900, null);
        Route full = route("LH-1030", "WH-A", "Bengaluru", "Chennai", List.of(),
                "21:30", 8, 8000, 7500, "T-102", "D-101", "READY");
        when(orderService.findByOrderId("LH-5002")).thenReturn(order);
        when(routeService.findByRouteId("LH-1030")).thenReturn(full);

        BusinessException problem = assertThrows(BusinessException.class,
                () -> service.assign("LH-5002", request("LH-1030", null, null)));

        assertTrue(problem.getMessage().contains("Assignment refused"), problem.getMessage());
        assertTrue(problem.getMessage().contains("500 kg is free"), problem.getMessage());
        verify(routeService, never()).assignOrder(anyString(), anyString());
    }

    @Test
    void aRouteThatAlreadyLeftIsRefused() {
        Order order = order("LH-5001", "Bengaluru", "Hyderabad", 500, null);
        Route gone = route("LH-1035", "WH-A", "Bengaluru", "Mumbai", List.of(),
                "05:00", 16, 7000, 3000, "T-106", "D-106", "IN_TRANSIT");
        when(orderService.findByOrderId("LH-5001")).thenReturn(order);
        when(routeService.findByRouteId("LH-1035")).thenReturn(gone);

        BusinessException problem = assertThrows(BusinessException.class,
                () -> service.assign("LH-5001", request("LH-1035", null, null)));

        assertTrue(problem.getMessage().contains("in transit"), problem.getMessage());
        verify(routeService, never()).assignOrder(anyString(), anyString());
    }

    @Test
    void anOrderThatAlreadyHasARouteIsNeverMovedSilently() {
        Order order = order("LH-5001", "Bengaluru", "Hyderabad", 1500, "LH-1029");
        when(orderService.findByOrderId("LH-5001")).thenReturn(order);

        BusinessException problem = assertThrows(BusinessException.class,
                () -> service.assign("LH-5001", request("LH-1033", null, null)));

        assertTrue(problem.getMessage().contains("already assigned to route LH-1029"), problem.getMessage());
    }

    @Test
    void theCrewOfACrossWarehouseRouteIsLeftAlone() {
        Order order = order("LH-5001", "Bengaluru", "Hyderabad", 1500, null);
        Route foreign = route("LH-2022", "WH-B", "Chennai", "Hyderabad",
                List.of("Bengaluru", "Vijayawada"), "18:00", 14, 12000, 5000, "T-204", "D-203", "READY");
        when(orderService.findByOrderId("LH-5001")).thenReturn(order);
        when(routeService.findByRouteId("LH-2022")).thenReturn(foreign);

        BusinessException problem = assertThrows(BusinessException.class,
                () -> service.assign("LH-5001", request("LH-2022", "D-104", "T-103")));

        assertTrue(problem.getMessage().contains("already assigned its driver and truck"), problem.getMessage());
        verify(routeService, never()).assignDriver(anyString(), anyString());
        verify(routeService, never()).assignOrder(anyString(), anyString());
    }

    @Test
    void aNewRouteIsCreatedWithTheChosenCrewAndThenLoaded() {
        Order order = order("LH-5002", "Bengaluru", "Chennai", 900, null);
        when(orderService.findByOrderId("LH-5002")).thenReturn(order);
        when(driverService.findByDriverId("D-104")).thenReturn(driver("D-104", "Sameer Khan", "AVAILABLE", "WH-A"));
        when(vehicleService.findByTruckId("T-103")).thenReturn(truck("T-103", 4000, "AVAILABLE", "WH-A"));
        when(routeService.create(any(Route.class))).thenAnswer(call -> {
            Route created = call.getArgument(0);
            created.setRouteId("LH-4001");
            created.setStopSequence(new ArrayList<>(List.of(created.getOrigin(), created.getDestination())));
            return created;
        });
        when(routeService.assignTruck(eq("LH-4001"), anyString())).thenAnswer(call -> createdRoute());
        when(routeService.assignDriver(eq("LH-4001"), anyString())).thenAnswer(call -> createdRoute());
        when(routeService.assignOrder("LH-4001", "LH-5002")).thenAnswer(call -> createdRoute());

        CreateAndAssignRequest request = new CreateAndAssignRequest();
        request.setOrderId("LH-5002");
        request.setWarehouseId("WH-A");
        request.setDriverId("D-104");
        request.setTruckId("T-103");

        AssignmentResult result = service.createAndAssign(request);

        verify(routeService).create(any(Route.class));
        verify(routeService).assignTruck("LH-4001", "T-103");
        verify(routeService).assignDriver("LH-4001", "D-104");
        verify(routeService).assignOrder("LH-4001", "LH-5002");
        assertTrue(result.isNewRouteCreated());
        assertEquals("New route LH-4001 created and LH-5002 assigned successfully.", result.getMessage());
    }

    @Test
    void aDriverFromAnotherWarehouseCannotBeBorrowed() {
        Order order = order("LH-5002", "Bengaluru", "Chennai", 900, null);
        when(orderService.findByOrderId("LH-5002")).thenReturn(order);
        when(driverService.findByDriverId("D-202"))
                .thenReturn(driver("D-202", "Lakshmi Menon", "AVAILABLE", "WH-B"));
        when(vehicleService.findByTruckId("T-103")).thenReturn(truck("T-103", 4000, "AVAILABLE", "WH-A"));

        CreateAndAssignRequest request = new CreateAndAssignRequest();
        request.setOrderId("LH-5002");
        request.setWarehouseId("WH-A");
        request.setDriverId("D-202");
        request.setTruckId("T-103");

        BusinessException problem = assertThrows(BusinessException.class, () -> service.createAndAssign(request));

        assertTrue(problem.getMessage().contains("belongs to WH-B"), problem.getMessage());
        verify(routeService, never()).create(any(Route.class));
    }

    @Test
    void aTooSmallTruckIsRefusedEvenThoughItIsFree() {
        Order order = order("LH-5005", "Bengaluru", "Kolkata", 5000, null);
        when(orderService.findByOrderId("LH-5005")).thenReturn(order);
        when(driverService.findByDriverId("D-104")).thenReturn(driver("D-104", "Sameer Khan", "AVAILABLE", "WH-A"));
        when(vehicleService.findByTruckId("T-103")).thenReturn(truck("T-103", 4000, "AVAILABLE", "WH-A"));

        CreateAndAssignRequest request = new CreateAndAssignRequest();
        request.setOrderId("LH-5005");
        request.setWarehouseId("WH-A");
        request.setDriverId("D-104");
        request.setTruckId("T-103");
        request.setMaxCapacity(6000);

        BusinessException problem = assertThrows(BusinessException.class, () -> service.createAndAssign(request));

        assertTrue(problem.getMessage().contains("carries 4,000 kg"), problem.getMessage());
        verify(routeService, never()).create(any(Route.class));
    }

    @Test
    void noRouteLeftLeadsToANewRouteProposalInsteadOfAForceFit() {
        Order order = order("LH-5002", "Bengaluru", "Chennai", 900, null);
        Route full = route("LH-1030", "WH-A", "Bengaluru", "Chennai", List.of(),
                "21:30", 8, 8000, 7500, "T-102", "D-101", "READY");
        when(orderService.findByOrderId("LH-5002")).thenReturn(order);
        when(routeService.findAll(null)).thenReturn(List.of(full));
        when(routeService.nextRouteId()).thenReturn("LH-4001");
        when(driverService.findAvailable("WH-A"))
                .thenReturn(List.of(driver("D-104", "Sameer Khan", "AVAILABLE", "WH-A")));
        when(vehicleService.findAvailable(900, "WH-A")).thenReturn(List.of(truck("T-103", 4000, "AVAILABLE", "WH-A")));

        AssignmentAdvice advice = service.advise("LH-5002", "WH-A", true);

        assertTrue(advice.getCandidates().isEmpty());
        assertTrue(advice.isNewRouteRequired());
        assertNotNull(advice.getNewRoute());
        assertTrue(advice.getNewRoute().isCanCreate());
        assertEquals("LH-4001", advice.getNewRoute().getSuggestedRouteId());
        assertEquals(1, advice.getNewRoute().getDrivers().size());
        assertEquals(1, advice.getNewRoute().getVehicles().size());
        assertEquals("Bengaluru", advice.getNewRoute().getOrigin());
        assertEquals("Chennai", advice.getNewRoute().getDestination());
        assertTrue(advice.getRejected().get(0).getReason().contains("Not enough room"),
                advice.getRejected().get(0).getReason());
    }

    @Test
    void withoutACrewANewRouteCannotBeProposedEither() {
        Order order = order("LH-5006", "Bengaluru", "Hyderabad", 9500, null);
        when(orderService.findByOrderId("LH-5006")).thenReturn(order);
        when(routeService.findAll(null)).thenReturn(List.of(route("LH-1029", "WH-A", "Bengaluru",
                "Hyderabad", List.of("Vijayawada"), "20:00", 9, 10000, 9200, "T-101", "D-103", "READY")));
        when(routeService.nextRouteId()).thenReturn("LH-4002");
        when(driverService.findAvailable("WH-A")).thenReturn(List.of());
        when(vehicleService.findAvailable(9500, "WH-A")).thenReturn(List.of());

        AssignmentAdvice advice = service.advise("LH-5006", "WH-A", true);

        assertFalse(advice.getNewRoute().isCanCreate());
        assertTrue(advice.getMessage().contains("No suitable driver/vehicle is currently available in this warehouse."),
                advice.getMessage());
    }

    @Test
    void theModelIsNeverAskedWhenOnlyOneRouteQualifies() {
        Order order = order("LH-5001", "Bengaluru", "Hyderabad", 1500, null);
        when(orderService.findByOrderId("LH-5001")).thenReturn(order);
        when(routeService.findAll(null)).thenReturn(List.of(route("LH-1029", "WH-A", "Bengaluru",
                "Hyderabad", List.of("Vijayawada"), "20:00", 9, 10000, 6200, "T-101", "D-103", "READY")));

        AssignmentAdvice advice = service.advise("LH-5001", "WH-A", true);

        assertEquals(1, advice.getCandidates().size());
        assertEquals(1, advice.getCandidates().get(0).getRank());
        assertEquals("disabled", advice.getAiStatus());
        assertFalse(advice.isAiUsed());
        verify(ai, never()).rank(any(), any(), any());
    }

    @Test
    void assignAllCountsEveryOutcomeAndNeverLeavesAnOrderUndocumented() {
        Order good = order("LH-5001", "Bengaluru", "Hyderabad", 1500, null);
        // this one only fits the route that is nearly full, has no crew and no departure time
        Order weak = order("LH-5060", "Bengaluru", "Vizag", 1500, null);
        when(orderService.findUnassigned("WH-A")).thenReturn(List.of(good, weak));
        when(warehouseService.findByWarehouseId("WH-A")).thenReturn(depotA);
        when(orderService.findByOrderId("LH-5001")).thenReturn(good);
        when(orderService.findByOrderId("LH-5060")).thenReturn(weak);

        Route strong = route("LH-1029", "WH-A", "Bengaluru", "Hyderabad", List.of("Vijayawada"),
                "20:00", 9, 10000, 6200, "T-101", "D-103", "READY");
        Route tired = route("LH-9001", "WH-C", "Pune", "Vizag", List.of("Bengaluru", "Hyderabad"),
                "", 12, 16000, 14500, "T-999", "D-999", "BLOCKED");
        when(routeService.findAll(null)).thenReturn(List.of(strong, tired));
        when(routeService.findByRouteId("LH-1029")).thenReturn(strong);
        when(routeService.assignOrder("LH-1029", "LH-5001")).thenReturn(strong);
        when(routeService.nextRouteId()).thenReturn("LH-4003");

        BatchAssignmentReport report = service.assignAll("WH-A", false);

        assertEquals(2, report.getTotalOrders());
        assertEquals(1, report.getAssigned());
        assertEquals(0, report.getNewRouteRequired());
        assertEquals(1, report.getNeedsReview());
        BatchAssignmentReport.Outcome weakOutcome = report.getOutcomes().stream()
                .filter(outcome -> outcome.getOrderId().equals("LH-5060")).findFirst().orElseThrow();
        assertEquals("NEEDS_REVIEW", weakOutcome.getAction());
        assertTrue(weakOutcome.getReason().contains("below the"), weakOutcome.getReason());
        assertNotNull(report.getMessage());
    }

    @Test
    void aiOpinionsAreOnlyEverAcceptedForRoutesTheEngineApproved() {
        Order order = order("LH-5001", "Bengaluru", "Hyderabad", 1500, null);
        Route local = route("LH-1029", "WH-A", "Bengaluru", "Hyderabad", List.of("Vijayawada"),
                "20:00", 9, 10000, 6200, "T-101", "D-103", "READY");
        Route other = route("LH-1033", "WH-A", "Bengaluru", "Vizag", List.of("Warangal", "Hyderabad"),
                "19:00", 15, 9000, 4000, "T-105", "D-102", "READY");
        when(orderService.findByOrderId("LH-5001")).thenReturn(order);
        when(routeService.findAll(null)).thenReturn(List.of(local, other));
        when(ai.isEnabled()).thenReturn(true);
        when(ai.rank(any(), any(), any())).thenAnswer(call -> {
            // the model prefers LH-1033 and also mentions LH-9999, which does not exist at all
            AiAdvisor.StatusHolder status = call.getArgument(2);
            status.set(AiAdvisor.Status.APPLIED);
            return Optional.of(new AiAdvisor.Ranking(
                    List.of("LH-1033", "LH-9999", "LH-1029"),
                    java.util.Map.of("LH-1033", "Arrives sooner for this stop."),
                    java.util.Map.of(),
                    "LH-1033 is quicker for the drop-off."));
        });

        AssignmentAdvice advice = service.advise("LH-5001", "WH-A", true);

        assertEquals(2, advice.getCandidates().size());
        assertEquals("LH-1033", advice.getCandidates().get(0).getRouteId());
        assertEquals("LH-1029", advice.getCandidates().get(1).getRouteId());
        assertTrue(advice.isAiUsed());
        assertTrue(advice.getCandidates().stream().noneMatch(item -> item.getRouteId().equals("LH-9999")),
                "a route the model invented must never reach the dispatcher");
    }

    // ------------------------------------------------------------------ builders

    private static AssignRequest request(String routeId, String driverId, String truckId) {
        AssignRequest request = new AssignRequest();
        request.setRouteId(routeId);
        request.setDriverId(driverId);
        request.setTruckId(truckId);
        request.setWarehouseId("WH-A");
        return request;
    }

    private static Route createdRoute() {
        Route route = new Route();
        route.setRouteId("LH-4001");
        route.setWarehouseId("WH-A");
        route.setOrigin("Bengaluru");
        route.setDestination("Chennai");
        route.setStops(List.of());
        route.setStopSequence(new ArrayList<>(List.of("Bengaluru", "Chennai")));
        route.setDepartureTime("20:00");
        route.setTravelDuration(9);
        route.setMaxCapacity(4000);
        route.setCurrentWeight(900);
        route.setTruckId("T-103");
        route.setDriverId("D-104");
        route.setStatus("READY");
        route.setReadiness("READY");
        route.setReadinessReason("Ready to dispatch");
        route.setCapacityPercent(23);
        route.setAvailableCapacity(3100);
        route.setOrderIds(new ArrayList<>(List.of("LH-5002")));
        return route;
    }

    private static Order order(String orderId, String origin, String destination, int weight, String routeId) {
        Order order = new Order();
        order.setOrderId(orderId);
        order.setCustomer("Coastal Traders");
        order.setOrigin(origin);
        order.setDestination(destination);
        order.setWeight(weight);
        order.setPieces(6);
        order.setServiceDate("2026-09-15");
        order.setStatus("READY");
        order.setRouteId(routeId);
        order.setWarehouseId("WH-A");
        return order;
    }

    private static Route route(String routeId, String warehouseId, String origin, String destination,
                               List<String> stops, String departureTime, int hours, int maxCapacity,
                               int currentWeight, String truckId, String driverId, String status) {
        Route route = new Route();
        route.setRouteId(routeId);
        route.setWarehouseId(warehouseId);
        route.setOrigin(origin);
        route.setDestination(destination);
        route.setStops(new ArrayList<>(stops));
        route.setDepartureTime(departureTime);
        route.setTravelDuration(hours);
        route.setMaxCapacity(maxCapacity);
        route.setCurrentWeight(currentWeight);
        route.setTruckId(truckId);
        route.setDriverId(driverId);
        route.setStatus(status);
        route.setReadiness("READY".equals(status) ? "READY" : status);
        route.setReadinessReason("");
        route.setEta(com.example.linehaul.service.LinehaulUtil.calculateEta(departureTime, hours));
        route.setCapacityPercent(com.example.linehaul.service.LinehaulUtil
                .capacityPercent(currentWeight, maxCapacity));
        route.setAvailableCapacity(Math.max(0, maxCapacity - currentWeight));
        route.setOrderIds(new ArrayList<>());
        List<String> sequence = new ArrayList<>();
        sequence.add(origin);
        sequence.addAll(stops);
        sequence.add(destination);
        route.setStopSequence(sequence);
        return route;
    }

    private static Vehicle truck(String truckId, int capacity, String status, String warehouseId) {
        Vehicle vehicle = new Vehicle();
        vehicle.setTruckId(truckId);
        vehicle.setType("Truck");
        vehicle.setCapacity(capacity);
        vehicle.setStatus(status);
        vehicle.setWarehouseId(warehouseId);
        if ("ASSIGNED".equals(status)) {
            vehicle.setRouteId("LH-1029");
        }
        return vehicle;
    }

    private static Driver driver(String driverId, String name, String status, String warehouseId) {
        Driver driver = new Driver();
        driver.setDriverId(driverId);
        driver.setName(name);
        driver.setStatus(status);
        driver.setWarehouseId(warehouseId);
        return driver;
    }
}
