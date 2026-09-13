package com.example.linehaul.automation;

import com.example.linehaul.dto.PipelineStep;
import com.example.linehaul.dto.RouteRejection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What the filter pipeline produced for a single order. */
public class MatchResult {

    private final List<MatchOutcome> eligible = new ArrayList<>();
    private final List<RouteRejection> rejected = new ArrayList<>();
    private final List<PipelineStep> steps = new ArrayList<>();
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private int routesConsidered;

    /** Counts how many routes survived each individual filter, so the trace can say so precisely. */
    public void note(String stage) {
        counts.merge(stage, 1, Integer::sum);
    }

    public int count(String stage) {
        return counts.getOrDefault(stage, 0);
    }

    public void addEligible(MatchOutcome outcome) {
        eligible.add(outcome);
    }

    public void addRejected(RouteRejection rejection) {
        rejected.add(rejection);
    }

    public void addStep(PipelineStep step) {
        steps.add(step);
    }

    public List<MatchOutcome> eligible() {
        return eligible;
    }

    public List<RouteRejection> rejected() {
        return rejected;
    }

    public List<PipelineStep> steps() {
        return steps;
    }

    public int routesConsidered() {
        return routesConsidered;
    }

    public void setRoutesConsidered(int routesConsidered) {
        this.routesConsidered = routesConsidered;
    }
}
