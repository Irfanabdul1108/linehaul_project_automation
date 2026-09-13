package com.example.linehaul.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Shown when no existing route can take the order. The engine only <em>recommends</em> a new route -
 * nothing is created until the dispatcher confirms with driver and truck.
 */
public class NewRouteAdvice {

    private boolean required;
    private boolean canCreate;
    private String origin;
    private String destination;
    private int requiredCapacity;
    private int suggestedMaxCapacity;
    private int suggestedTravelDuration = 9;

    /** True when {@code suggestedTravelDuration} is the lane's remembered time, not the default. */
    private boolean durationKnown;

    private String suggestedDepartureTime = "20:00";
    private String suggestedRouteId;
    private String message;
    private List<String> missingChecks = new ArrayList<>();
    private List<ResourceOption> drivers = new ArrayList<>();
    private List<ResourceOption> vehicles = new ArrayList<>();

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public boolean isCanCreate() {
        return canCreate;
    }

    public void setCanCreate(boolean canCreate) {
        this.canCreate = canCreate;
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

    public int getRequiredCapacity() {
        return requiredCapacity;
    }

    public void setRequiredCapacity(int requiredCapacity) {
        this.requiredCapacity = requiredCapacity;
    }

    public int getSuggestedMaxCapacity() {
        return suggestedMaxCapacity;
    }

    public void setSuggestedMaxCapacity(int suggestedMaxCapacity) {
        this.suggestedMaxCapacity = suggestedMaxCapacity;
    }

    public int getSuggestedTravelDuration() {
        return suggestedTravelDuration;
    }

    public void setSuggestedTravelDuration(int suggestedTravelDuration) {
        this.suggestedTravelDuration = suggestedTravelDuration;
    }

    public boolean isDurationKnown() {
        return durationKnown;
    }

    public void setDurationKnown(boolean durationKnown) {
        this.durationKnown = durationKnown;
    }

    public String getSuggestedDepartureTime() {
        return suggestedDepartureTime;
    }

    public void setSuggestedDepartureTime(String suggestedDepartureTime) {
        this.suggestedDepartureTime = suggestedDepartureTime;
    }

    public String getSuggestedRouteId() {
        return suggestedRouteId;
    }

    public void setSuggestedRouteId(String suggestedRouteId) {
        this.suggestedRouteId = suggestedRouteId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getMissingChecks() {
        return missingChecks;
    }

    public void setMissingChecks(List<String> missingChecks) {
        this.missingChecks = missingChecks == null ? new ArrayList<>() : missingChecks;
    }

    public List<ResourceOption> getDrivers() {
        return drivers;
    }

    public void setDrivers(List<ResourceOption> drivers) {
        this.drivers = drivers == null ? new ArrayList<>() : drivers;
    }

    public List<ResourceOption> getVehicles() {
        return vehicles;
    }

    public void setVehicles(List<ResourceOption> vehicles) {
        this.vehicles = vehicles == null ? new ArrayList<>() : vehicles;
    }
}
