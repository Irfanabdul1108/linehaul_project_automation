package com.example.linehaul.service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class LinehaulUtil {

    public static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("HH:mm");
    public static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("hh:mm a");

    private LinehaulUtil() {
    }

   
    public static String calculateEta(String departureTime, int travelDurationHours) {
        if (departureTime == null || departureTime.isBlank()) {
            return null;
        }
        try {
            LocalTime departure = LocalTime.parse(departureTime.trim(), INPUT);
            LocalTime arrival = departure.plusHours(travelDurationHours);
            String text = arrival.format(DISPLAY);
            long days = travelDurationHours / 24;
            if (travelDurationHours % 24 != 0 && arrival.isBefore(departure)) {
                days += 1;
            }
            if (days == 1) {
                return text + " (+1 day)";
            }
            if (days > 1) {
                return text + " (+" + days + " days)";
            }
            return text;
        } catch (Exception e) {
            return null;
        }
    }

    public static int capacityPercent(int currentWeight, int maxCapacity) {
        if (maxCapacity <= 0) {
            return 0;
        }
        return (int) Math.round((currentWeight * 100.0) / maxCapacity);
    }

    public static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
