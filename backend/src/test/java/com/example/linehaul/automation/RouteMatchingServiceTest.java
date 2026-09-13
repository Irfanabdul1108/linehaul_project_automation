package com.example.linehaul.automation;

import com.example.linehaul.dto.RouteRejection;
import com.example.linehaul.dto.RouteRecommendation;
import com.example.linehaul.model.Order;
import com.example.linehaul.model.Route;
import com.example.linehaul.model.Vehicle;
import com.example.linehaul.model.Warehouse;
import com.example.linehaul.service.LinehaulUtil;
import com.example.linehaul.service.RouteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The filtering rules of the assignment engine, on hand-built routes. These are the rules that must
 * never bend, because they are what stops an order from landing on a route that cannot carry it.
 */
class RouteMatchingServiceTest {

    private RouteMatchingService matching;
    private RouteScoringService scoring;

    private Warehouse depotA;
    private Vehicle truck10000;
    private Vehicle truck12000;

    @BeforeEach
    void setUp() {
        // a real RouteService with no repositories: isEditable() only looks at the route itself
        matching = new RouteMatchingService(new RouteService(null, null, null, null, null));
        scoring = new RouteScoringService();

        depotA = new Warehouse("WH-A", "Bengaluru", "WH-A", "Bengaluru", "Bengaluru", "Karnataka", "");
        truck10000 = vehicle("T-101", 10000);
        truck12000 = vehicle("T-204", 12000);
    }

    /** Case A: same lane, same depot - the strongest match there is. */
    @Test
    void anExactLaneMatchWins() {
        Route local = route("LH-1029", "Bengaluru", "Hyderabad", List.of("Vijayawada"),
                "20:00", 9, 10000, 6200, "T-101", "D-103", "WH-A", "READY");

        MatchResult result = matching.evaluate(order("LH-5001", 1500), depotA,
                List.of(local), vehicles(truck10000));

        assertEquals(1, result.eligible().size());
        RouteRecommendation item = result.eligible().get(0).recommendation();
        assertEquals(RouteMatchingService.MATCH_EXACT, item.getMatchType());
        assertEquals(0, item.getPickupIndex());
        assertEquals(2, item.getDropoffIndex());
        assertEquals(3800, item.getAvailableCapacity());
        assertEquals(7700, item.getLoadAfter());
        assertFalse(item.isCrossWarehouse());
        assertTrue(item.getReasons().get(0).contains("Exact lane match"), item.getReasons().toString());
    }

    /** Case D and I: a Chennai truck that stops here may be used, but not re-crewed. */
    @Test
    void aCrossWarehouseRouteWithAValidStopIsEligibleAndKeepsItsOwnCrew() {
        Route through = route("LH-2022", "Chennai", "Hyderabad", List.of("Bengaluru", "Vijayawada"),
                "18:00", 14, 12000, 5000, "T-204", "D-203", "WH-B", "READY");

        MatchResult result = matching.evaluate(order("LH-5001", 1500), depotA,
                List.of(through), vehicles(truck12000));

        assertEquals(1, result.eligible().size());
        RouteRecommendation item = result.eligible().get(0).recommendation();
        assertEquals(1, item.getPickupIndex());
        assertEquals("Bengaluru", item.getPickupLabel());
        assertTrue(item.isCrossWarehouse());
        assertEquals("WH-B", item.getWarehouseId());
        assertFalse(item.isNeedsDriver());
        assertTrue(item.getWarnings().get(0).contains("originates in WH-B"), item.getWarnings().toString());
    }

    /** A route that ends somewhere else but passes the destination on the way is still usable (case C). */
    @Test
    void aDestinationThatIsAnIntermediateStopCounts() {
        Route longer = route("LH-1033", "Bengaluru", "Vizag", List.of("Warangal", "Hyderabad"),
                "19:00", 15, 9000, 4000, "T-105", "D-102", "WH-A", "READY");

        MatchResult result = matching.evaluate(order("LH-5001", 1500), depotA,
                List.of(longer), vehicles(vehicle("T-105", 9000)));

        assertEquals(1, result.eligible().size());
        RouteRecommendation item = result.eligible().get(0).recommendation();
        assertEquals(RouteMatchingService.MATCH_STOP, item.getMatchType());
        assertEquals(2, item.getDropoffIndex());
        assertTrue(item.getMatchLabel().contains("scheduled stop"), item.getMatchLabel());
    }

