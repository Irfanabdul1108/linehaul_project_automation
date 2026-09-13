package com.example.linehaul.service;

/**
 * Normalises the warehouse key that travels through the API layer.
 *
 * <p>An empty key means "no warehouse selected", which keeps every existing endpoint working
 * exactly as it did before the multi-warehouse support was added.</p>
 */
public final class WarehouseScope {

    private WarehouseScope() {
    }

    public static String key(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? "" : trimmed.toUpperCase();
    }

    public static boolean isActive(String raw) {
        return !key(raw).isEmpty();
    }
}
