package com.example.linehaul.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the "Assign Order" panel shows: the order, the pipeline the engine ran, the ranked
 * candidates, the routes it rejected with reasons, and what to do when nothing fits.
 */
public class AssignmentAdvice {

    private String orderId;
    private String customer;
    private String origin;
    private String destination;
    private int weight;
    private int pieces;
    private String serviceDate;
    private String status;
    private String warehouseId;
    private String warehouseName;
    private String warehouseCity;

    private boolean alreadyAssigned;
    private String currentRouteId;

    /** The top candidates, best first. Never more than three. */
    private List<RouteRecommendation> candidates = new ArrayList<>();
    /** Routes that were considered but filtered out, with the stage that removed them. */
    private List<RouteRejection> rejected = new ArrayList<>();
    /** The filter pipeline, so the UI can show what actually happened. */
    private List<PipelineStep> steps = new ArrayList<>();

    private int routesConsidered;
    private int eligibleCount;

    private boolean newRouteRequired;
    private NewRouteAdvice newRoute;

    private String headline;
    private String message;
    private String aiReasoning;
    private boolean aiUsed;
    private String aiStatus = "disabled";
    private String generatedAt;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCustomer() {
        return customer;
    }

    public void setCustomer(String customer) {
        this.customer = customer;
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

    public int getPieces() {
        return pieces;
    }

    public void setPieces(int pieces) {
        this.pieces = pieces;
    }

    public String getServiceDate() {
        return serviceDate;
    }

    public void setServiceDate(String serviceDate) {
        this.serviceDate = serviceDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getWarehouseCity() {
        return warehouseCity;
    }

    public void setWarehouseCity(String warehouseCity) {
        this.warehouseCity = warehouseCity;
    }

    public boolean isAlreadyAssigned() {
        return alreadyAssigned;
    }

    public void setAlreadyAssigned(boolean alreadyAssigned) {
        this.alreadyAssigned = alreadyAssigned;
    }

    public String getCurrentRouteId() {
        return currentRouteId;
    }

    public void setCurrentRouteId(String currentRouteId) {
        this.currentRouteId = currentRouteId;
    }

    public List<RouteRecommendation> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<RouteRecommendation> candidates) {
        this.candidates = candidates == null ? new ArrayList<>() : candidates;
    }

    public List<RouteRejection> getRejected() {
        return rejected;
    }

    public void setRejected(List<RouteRejection> rejected) {
        this.rejected = rejected == null ? new ArrayList<>() : rejected;
    }

    public List<PipelineStep> getSteps() {
        return steps;
    }

    public void setSteps(List<PipelineStep> steps) {
        this.steps = steps == null ? new ArrayList<>() : steps;
    }

    public int getRoutesConsidered() {
        return routesConsidered;
    }

    public void setRoutesConsidered(int routesConsidered) {
        this.routesConsidered = routesConsidered;
    }

    public int getEligibleCount() {
        return eligibleCount;
    }

    public void setEligibleCount(int eligibleCount) {
        this.eligibleCount = eligibleCount;
    }

    public boolean isNewRouteRequired() {
        return newRouteRequired;
    }

    public void setNewRouteRequired(boolean newRouteRequired) {
        this.newRouteRequired = newRouteRequired;
    }

    public NewRouteAdvice getNewRoute() {
        return newRoute;
    }

    public void setNewRoute(NewRouteAdvice newRoute) {
        this.newRoute = newRoute;
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAiReasoning() {
        return aiReasoning;
    }

    public void setAiReasoning(String aiReasoning) {
        this.aiReasoning = aiReasoning;
    }

    public boolean isAiUsed() {
        return aiUsed;
    }

    public void setAiUsed(boolean aiUsed) {
        this.aiUsed = aiUsed;
    }

    public String getAiStatus() {
        return aiStatus;
    }

    public void setAiStatus(String aiStatus) {
        this.aiStatus = aiStatus;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
    }

    public RouteRecommendation best() {
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /** Appends one line to the pipeline trace the UI renders as a check list. */
    public void addStep(PipelineStep step) {
        if (step != null) {
            this.steps.add(step);
        }
    }
}
