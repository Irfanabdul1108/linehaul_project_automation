package com.example.linehaul.automation;

import com.example.linehaul.dto.RouteRecommendation;
import com.example.linehaul.model.Route;

/**
 * Pairs the data the UI sees with the stored route it came from, so the engine can re-validate the
 * exact same numbers right before writing to MongoDB.
 */
public class MatchOutcome {

    private final Route route;
    private final RouteRecommendation recommendation;

    public MatchOutcome(Route route, RouteRecommendation recommendation) {
        this.route = route;
        this.recommendation = recommendation;
    }

    public Route route() {
        return route;
    }

    public RouteRecommendation recommendation() {
        return recommendation;
    }
}
