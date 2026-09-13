package com.example.linehaul.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * One route that the assignment engine considered for an order, with the numbers behind the
 * recommendation. Every value in here comes from MongoDB; nothing is generated.
 */
public class RouteRecommendation {

    private int rank;
    private String routeId;
    private String warehouseId;
    private String warehouseName;
    private boolean crossWarehouse;
    private String origin;
    private String destination;
    private List<String> stops = new ArrayList<>();
    private List<String> stopSequence = new ArrayList<>();

    /** EXACT (same lane), DESTINATION (ends in the right city) or STOP (passes through it). */
    private String matchType;
    private String matchLabel;
    private int pickupIndex = -1;
    private int dropoffIndex = -1;
    private String pickupLabel;

    private int orderWeight;
    private int currentWeight;
    private int maxCapacity;
    private int availableCapacity;
    private int loadAfter;
    private int fillPercentAfter;
    private int capacityPercent;

    private String truckId;
    private String truckType;
    private Integer truckCapacity;
    private String driverId;
    private String driverName;

    private String status;
    private String readiness;
    private String readinessReason;
    private String departureTime;
    private String routeEta;
    private boolean etaKnown;
    private String orderEta;
    private String travelTime;
    private double travelHours;

    private boolean needsDriver;
    private boolean needsTruck;

    private int score;
    private List<String> reasons = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private List<ScoreFactor> scoreBreakdown = new ArrayList<>();
    private String aiReason;

    public RouteRecommendation() {
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(String warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getWarehouseName() {
        return warehouseName;
    }

    public void setWarehouseName(String warehouseName) {
        this.warehouseName = warehouseName;
    }

    public boolean isCrossWarehouse() {
        return crossWarehouse;
    }

    public void setCrossWarehouse(boolean crossWarehouse) {
        this.crossWarehouse = crossWarehouse;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public List<String> getStops() {
        return stops;
    }

    public void setStops(List<String> stops) {
        this.stops = stops == null ? new ArrayList<>() : stops;
    }

    public List<String> getStopSequence() {
        return stopSequence;
    }

    public void setStopSequence(List<String> stopSequence) {
        this.stopSequence = stopSequence == null ? new ArrayList<>() : stopSequence;
    }

    public String getMatchType() {
        return matchType;
    }

    public void setMatchType(String matchType) {
        this.matchType = matchType;
    }

    public String getMatchLabel() {
        return matchLabel;
    }

    public void setMatchLabel(String matchLabel) {
        this.matchLabel = matchLabel;
    }

    public int getPickupIndex() {
        return pickupIndex;
    }

    public void setPickupIndex(int pickupIndex) {
        this.pickupIndex = pickupIndex;
    }

    public int getDropoffIndex() {
        return dropoffIndex;
    }

    public void setDropoffIndex(int dropoffIndex) {
        this.dropoffIndex = dropoffIndex;
    }

    public String getPickupLabel() {
        return pickupLabel;
    }

    public void setPickupLabel(String pickupLabel) {
        this.pickupLabel = pickupLabel;
    }

    public int getOrderWeight() {
        return orderWeight;
    }

    public void setOrderWeight(int orderWeight) {
        this.orderWeight = orderWeight;
    }

    public int getCurrentWeight() {
        return currentWeight;
    }

    public void setCurrentWeight(int currentWeight) {
        this.currentWeight = currentWeight;
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public int getAvailableCapacity() {
        return availableCapacity;
    }

    public void setAvailableCapacity(int availableCapacity) {
        this.availableCapacity = availableCapacity;
    }

    public int getLoadAfter() {
        return loadAfter;
    }

    public void setLoadAfter(int loadAfter) {
        this.loadAfter = loadAfter;
    }

    public int getFillPercentAfter() {
        return fillPercentAfter;
    }

    public void setFillPercentAfter(int fillPercentAfter) {
        this.fillPercentAfter = fillPercentAfter;
    }

    public int getCapacityPercent() {
        return capacityPercent;
    }

    public void setCapacityPercent(int capacityPercent) {
        this.capacityPercent = capacityPercent;
    }

    public String getTruckId() {
        return truckId;
    }

    public void setTruckId(String truckId) {
        this.truckId = truckId;
    }

    public String getTruckType() {
        return truckType;
    }

    public void setTruckType(String truckType) {
        this.truckType = truckType;
    }

    public Integer getTruckCapacity() {
        return truckCapacity;
    }

    public void setTruckCapacity(Integer truckCapacity) {
        this.truckCapacity = truckCapacity;
    }

    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReadiness() {
        return readiness;
    }

    public void setReadiness(String readiness) {
        this.readiness = readiness;
    }

    public String getReadinessReason() {
        return readinessReason;
    }

    public void setReadinessReason(String readinessReason) {
        this.readinessReason = readinessReason;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(String departureTime) {
        this.departureTime = departureTime;
    }

    public String getRouteEta() {
        return routeEta;
    }

    public void setRouteEta(String routeEta) {
        this.routeEta = routeEta;
    }

    public boolean isEtaKnown() {
        return etaKnown;
    }

    public void setEtaKnown(boolean etaKnown) {
        this.etaKnown = etaKnown;
    }

    public String getOrderEta() {
        return orderEta;
    }

    public void setOrderEta(String orderEta) {
        this.orderEta = orderEta;
    }

    public String getTravelTime() {
        return travelTime;
    }

    public void setTravelTime(String travelTime) {
        this.travelTime = travelTime;
    }

    public double getTravelHours() {
        return travelHours;
    }

    public void setTravelHours(double travelHours) {
        this.travelHours = travelHours;
    }

    public boolean isNeedsDriver() {
        return needsDriver;
    }

    public void setNeedsDriver(boolean needsDriver) {
        this.needsDriver = needsDriver;
    }

    public boolean isNeedsTruck() {
        return needsTruck;
    }

    public void setNeedsTruck(boolean needsTruck) {
        this.needsTruck = needsTruck;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public void setReasons(List<String> reasons) {
        this.reasons = reasons == null ? new ArrayList<>() : reasons;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings == null ? new ArrayList<>() : warnings;
    }

    public List<ScoreFactor> getScoreBreakdown() {
        return scoreBreakdown;
    }

    public void setScoreBreakdown(List<ScoreFactor> scoreBreakdown) {
        this.scoreBreakdown = scoreBreakdown == null ? new ArrayList<>() : scoreBreakdown;
    }

    public String getAiReason() {
        return aiReason;
    }

    public void setAiReason(String aiReason) {
        this.aiReason = aiReason;
    }

    /** One line of the score explanation, shown under "view all details". */
    public static class ScoreFactor {
        private String label;
        private int points;
        private String detail;

        public ScoreFactor() {
        }

        public ScoreFactor(String label, int points, String detail) {
            this.label = label;
            this.points = points;
            this.detail = detail;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public int getPoints() {
            return points;
        }

        public void setPoints(int points) {
            this.points = points;
        }

        public String getDetail() {
            return detail;
        }

        public void setDetail(String detail) {
            this.detail = detail;
        }
    }
}
