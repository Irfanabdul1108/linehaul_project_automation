package com.example.linehaul.automation;

import com.example.linehaul.model.Route;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The arithmetic behind "can this route physically still carry it". */
class CapacityRulesTest {

    @Test
    void anOrderFitsWhileThereIsRoom() {
        assertNull(CapacityRules.rejectRouteCapacity(route(1000, 800), 100));
    }

    /** The exact numbers from the assignment rules: 950 already loaded, 100 to add, 1000 maximum. */
    @Test
    void anOrderIsRefusedWhenItWouldGoOver() {
        String reason = CapacityRules.rejectRouteCapacity(route(1000, 950), 100);

        assertNotNull(reason);
        assertTrue(reason.contains("50 kg is free"), reason);
        assertTrue(reason.contains("needs 100 kg"), reason);
    }

    @Test
    void fillingTheRouteExactlyIsStillAllowed() {
        assertNull(CapacityRules.rejectRouteCapacity(route(1000, 900), 100));
    }

    @Test
    void theTruckLimitsTheRouteToo() {
        assertNull(CapacityRules.rejectVehicleCapacity(route(9000, 800), 1500, 2500));
        assertNotNull(CapacityRules.rejectVehicleCapacity(route(9000, 2000), 1500, 2500));
    }

    @Test
    void anUnknownTruckCapacityIsNotGuessed() {
        assertNull(CapacityRules.rejectVehicleCapacity(route(9000, 8000), 1500, null));
        assertNull(CapacityRules.rejectVehicleCapacity(route(9000, 8000), 1500, 0));
    }

    @Test
    void fillAfterLoadingIsRounded() {
        assertEquals(8, CapacityRules.fillPercentAfter(route(1000, 0), 80));
        assertEquals(100, CapacityRules.fillPercentAfter(route(1000, 950), 50));
        assertEquals(0, CapacityRules.fillPercentAfter(route(0, 0), 50));
    }

    private static Route route(int maxCapacity, int currentWeight) {
        Route route = new Route();
        route.setRouteId("LH-TEST");
        route.setOrigin("Bengaluru");
        route.setDestination("Hyderabad");
        route.setStops(List.of());
        route.setMaxCapacity(maxCapacity);
        route.setCurrentWeight(currentWeight);
        return route;
    }
}