    /** Case E: the right route, but it would be overloaded - refused, with the numbers in the reason. */
    @Test
    void anOverloadedRouteIsRefusedAndTheReasonCarriesTheNumbers() {
        Route full = route("LH-1030", "Bengaluru", "Chennai", List.of(),
                "21:30", 8, 8000, 7500, "T-102", "D-101", "WH-A", "READY");
        Order order = order("LH-5002", 900);

        MatchResult result = matching.evaluate(order, depotA, List.of(full), vehicles(vehicle("T-102", 8000)));

        assertTrue(result.eligible().isEmpty());
        RouteRejection rejection = result.rejected().get(0);
        assertEquals("ROUTE_CAPACITY", rejection.getStage());
        assertTrue(rejection.getReason().contains("500 kg is free"), rejection.getReason());
        assertTrue(rejection.getReason().contains("needs 900 kg"), rejection.getReason());
    }

    /** The truck can be the limit even when the route is not. */
    @Test
    void aTruckThatIsTooSmallRefusesTheRoute() {
        Route small = route("LH-2023", "Chennai", "Madurai", List.of("Bengaluru"),
                "07:00", 6, 9000, 0, "T-203", null, "WH-B", "BLOCKED");

        MatchResult result = matching.evaluate(order("LH-5001", 3400), depotA,
                List.of(small), vehicles(vehicle("T-203", 3000)));

        assertTrue(result.eligible().isEmpty());
        assertEquals("VEHICLE_CAPACITY", result.rejected().get(0).getStage());
    }

    /** A route that already left is closed; the engine must not touch it. */
    @Test
    void aRouteThatAlreadyDepartedIsClosedForChanges() {
        Route gone = route("LH-1035", "Bengaluru", "Mumbai", List.of(),
                "05:00", 16, 7000, 3000, "T-106", "D-106", "WH-A", "IN_TRANSIT");

        MatchResult result = matching.evaluate(order("LH-5001", 500), depotA, List.of(gone), vehicles());

        assertTrue(result.eligible().isEmpty());
        assertEquals("ROUTE_STATUS", result.rejected().get(0).getStage());
    }

    /** The loading point has to come before the unloading point, not after it. */
    @Test
    void aStopAfterTheDestinationIsNotAConnection() {
        Route backwards = route("LH-9001", "Chennai", "Chennai", List.of("Hyderabad", "Bengaluru"),
                "08:00", 18, 10000, 0, null, null, "WH-B", "DRAFT");

        MatchResult result = matching.evaluate(order("LH-5001", 800), depotA, List.of(backwards), vehicles());

        assertTrue(result.eligible().isEmpty());
        assertEquals("STOP_ORDER", result.rejected().get(0).getStage());
    }

    /** A route that never comes here is not a candidate, even though it goes to the right city. */
    @Test
    void aRouteThatNeverReachesTheDepotIsRefused() {
        Route elsewhere = route("LH-4042", "Kolkata", "Hyderabad", List.of("Chennai"),
                "10:00", 26, 15000, 5000, "T-403", "D-403", "WH-D", "READY");

        MatchResult result = matching.evaluate(order("LH-5001", 1500), depotA, List.of(elsewhere), vehicles());

        assertTrue(result.eligible().isEmpty());
        assertEquals("WAREHOUSE_STOP", result.rejected().get(0).getStage());
        assertTrue(result.rejected().get(0).getReason().contains("does not pass through Bengaluru"),
                result.rejected().get(0).getReason());
    }

    /** Nothing is invented: without a departure time the ETA is reported as unknown. */
    @Test
    void aMissingDepartureTimeIsReportedAsUnknownEta() {
        Route noTime = route("LH-1099", "Bengaluru", "Hyderabad", List.of(),
                "", 9, 10000, 0, null, null, "WH-A", "DRAFT");

        MatchResult result = matching.evaluate(order("LH-5001", 500), depotA, List.of(noTime), vehicles());

        RouteRecommendation item = result.eligible().get(0).recommendation();
        assertFalse(item.isEtaKnown());
        assertNull(item.getOrderEta());
        assertTrue(item.getWarnings().stream().anyMatch(w -> w.contains("ETA could not be calculated")),
                item.getWarnings().toString());
    }

