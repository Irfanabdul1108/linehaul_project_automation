package com.example.linehaul.controller;

import com.example.linehaul.model.LaneDuration;
import com.example.linehaul.service.LaneDurationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The lane memory of the network, exposed so the "Create Route" form can fill the travel duration in
 * by itself once an origin and a destination are picked.
 *
 * <p>Read-only on purpose: durations are learned from the routes that are actually created, never
 * typed into this endpoint.</p>
 */
@RestController
@RequestMapping("/api/lane-durations")
public class LaneDurationController {

    private final LaneDurationService laneDurations;

    public LaneDurationController(LaneDurationService laneDurations) {
        this.laneDurations = laneDurations;
    }

    /** Every lane the network has travelled, so the UI can show what is already known. */
    @GetMapping
    public List<LaneDuration> all() {
        return laneDurations.findAll();
    }

    /**
     * The duration of one lane.
     *
     * <p>Always answers 200 with {@code known: false} when the two places were never connected: an
     * unknown lane is a normal situation, not an error, and the form simply keeps whatever the
     * dispatcher typed.</p>
     */
    @GetMapping("/lookup")
    public Map<String, Object> lookup(@RequestParam String origin, @RequestParam String destination) {
        Optional<LaneDuration> lane = laneDurations.lookup(origin, destination);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("origin", laneDurations.canonicalName(origin));
        body.put("destination", laneDurations.canonicalName(destination));
        body.put("known", lane.isPresent());
        body.put("travelDuration", lane.map(LaneDuration::getTravelDuration).orElse(null));
        body.put("samples", lane.map(LaneDuration::getSamples).orElse(0));
        body.put("source", lane.map(LaneDuration::getSource).orElse(null));
        return body;
    }
}
