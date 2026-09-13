package com.example.linehaul.model;


public final class Status {

    public static final String CREATED = "CREATED";
    public static final String READY = "READY";
    public static final String MANIFESTED = "MANIFESTED";
    public static final String ASSIGNED = "ASSIGNED";
    public static final String DISPATCHED = "DISPATCHED";
    public static final String IN_TRANSIT = "IN_TRANSIT";
    public static final String COMPLETED = "COMPLETED";
    public static final String BLOCKED = "BLOCKED";

    public static final String DRAFT = "DRAFT";

    public static final String AVAILABLE = "AVAILABLE";
    public static final String MAINTENANCE = "MAINTENANCE";

    private Status() {
    }
}
