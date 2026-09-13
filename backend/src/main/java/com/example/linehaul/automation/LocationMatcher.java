package com.example.linehaul.automation;

import com.example.linehaul.model.Warehouse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Compares the places written on orders and routes.
 *
 * <p>Lane names are free text ("Warehouse A", "Hyderabad", "HYD Bay Terminal"), so every
 * comparison goes through {@link #token(String)}: lower case, alphanumerics only, and generic words
 * such as "hub" or "warehouse" dropped. That keeps the matching predictable, and it never guesses:
 * two different places stay different, they are only equal when the remaining words are equal.</p>
 */
public final class LocationMatcher {

    private static final Set<String> NOISE = new LinkedHashSet<>(Arrays.asList(
            "warehouse", "hub", "dc", "depot", "terminal", "yard", "gate", "bay", "the", "of", "and"));

    private LocationMatcher() {
    }

    /** Normalised, comparable form of a lane name. Returns "" for null/blank input. */
    public static String token(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.toLowerCase().replaceAll("[^a-z0-9 ]", " ").trim().replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) {
            return "";
        }
        List<String> kept = new ArrayList<>();
        for (String word : cleaned.split(" ")) {
            if (!NOISE.contains(word)) {
                kept.add(word);
            }
        }
        if (kept.isEmpty()) {
            return cleaned.replace(" ", "");
        }
        return String.join("", kept);
    }

    public static boolean same(String left, String right) {
        String a = token(left);
        String b = token(right);
        return !a.isEmpty() && !b.isEmpty() && a.equals(b);
    }

    /**
     * Every name a warehouse can be referred to on a lane: its id, code, hub location, city and
     * display name. Used to decide whether a route physically passes through that depot.
     */
    public static List<String> namesOf(Warehouse warehouse) {
        if (warehouse == null) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (String candidate : new String[]{warehouse.getHubLocation(), warehouse.getWarehouseId(),
                warehouse.getCode(), warehouse.getCity(), warehouse.getName()}) {
            String cleaned = candidate == null ? "" : candidate.trim();
            if (!cleaned.isEmpty()) {
                names.add(cleaned);
            }
        }
        return new ArrayList<>(names);
    }

    /** True when {@code location} names the given warehouse (or any of its aliases). */
    public static boolean isThisWarehouse(String location, Warehouse warehouse) {
        for (String name : namesOf(warehouse)) {
            if (same(location, name)) {
                return true;
            }
        }
        return false;
    }

    /** First position in the route's stop sequence that matches {@code location}, or -1. */
    public static int indexOf(List<String> sequence, String location) {
        if (sequence == null) {
            return -1;
        }
        for (int i = 0; i < sequence.size(); i++) {
            if (same(sequence.get(i), location)) {
                return i;
            }
        }
        return -1;
    }

    /** First position in the stop sequence that names the given warehouse, or -1. */
    public static int indexOfWarehouse(List<String> sequence, Warehouse warehouse) {
        if (sequence == null || warehouse == null) {
            return -1;
        }
        for (String name : namesOf(warehouse)) {
            int index = indexOf(sequence, name);
            if (index >= 0) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Index of the point where an order travelling to {@code destination} leaves the route: the final
     * stop when the route ends there, otherwise the first intermediate stop with that name.
     *
     * <p>This is the single definition of "this route actually gets the freight there". The smart
     * assignment engine and the manual drag-and-drop both call it, so a route can never be refused by
     * one and accepted by the other.</p>
     *
     * @return the index in the sequence, or -1 when the route never reaches the destination
     */
    public static int unloadIndex(List<String> sequence, String destination) {
        if (sequence == null || sequence.isEmpty() || token(destination).isEmpty()) {
            return -1;
        }
        int last = sequence.size() - 1;
        if (same(sequence.get(last), destination)) {
            return last;
        }
        for (int i = 1; i < last; i++) {
            if (same(sequence.get(i), destination)) {
                return i;
            }
        }
        return -1;
    }

    /** True when the route's stop sequence brings freight to {@code destination}. */
    public static boolean reaches(List<String> sequence, String destination) {
        return unloadIndex(sequence, destination) >= 0;
    }

    public static boolean matchesAny(String location, List<String> candidates) {
        if (candidates == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (same(location, candidate)) {
                return true;
            }
        }
        return false;
    }
}
