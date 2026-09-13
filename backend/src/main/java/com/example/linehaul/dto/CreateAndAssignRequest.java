package com.example.linehaul.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of "create a new route for this order and put the order on it". */
public class CreateAndAssignRequest {

    @NotBlank(message = "An order must be selected.")
    private String orderId;

    private String warehouseId;

    @NotBlank(message = "Select a driver from this warehouse.")
    private String driverId;

    @NotBlank(message = "Select a truck from this warehouse.")
    private String truckId;

    private Integer maxCapacity;

    private Integer travelDuration;

    private String departureTime;

    private String note;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(String warehouseId) {
        this.warehouseId = warehouseId;
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

    public Integer getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(Integer maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public Integer getTravelDuration() {
        return travelDuration;
    }

    public void setTravelDuration(Integer travelDuration) {
        this.travelDuration = travelDuration;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(String departureTime) {
        this.departureTime = departureTime;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
