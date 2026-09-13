package com.example.linehaul.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


class LinehaulUtilTest {

    @Test
    void etaIsDeparturePlusDurationAcrossMidnight() {
        assertEquals("05:00 AM (+1 day)", LinehaulUtil.calculateEta("20:00", 9));
    }

    @Test
    void etaOnTheSameDayHasNoDayMarker() {
        assertEquals("10:00 AM", LinehaulUtil.calculateEta("06:00", 4));
    }

    @Test
    void etaHandlesLongTrips() {
        assertEquals("11:00 AM (+1 day)", LinehaulUtil.calculateEta("21:00", 14));
        assertEquals("07:00 PM (+1 day)", LinehaulUtil.calculateEta("19:00", 24));
    }

    @Test
    void etaHandlesAMDepartures() {
        assertEquals("09:30 AM (+1 day)", LinehaulUtil.calculateEta("22:30", 11));
    }

    @Test
    void badInputDoesNotThrow() {
        assertNull(LinehaulUtil.calculateEta(null, 9));
        assertNull(LinehaulUtil.calculateEta("", 9));
        assertNull(LinehaulUtil.calculateEta("not-a-time", 9));
    }

    @Test
    void capacityIsAPercentage() {
        assertEquals(0, LinehaulUtil.capacityPercent(0, 10000));
        assertEquals(82, LinehaulUtil.capacityPercent(8200, 10000));
        assertEquals(100, LinehaulUtil.capacityPercent(10000, 10000));
        assertEquals(120, LinehaulUtil.capacityPercent(12000, 10000));
    }

    @Test
    void zeroCapacityNeverDividesByZero() {
        assertEquals(0, LinehaulUtil.capacityPercent(500, 0));
    }
}
