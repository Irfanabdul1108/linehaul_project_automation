package com.example.linehaul.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of "assign this order to that route". The route id is the only mandatory field: driver and
 * truck are only needed when the chosen route does not have them yet.
 */
public class AssignRequest {

    /** Optional: the endpoint in the address is authoritative, this is only cross-checked. */
    private String orderId;

    @NotBlank(message = "A route must be selected.")
    private String routeId;

    private String driverId;

    private String truckId;

    private String warehouseId;

    public AssignRequest() {
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
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

    public String getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(String warehouseId) {
        this.warehouseId = warehouseId;
    }
}
