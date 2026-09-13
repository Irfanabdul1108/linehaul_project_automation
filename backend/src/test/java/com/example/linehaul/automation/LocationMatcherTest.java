package com.example.linehaul.automation;

import com.example.linehaul.model.Warehouse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lane names are free text, so this is where "is that the same place?" is decided. The rules below
 * are the ones the assignment engine relies on.
 */
class LocationMatcherTest {

    @Test
    void ignoresCasePunctuationAndGenericWords() {
        // "warehouse"/"hub" are generic words: what identifies the place is the rest of the name
        assertEquals("bengaluru", LocationMatcher.token("Bengaluru"));
        assertEquals("a", LocationMatcher.token("  WAREHOUSE-A  "));
        assertEquals("hyderabad", LocationMatcher.token("Hyderabad"));
        assertTrue(LocationMatcher.same("  bengaluru ", "Bengaluru"));
        assertTrue(LocationMatcher.same("Hyderabad Hub", "Hyderabad"));
    }

    @Test
    void differentPlacesStayDifferent() {
        assertFalse(LocationMatcher.same("Bengaluru", "Chennai"));
        assertFalse(LocationMatcher.same("Hyderabad", "Hyderabad X Roads"));
        assertFalse(LocationMatcher.same("", "Hyderabad"));
        assertFalse(LocationMatcher.same(null, null));
    }

    @Test
    void aWarehouseIsRecognisedByItsIdCodeHubAndCity() {
        Warehouse depot = new Warehouse("WH-B", "Chennai", "WH-B", "Chennai", "Chennai", "Tamil Nadu", "");

        assertTrue(LocationMatcher.isThisWarehouse("Chennai", depot));
        assertTrue(LocationMatcher.isThisWarehouse("WH-B", depot));
        assertTrue(LocationMatcher.isThisWarehouse("  chennai  ", depot));
        assertFalse(LocationMatcher.isThisWarehouse("Bengaluru", depot));
    }

    @Test
    void findsPositionsAlongTheRun() {
        List<String> sequence = List.of("Chennai", "Bengaluru", "Vijayawada", "Hyderabad");
        Warehouse depotA = new Warehouse("WH-A", "Bengaluru", "WH-A", "Bengaluru", "Bengaluru", "Karnataka", "");

        assertEquals(1, LocationMatcher.indexOfWarehouse(sequence, depotA));
        assertEquals(3, LocationMatcher.indexOf(sequence, "Hyderabad"));
        assertEquals(-1, LocationMatcher.indexOf(sequence, "Chennai"));
        assertEquals(-1, LocationMatcher.indexOfWarehouse(sequence,
                new Warehouse("WH-D", "Kolkata", "WH-D", "Kolkata", "Kolkata", "West Bengal", "")));
    }

    /**
     * The rule that decides whether a route may carry an order at all. It is shared by the smart
     * assignment engine and by the manual drag-and-drop, so both refuse exactly the same routes.
     */
    @Test
    void aRouteOnlyReachesADestinationItEndsAtOrStopsAt() {
        List<String> toVizag = List.of("Bengaluru", "Warangal", "Hyderabad", "Vizag");

        assertEquals(3, LocationMatcher.unloadIndex(toVizag, "Vizag"));
        assertEquals(2, LocationMatcher.unloadIndex(toVizag, "Hyderabad"));
        assertTrue(LocationMatcher.reaches(toVizag, "Warangal"));

        // the origin is where freight is loaded, never where it is dropped
        assertEquals(-1, LocationMatcher.unloadIndex(toVizag, "Bengaluru"));
        // and a place the route simply does not serve is refused
        assertFalse(LocationMatcher.reaches(toVizag, "Chennai"));
        assertFalse(LocationMatcher.reaches(List.of("Bengaluru", "Chennai"), "Vizag"));
        assertFalse(LocationMatcher.reaches(null, "Vizag"));
    }
}
