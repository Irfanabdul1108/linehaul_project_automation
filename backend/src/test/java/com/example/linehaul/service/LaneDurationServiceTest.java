package com.example.linehaul.service;

import com.example.linehaul.model.LaneDuration;
import com.example.linehaul.model.Warehouse;
import com.example.linehaul.repository.LaneDurationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The memory that removes the typing: once the network has driven a lane, the duration of that lane
 * is known and a new route between the same two places fills it in by itself.
 */
class LaneDurationServiceTest {

    private Map<String, LaneDuration> stored;
    private LaneDurationService service;

    @BeforeEach
    void setUp() {
        stored = new LinkedHashMap<>();

        LaneDurationRepository repository = mock(LaneDurationRepository.class);
        when(repository.save(any(LaneDuration.class))).thenAnswer(call -> {
            LaneDuration lane = call.getArgument(0);
            stored.put(lane.getLaneKey(), lane);
            return lane;
        });
        when(repository.findByLaneKey(anyString()))
                .thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0))));
        when(repository.existsByLaneKey(anyString()))
                .thenAnswer(call -> stored.containsKey(call.getArgument(0)));
        when(repository.findAll()).thenAnswer(call -> List.copyOf(stored.values()));

        WarehouseService warehouses = mock(WarehouseService.class);
        when(warehouses.findAll()).thenReturn(List.of(
                new Warehouse("WH-A", "Warehouse A", "WH-A", "Bengaluru", "Bengaluru", "Karnataka", ""),
                new Warehouse("WH-B", "Warehouse B", "WH-B", "Chennai", "Chennai", "Tamil Nadu", "")));

        service = new LaneDurationService(repository, warehouses);
    }

    @Test
    void aLaneIsRememberedAndFoundAgain() {
        service.remember("Bengaluru", "Hyderabad", 9);

        assertEquals(9, service.durationFor("Bengaluru", "Hyderabad"));
    }

    /** Driving back takes as long as driving there, so one row answers for both directions. */
    @Test
    void aLaneHasNoDirection() {
        service.remember("Bengaluru", "Hyderabad", 9);

        assertEquals(9, service.durationFor("Hyderabad", "Bengaluru"));
        assertEquals(1, service.findAll().size());
    }

    /**
     * The point of requirement 2 and 3 together: a depot may be written as its id, its designation or
     * its city, and all three mean the same place - so all three find the same duration.
     */
    @Test
    void aWarehouseIsTheSamePlaceUnderEveryNameItHas() {
        service.remember("Bengaluru", "Hyderabad", 9);

        assertEquals(9, service.durationFor("WH-A", "Hyderabad"));
        assertEquals(9, service.durationFor("Warehouse A", "Hyderabad"));
        assertEquals(1, service.findAll().size());
    }

    @Test
    void aLaneNobodyHasDrivenHasNoAnswer() {
        service.remember("Bengaluru", "Hyderabad", 9);

        assertNull(service.durationFor("Bengaluru", "Guwahati"));
        assertFalse(service.lookup("Chennai", "Delhi").isPresent());
    }

    /** Nonsense in, nothing out: no lane to itself, no zero-hour drives, no blank places. */
    @Test
    void unusableInputIsNeverStored() {
        assertFalse(service.remember("Bengaluru", "Bengaluru", 5).isPresent());
        assertFalse(service.remember("Bengaluru", "Hyderabad", 0).isPresent());
        assertFalse(service.remember("", "Hyderabad", 5).isPresent());
        assertFalse(service.remember("Bengaluru", null, 5).isPresent());
        assertTrue(service.findAll().isEmpty());
    }

    /** The first real route replaces the reference value; later routes average with it. */
    @Test
    void realRoutesTeachTheLaneBetterThanTheSeedTable() {
        service.rememberIfUnknown("Bengaluru", "Hyderabad", 9);
        assertEquals(9, service.durationFor("Bengaluru", "Hyderabad"));

        service.remember("Bengaluru", "Hyderabad", 11);
        assertEquals(11, service.durationFor("Bengaluru", "Hyderabad"));

        service.remember("Bengaluru", "Hyderabad", 13);
        assertEquals(12, service.durationFor("Bengaluru", "Hyderabad"));
        assertEquals(LaneDuration.SOURCE_ROUTE, service.lookup("Bengaluru", "Hyderabad").orElseThrow().getSource());
    }

    @Test
    void seedingNeverOverwritesWhatARouteAlreadyProved() {
        service.remember("Bengaluru", "Hyderabad", 11);
        service.rememberIfUnknown("Bengaluru", "Hyderabad", 9);

        assertEquals(11, service.durationFor("Bengaluru", "Hyderabad"));
    }

    @Test
    void aPlaceIsNamedByItsCityWheneverItIsADepot() {
        assertEquals("Bengaluru", service.canonicalName("Warehouse A"));
        assertEquals("Bengaluru", service.canonicalName("WH-A"));
        assertEquals("Chennai", service.canonicalName("Warehouse B"));
        // somewhere that is not a depot keeps the name it was given
        assertEquals("Hyderabad", service.canonicalName("Hyderabad"));
    }
}
