package com.example.linehaul.automation;

import com.example.linehaul.dto.RouteRecommendation;
import com.example.linehaul.model.Status;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns the eligible routes of {@link RouteMatchingService} into a ranking.
 *
 * <p>Hard rules (does the route reach the destination, does it pass the depot, does the load fit) are
 * answered by {@link RouteMatchingService}. This class only weighs those facts, on a simple 0-100
 * scale, so a dispatcher can read the reasoning off the screen. No machine learning is involved:
 * the same data always produces the same order.</p>
 */
@Service
public class RouteScoringService {

    /** How many candidates the UI should show. */
    public static final int TOP_N = 3;

    /** "Assign All Orders" only writes when the best route clears this score. */
    public static final int AUTO_ASSIGN_THRESHOLD = 55;

    private static final int MAX_SCORE = 100;

    public List<RouteRecommendation> rank(List<MatchOutcome> outcomes) {
        List<RouteRecommendation> items = new ArrayList<>();
        for (MatchOutcome outcome : outcomes) {
            items.add(outcome.recommendation());
        }

        double fastest = fastestHours(items);
        for (RouteRecommendation item : items) {
            score(item, fastest);
        }

        items.sort(Comparator
                .comparingInt(RouteRecommendation::getScore).reversed()
                .thenComparingDouble(RouteRecommendation::getTravelHours)
                .thenComparing(RouteRecommendation::getRouteId));

        for (int i = 0; i < items.size(); i++) {
            items.get(i).setRank(i + 1);
        }
        return items;
    }

    private double fastestHours(List<RouteRecommendation> items) {
        double fastest = Double.MAX_VALUE;
        for (RouteRecommendation item : items) {
            if (item.isEtaKnown() && item.getTravelHours() > 0) {
                fastest = Math.min(fastest, item.getTravelHours());
            }
        }
        return fastest == Double.MAX_VALUE ? 0 : fastest;
    }

    private void score(RouteRecommendation item, double fastest) {
        List<RouteRecommendation.ScoreFactor> parts = new ArrayList<>();

        int destinationPoints = switch (item.getMatchType() == null ? "" : item.getMatchType()) {
            case RouteMatchingService.MATCH_EXACT -> 40;
            case RouteMatchingService.MATCH_DESTINATION -> 30;
            case RouteMatchingService.MATCH_STOP -> 22;
            default -> 15;
        };
        parts.add(new RouteRecommendation.ScoreFactor("Destination match", destinationPoints,
                item.getMatchLabel() == null ? "" : item.getMatchLabel()));

        int originPoints = item.isCrossWarehouse() ? 14 : 20;
        parts.add(new RouteRecommendation.ScoreFactor("Loading point", originPoints,
                item.isCrossWarehouse()
                        ? "Route starts elsewhere but stops at " + item.getPickupLabel()
                        : "Route starts at your warehouse"));

        int headroomPoints = switch (bucket(item.getFillPercentAfter())) {
            case 0 -> 15;
            case 1 -> 12;
            case 2 -> 8;
            default -> 5;
        };
        parts.add(new RouteRecommendation.ScoreFactor("Capacity left after loading", headroomPoints,
                item.getFillPercentAfter() + "% full with this order on board"));

        int vehiclePoints;
        String vehicleDetail;
        if (item.getTruckCapacity() == null) {
            vehiclePoints = item.isNeedsTruck() ? 4 : 2;
            vehicleDetail = item.isNeedsTruck()
                    ? "No truck yet - you will pick one from this warehouse"
                    : "Truck capacity is not recorded";
        } else if (item.getLoadAfter() <= item.getTruckCapacity()) {
            vehiclePoints = item.getLoadAfter() <= (int) (item.getTruckCapacity() * 0.9d) ? 8 : 5;
            vehicleDetail = "Truck " + item.getTruckId() + " carries " + item.getTruckCapacity() + " kg";
        } else {
            vehiclePoints = 0;
            vehicleDetail = "Truck " + item.getTruckId() + " is too small";
        }
        parts.add(new RouteRecommendation.ScoreFactor("Vehicle capacity", vehiclePoints, vehicleDetail));

        String status = item.getStatus() == null ? "" : item.getStatus().toUpperCase();
        int statusPoints = Status.READY.equals(status) ? 12 : Status.DRAFT.equals(status) ? 9
                : Status.BLOCKED.equals(status) ? 5 : 4;
        parts.add(new RouteRecommendation.ScoreFactor("Route status", statusPoints,
                status + (item.getReadinessReason() == null || item.getReadinessReason().isBlank()
                        ? "" : " - " + item.getReadinessReason())));

        int etaPoints = 0;
        String etaDetail = "No ETA available, so it could not be compared";
        if (item.isEtaKnown() && fastest > 0 && item.getTravelHours() > 0) {
            etaPoints = (int) Math.round(Math.min(15d, 15d * fastest / item.getTravelHours()));
            etaDetail = item.getTravelTime() + " on board, arriving " + item.getOrderEta();
        }
        parts.add(new RouteRecommendation.ScoreFactor("ETA", etaPoints, etaDetail));

        int penalty = item.isCrossWarehouse() ? -6 : 0;
        if (penalty != 0) {
            parts.add(new RouteRecommendation.ScoreFactor("Cross-warehouse penalty", penalty,
                    "Freight changes trucks' home depot, so a local route is preferred when it is comparable"));
        }

        int total = 0;
        for (RouteRecommendation.ScoreFactor part : parts) {
            total += part.getPoints();
        }
        item.setScoreBreakdown(parts);
        item.setScore(Math.max(0, Math.min(MAX_SCORE, total)));
    }

    /** 0 = plenty of room, 1 = comfortable, 2 = tight but fine, 3 = nearly full. */
    private int bucket(int fillPercentAfter) {
        if (fillPercentAfter <= 60) {
            return 0;
        }
        if (fillPercentAfter <= 80) {
            return 1;
        }
        if (fillPercentAfter <= 95) {
            return 2;
        }
        return 3;
    }
}
