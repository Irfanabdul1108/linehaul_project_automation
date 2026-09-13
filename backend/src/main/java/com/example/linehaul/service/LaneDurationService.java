package com.example.linehaul.service;

import com.example.linehaul.automation.LocationMatcher;
import com.example.linehaul.model.LaneDuration;
import com.example.linehaul.model.Warehouse;
import com.example.linehaul.repository.LaneDurationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The memory of the network: how long it takes to drive from one place to another.
 *
 * <p>This is what makes "pick an origin and a destination and the duration fills itself in" possible.
 * Two rules keep it honest:</p>
 * <ol>
 *   <li><b>a lane has no direction</b> - Bengaluru to Hyderabad and Hyderabad to Bengaluru are the
 *       same drive, so one answers for the other;</li>
 *   <li><b>a warehouse is its city</b> - {@code WH-A}, {@code Warehouse A} and {@code Bengaluru} are
 *       resolved to one token before the lane key is built, which is why the duration is found no
 *       matter which of the three names a route was written with.</li>
 * </ol>
 *
 * <p>Nothing is ever invented: a lane that was never travelled simply has no answer, and the form
 * keeps whatever the dispatcher types.</p>
 */
@Service
public class LaneDurationService {

    private static final Logger log = LoggerFactory.getLogger(LaneDurationService.class);

    private final LaneDurationRepository repository;
    private final WarehouseService warehouseService;

    public LaneDurationService(LaneDurationRepository repository, WarehouseService warehouseService) {
        this.repository = repository;
        this.warehouseService = warehouseService;
    }

    /** Every remembered lane, longest first is not useful here - alphabetical is easiest to scan. */
    public List<LaneDuration> findAll() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(lane -> LinehaulUtil.clean(lane.getLaneKey())))
                .toList();
    }

    /**
     * The remembered duration of a lane.
     *
     * @return empty when the two places were never connected before, so the caller can leave the
     *         field to the dispatcher instead of guessing a number.
     */
    public Optional<LaneDuration> lookup(String origin, String destination) {
        String key = laneKey(origin, destination);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return repository.findByLaneKey(key);
    }

    /** The remembered hours, or {@code null} when this lane is new to the network. */
    public Integer durationFor(String origin, String destination) {
        return lookup(origin, destination).map(LaneDuration::getTravelDuration).orElse(null);
    }

    /**
     * Records what a real route says about a lane. Called whenever a route is created or its lane or
     * duration is edited, so the estimate is always built from the routes that actually exist.
     *
     * @return the stored row, or empty when the input was not usable (missing place, zero hours, or
     *         an origin and destination that normalise to the same place)
     */
    public Optional<LaneDuration> remember(String origin, String destination, int hours) {
        return remember(origin, destination, hours, LaneDuration.SOURCE_ROUTE);
    }

    public Optional<LaneDuration> remember(String origin, String destination, int hours, String source) {
        String key = laneKey(origin, destination);
        if (key.isEmpty() || hours <= 0) {
            return Optional.empty();
        }
        String from = canonicalName(origin);
        String to = canonicalName(destination);

        LaneDuration lane = repository.findByLaneKey(key).orElse(null);
        if (lane == null) {
            lane = new LaneDuration(key, from, to, hours, 1, source);
            log.info("Learned a new lane: {} to {} takes {} h", from, to, hours);
            return Optional.of(repository.save(lane));
        }

        // A seeded reference row is replaced by the first real route; after that the value is the
        // running average, so one unusual route cannot throw the estimate off on its own.
        if (LaneDuration.SOURCE_SEED.equals(lane.getSource()) && LaneDuration.SOURCE_ROUTE.equals(source)) {
            lane.setTravelDuration(hours);
            lane.setSamples(1);
        } else {
            int samples = Math.max(1, lane.getSamples());
            int total = lane.getTravelDuration() * samples + hours;
            lane.setTravelDuration((int) Math.round(total / (double) (samples + 1)));
            lane.setSamples(samples + 1);
        }
        lane.setOrigin(from);
        lane.setDestination(to);
        if (LaneDuration.SOURCE_ROUTE.equals(source)) {
            lane.setSource(LaneDuration.SOURCE_ROUTE);
        }
        return Optional.of(repository.save(lane));
    }

    /** Seeds a reference row without overwriting anything a real route already taught us. */
    public void rememberIfUnknown(String origin, String destination, int hours) {
        String key = laneKey(origin, destination);
        if (key.isEmpty() || hours <= 0 || repository.existsByLaneKey(key)) {
            return;
        }
        repository.save(new LaneDuration(key, canonicalName(origin), canonicalName(destination),
                hours, 1, LaneDuration.SOURCE_SEED));
    }

    /**
     * Direction independent key of a lane. Warehouse aliases are resolved to the depot's city first,
     * so a route written as "Warehouse A" and one written as "Bengaluru" share a single row.
     */
    public String laneKey(String origin, String destination) {
        String from = LocationMatcher.token(canonicalName(origin));
        String to = LocationMatcher.token(canonicalName(destination));
        if (from.isEmpty() || to.isEmpty() || from.equals(to)) {
            return "";
        }
        return from.compareTo(to) <= 0 ? from + "|" + to : to + "|" + from;
    }

    /**
     * The name this project prefers for a place: the city of the warehouse it names, or the text
     * itself when it is not a warehouse at all.
     */
    public String canonicalName(String place) {
        String cleaned = LinehaulUtil.clean(place);
        if (cleaned.isEmpty()) {
            return "";
        }
        for (Warehouse warehouse : safeWarehouses()) {
            if (LocationMatcher.isThisWarehouse(cleaned, warehouse)) {
                String city = LinehaulUtil.clean(warehouse.getCity());
                return city.isEmpty() ? LinehaulUtil.clean(warehouse.getName()) : city;
            }
        }
        return cleaned;
    }

    private List<Warehouse> safeWarehouses() {
        try {
            return warehouseService.findAll();
        } catch (RuntimeException databaseNotReady) {
            // A lane key must never be the reason a request fails; without the warehouse list the
            // place name is simply used as it was written.
            return new ArrayList<>();
        }
    }
}
