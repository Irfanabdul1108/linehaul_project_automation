package com.example.linehaul.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "routes")
public class Route {

    @Id
    private String id;

    @NotBlank(message = "Route ID is required.")
    @Indexed(unique = true)
    private String routeId;

    @NotBlank(message = "Origin is required.")
    private String origin;

    @NotBlank(message = "Destination is required.")
    private String destination;

    private String departureTime = "20:00";

    @PositiveOrZero(message = "Travel duration cannot be negative.")
    private int travelDuration = 9;

    private String eta;

    private String status = "DRAFT";

    private List<String> orderIds = new ArrayList<>();

    private String truckId;

    private String driverId;

    /** Depot that owns (starts) this route. */
    @Indexed
    private String warehouseId;

    /** Intermediate stops in travel order, between the origin and the destination. */
    private List<String> stops = new ArrayList<>();

    @Positive(message = "Maximum capacity must be greater than zero.")
    private int maxCapacity = 10000;

    @PositiveOrZero(message = "Current weight cannot be negative.")
    private int currentWeight = 0;

 
    @Transient
    private int capacityPercent;

    @Transient
    private String readiness = "BLOCKED";

    @Transient
    private String readinessReason = "";

    @Transient
    private boolean hasOrders;

    @Transient
    private boolean hasTruck;

    @Transient
    private boolean hasDriver;

    @Transient
    private boolean capacityOk;

    /** Origin, stops and destination in travel order. Derived, never stored. */
    @Transient
    private List<String> stopSequence = new ArrayList<>();

    /** maxCapacity minus currentWeight. Derived, never stored. */
    @Transient
    private int availableCapacity;

    public Route() {
    }

    public List<String> getStops() {
        return stops;
    }

    public void setStops(List<String> stops) {
        this.stops = stops == null ? new ArrayList<>() : stops;
    }

    public String getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(String warehouseId) {
        this.warehouseId = warehouseId;
    }

    public List<String> getStopSequence() {
        return stopSequence;
    }

    public void setStopSequence(List<String> stopSequence) {
        this.stopSequence = stopSequence == null ? new ArrayList<>() : stopSequence;
    }

    public int getAvailableCapacity() {
        return availableCapacity;
    }

    public void setAvailableCapacity(int availableCapacity) {
        this.availableCapacity = availableCapacity;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
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

    public String getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(String departureTime) {
        this.departureTime = departureTime;
    }

    public int getTravelDuration() {
        return travelDuration;
    }

    public void setTravelDuration(int travelDuration) {
        this.travelDuration = travelDuration;
    }

    public String getEta() {
        return eta;
    }

    public void setEta(String eta) {
        this.eta = eta;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getOrderIds() {
        return orderIds;
    }

    public void setOrderIds(List<String> orderIds) {
        this.orderIds = orderIds == null ? new ArrayList<>() : orderIds;
    }

    public String getTruckId() {
        return truckId;
    }

    public void setTruckId(String truckId) {
        this.truckId = truckId;
    }

    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public int getCurrentWeight() {
        return currentWeight;
    }

    public void setCurrentWeight(int currentWeight) {
        this.currentWeight = currentWeight;
    }

    public int getCapacityPercent() {
        return capacityPercent;
    }

    public void setCapacityPercent(int capacityPercent) {
        this.capacityPercent = capacityPercent;
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

    public boolean isHasOrders() {
        return hasOrders;
    }

    public void setHasOrders(boolean hasOrders) {
        this.hasOrders = hasOrders;
    }

    public boolean isHasTruck() {
        return hasTruck;
    }

    public void setHasTruck(boolean hasTruck) {
        this.hasTruck = hasTruck;
    }

    public boolean isHasDriver() {
        return hasDriver;
    }

    public void setHasDriver(boolean hasDriver) {
        this.hasDriver = hasDriver;
    }

    public boolean isCapacityOk() {
        return capacityOk;
    }

    public void setCapacityOk(boolean capacityOk) {
        this.capacityOk = capacityOk;
    }
}
