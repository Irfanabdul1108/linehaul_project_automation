package com.example.linehaul.dto;

import java.util.ArrayList;
import java.util.List;

/** Result of "Assign All Orders": what was assigned, what needs a new route and what needs a person. */
public class BatchAssignmentReport {

    private String warehouseId;
    private String warehouseName;
    private int totalOrders;
    private int assigned;
    private int newRouteRequired;
    private int needsReview;
    private int skipped;
    private String message;
    private String aiSummary;
    private boolean aiUsed;
    private List<Outcome> outcomes = new ArrayList<>();

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

    public int getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(int totalOrders) {
        this.totalOrders = totalOrders;
    }

    public int getAssigned() {
        return assigned;
    }

    public void setAssigned(int assigned) {
        this.assigned = assigned;
    }

    public int getNewRouteRequired() {
        return newRouteRequired;
    }

    public void setNewRouteRequired(int newRouteRequired) {
        this.newRouteRequired = newRouteRequired;
    }

    public int getNeedsReview() {
        return needsReview;
    }

    public void setNeedsReview(int needsReview) {
        this.needsReview = needsReview;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAiSummary() {
        return aiSummary;
    }

    public void setAiSummary(String aiSummary) {
        this.aiSummary = aiSummary;
    }

    public boolean isAiUsed() {
        return aiUsed;
    }

    public void setAiUsed(boolean aiUsed) {
        this.aiUsed = aiUsed;
    }

    public List<Outcome> getOutcomes() {
        return outcomes;
    }

    public void setOutcomes(List<Outcome> outcomes) {
        this.outcomes = outcomes == null ? new ArrayList<>() : outcomes;
    }

    /** ASSIGNED, NEW_ROUTE_REQUIRED or NEEDS_REVIEW. */
    public static class Outcome {
        private String orderId;
        private String action;
        private String routeId;
        private String routeLane;
        private String origin;
        private String destination;
        private int weight;
        private int score;
        private String eta;
        private int availableCapacity;
        private String reason;
        private String driverId;
        private String truckId;

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getRouteId() {
            return routeId;
        }

        public void setRouteId(String routeId) {
            this.routeId = routeId;
        }

        public String getRouteLane() {
            return routeLane;
        }

        public void setRouteLane(String routeLane) {
            this.routeLane = routeLane;
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

        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }

        public String getEta() {
            return eta;
        }

        public void setEta(String eta) {
            this.eta = eta;
        }

        public int getAvailableCapacity() {
            return availableCapacity;
        }

        public void setAvailableCapacity(int availableCapacity) {
            this.availableCapacity = availableCapacity;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public String getDriverId() {
            return driverId;
        }

        public void setDriverId(String driverId) {
            this.driverId = driverId;
        }

        public String getTruckId() {
            return truckId;
        }

        public void setTruckId(String truckId) {
            this.truckId = truckId;
        }
    }
}
