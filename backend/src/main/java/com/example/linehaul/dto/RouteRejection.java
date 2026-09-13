package com.example.linehaul.dto;

/** A route that the engine looked at and ruled out, with the exact reason. Failures are never hidden. */
public class RouteRejection {

    private String routeId;
    private String warehouseId;
    private String origin;
    private String destination;
    /** DESTINATION, WAREHOUSE_STOP, STOP_ORDER, ROUTE_STATUS, ROUTE_CAPACITY, VEHICLE_CAPACITY, ETA. */
    private String stage;
    private String reason;

    public RouteRejection() {
    }

    public RouteRejection(String routeId, String warehouseId, String origin, String destination,
                          String stage, String reason) {
        this.routeId = routeId;
        this.warehouseId = warehouseId;
        this.origin = origin;
        this.destination = destination;
        this.stage = stage;
        this.reason = reason;
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

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