    /** Ranking is deterministic: the best local match leads, and the cross-warehouse one follows. */
    @Test
    void rankingPutsTheLocalExactMatchFirstAndKeepsThree() {
        List<Route> network = List.of(
                route("LH-1029", "Bengaluru", "Hyderabad", List.of("Vijayawada"),
                        "20:00", 9, 10000, 6200, "T-101", "D-103", "WH-A", "READY"),
                route("LH-2022", "Chennai", "Hyderabad", List.of("Bengaluru", "Vijayawada"),
                        "18:00", 14, 12000, 5000, "T-204", "D-203", "WH-B", "READY"),
                route("LH-1033", "Bengaluru", "Vizag", List.of("Warangal", "Hyderabad"),
                        "19:00", 15, 9000, 4000, "T-105", "D-102", "WH-A", "READY"),
                route("LH-3034", "Pune", "Hyderabad", List.of("Bengaluru"),
                        "17:00", 11, 9000, 0, null, null, "WH-C", "BLOCKED"));

        MatchResult result = matching.evaluate(order("LH-5001", 1500), depotA,
                network, vehicles(truck10000, truck12000, vehicle("T-105", 9000)));

        assertEquals(4, result.eligible().size());
        List<RouteRecommendation> ranked = scoring.rank(result.eligible());

        assertEquals("LH-1029", ranked.get(0).getRouteId());
        assertEquals(1, ranked.get(0).getRank());
        assertTrue(ranked.get(0).getScore() >= RouteScoringService.AUTO_ASSIGN_THRESHOLD,
                "a perfect local match must clear the auto assign bar");
        assertTrue(ranked.get(3).getScore() < ranked.get(0).getScore());
    }

    /** The engine also reports the pipeline the UI shows as a check list. */
    @Test
    void everyInspectedRouteShowsUpInThePipelineTrace() {
        MatchResult result = matching.evaluate(order("LH-5001", 1500), depotA,
                List.of(route("LH-1029", "Bengaluru", "Hyderabad", List.of("Vijayawada"),
                        "20:00", 9, 10000, 6200, "T-101", "D-103", "WH-A", "READY")),
                vehicles(truck10000));

        assertEquals(1, result.routesConsidered());
        assertTrue(result.steps().stream().anyMatch(step -> step.getLabel().equals("Capacity available")),
                result.steps().toString());
        assertNotNull(result.steps().get(0).getDetail());
    }

    // ------------------------------------------------------------------ builders

    private Map<String, Vehicle> vehicles(Vehicle... items) {
        Map<String, Vehicle> index = new LinkedHashMap<>();
        for (Vehicle item : items) {
            index.put(item.getTruckId().toUpperCase(), item);
        }
        return index;
    }

    private static Vehicle vehicle(String truckId, int capacity) {
        Vehicle vehicle = new Vehicle();
        vehicle.setTruckId(truckId);
        vehicle.setType("Truck");
        vehicle.setCapacity(capacity);
        vehicle.setStatus("ASSIGNED");
        return vehicle;
    }

    private static Order order(String orderId, int weight) {
        Order order = new Order();
        order.setOrderId(orderId);
        order.setCustomer("Coastal Traders");
        order.setOrigin("Bengaluru");
        order.setDestination("Hyderabad");
        order.setWeight(weight);
        order.setPieces(6);
        order.setServiceDate("2026-09-15");
        order.setStatus("READY");
        order.setWarehouseId("WH-A");
        return order;
    }

    private static Route route(String routeId, String origin, String destination, List<String> stops,
                               String departureTime, int hours, int maxCapacity, int currentWeight,
                               String truckId, String driverId, String warehouseId, String status) {
        Route route = new Route();
        route.setRouteId(routeId);
        route.setOrigin(origin);
        route.setDestination(destination);
        route.setStops(new ArrayList<>(stops));
        route.setDepartureTime(departureTime);
        route.setTravelDuration(hours);
        route.setMaxCapacity(maxCapacity);
        route.setCurrentWeight(currentWeight);
        route.setTruckId(truckId);
        route.setDriverId(driverId);
        route.setWarehouseId(warehouseId);
        route.setStatus(status);
        route.setOrderIds(new ArrayList<>());
        // the stop sequence is normally derived by RouteService.decorate(); the unit test feeds it in
        List<String> sequence = new ArrayList<>();
        sequence.add(origin);
        sequence.addAll(stops);
        sequence.add(destination);
        route.setStopSequence(sequence);
        route.setEta(LinehaulUtil.calculateEta(departureTime, hours));
        route.setCapacityPercent(LinehaulUtil.capacityPercent(currentWeight, maxCapacity));
        route.setAvailableCapacity(Math.max(0, maxCapacity - currentWeight));
        return route;
    }
}
