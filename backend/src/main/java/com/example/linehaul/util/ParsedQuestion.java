package com.example.linehaul.util;


public record ParsedQuestion(Entity entity, Action action, String status, Focus focus, String id, String text) {

    public enum Entity {
        ROUTE, ORDER, DRIVER, VEHICLE
    }

    public enum Action {
        COUNT, LIST, DETAIL
    }

    public enum Focus {
        NONE,
        GREETING,
        HELP,
        SUMMARY,
        ETA,
        CAPACITY,
        DRIVER,
        TRUCK,
        ORDERS,
        HIGHEST_CAPACITY,
        MOST_ORDERS,
        NO_DRIVER,
        NO_TRUCK,
        NO_ORDERS,
        ORDERS_PER_ROUTE
    }
}
