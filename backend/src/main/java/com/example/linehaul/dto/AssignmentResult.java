package com.example.linehaul.dto;

import com.example.linehaul.model.Order;
import com.example.linehaul.model.Route;

/** What a successful write returned: the order, the route it now sits on, and a plain message. */
public class AssignmentResult {

    private String message;
    private String orderId;
    private String routeId;
    private boolean newRouteCreated;
    private String driverId;
    private String truckId;
    private Order order;
    private Route route;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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

    public boolean isNewRouteCreated() {
        return newRouteCreated;
    }

    public void setNewRouteCreated(boolean newRouteCreated) {
        this.newRouteCreated = newRouteCreated;
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

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public Route getRoute() {
        return route;
    }

    public void setRoute(Route route) {
        this.route = route;
    }
}
