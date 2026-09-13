package com.example.linehaul.util;

import com.example.linehaul.util.ParsedQuestion.Action;
import com.example.linehaul.util.ParsedQuestion.Entity;
import com.example.linehaul.util.ParsedQuestion.Focus;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class QuestionParser {

    private static final Pattern ID = Pattern.compile("\\b([a-z]{1,5})-?(\\d{2,6})\\b");

    private static final String[] ROUTE_WORDS = {"route", "lane"};
    private static final String[] ORDER_WORDS = {"order", "shipment"};
    private static final String[] DRIVER_WORDS = {"driver"};
    private static final String[] VEHICLE_WORDS = {"vehicle", "truck", "tractor", "van", "trailer"};
    private static final String[] BIGGEST_WORDS = {"most", "highest", "biggest", "largest", "maximum", "max", "top"};

    private QuestionParser() {
    }

    public static ParsedQuestion parse(String question) {
        String text = normalize(question);
        String id = idOf(text);
        return new ParsedQuestion(entityOf(text), actionOf(text), statusOf(text), focusOf(text, id != null), id, text);
    }

    static String normalize(String question) {
        if (question == null) {
            return "";
        }
        return question.toLowerCase()
                .replaceAll("[^a-z0-9-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    static Entity entityOf(String text) {
        int route = firstIndex(text, ROUTE_WORDS);
        int order = firstIndex(text, ORDER_WORDS);
        int driver = firstIndex(text, DRIVER_WORDS);
        int vehicle = firstIndex(text, VEHICLE_WORDS);

        Entity entity = null;
        int best = -1;
        if (route >= 0) {
            entity = Entity.ROUTE;
            best = route;
        }
        if (order >= 0 && (best == -1 || order < best)) {
            entity = Entity.ORDER;
            best = order;
        }
        if (driver >= 0 && (best == -1 || driver < best)) {
            entity = Entity.DRIVER;
            best = driver;
        }
        if (vehicle >= 0 && (best == -1 || vehicle < best)) {
            entity = Entity.VEHICLE;
        }
        return entity;
    }

    static Action actionOf(String text) {
        if (has(text, "how many", "how much", "count", "total", "number of")) {
            return Action.COUNT;
        }
        if (has(text, "show", "list", "display", "give me", "which", "what are", "who are", "all ")) {
            return Action.LIST;
        }
        return Action.DETAIL;
    }

    static String statusOf(String text) {
        if (has(text, "unassigned", "unallocated")) {
            return "UNASSIGNED";
        }
        if (negated(text) && has(text, "assign", "allocat")) {
            return "UNASSIGNED";
        }
        if (negated(text) && has(text, "route")) {
            return "UNASSIGNED";
        }
        if (negated(text) && has(text, "ready")) {
            return "BLOCKED";
        }
        if (has(text, "available", "free", "unused", "idle", "spare")) {
            return "AVAILABLE";
        }
        if (has(text, "maintenance", "repair", "workshop")) {
            return "MAINTENANCE";
        }
        if (has(text, "active", "running", "on the road")) {
            return "ACTIVE";
        }
        if (has(text, "in transit", "in-transit", "transit", "moving")) {
            return "IN_TRANSIT";
        }
        if (has(text, "dispatched", "dispatch")) {
            return "DISPATCHED";
        }
        if (has(text, "completed", "complete", "delivered", "finished")) {
            return "COMPLETED";
        }
        if (has(text, "blocked", "stuck")) {
            return "BLOCKED";
        }
        if (has(text, "ready")) {
            return "READY";
        }
        if (has(text, "draft")) {
            return "DRAFT";
        }
        if (has(text, "manifested")) {
            return "MANIFESTED";
        }
        if (has(text, "created", "new")) {
            return "CREATED";
        }
        if (has(text, "assigned", "allocated", "busy")) {
            return "ASSIGNED";
        }
        return null;
    }

    static Focus focusOf(String text, boolean hasId) {
        Entity entity = entityOf(text);

        if (entity == null && !hasId) {
            if (hasWord(text, "hi", "hello", "hey", "hiya", "greetings", "thanks", "thank you")
                    || has(text, "good morning", "good afternoon", "good evening")) {
                return Focus.GREETING;
            }
            if (has(text, "help", "what can you", "what can i ask", "options")) {
                return Focus.HELP;
            }
        }
        if (hasId) {
            return idFocusOf(text);
        }
        if (has(text, "summary", "overview", "snapshot")) {
            return Focus.SUMMARY;
        }
        if (has(text, "each route", "per route", "every route", "by route", "route wise")) {
            return Focus.ORDERS_PER_ROUTE;
        }
        if (entity == Entity.ROUTE) {
            if (negated(text) && has(text, DRIVER_WORDS)) {
                return Focus.NO_DRIVER;
            }
            if (negated(text) && has(text, VEHICLE_WORDS)) {
                return Focus.NO_TRUCK;
            }
            if (negated(text) && has(text, ORDER_WORDS)) {
                return Focus.NO_ORDERS;
            }
            if (has(text, BIGGEST_WORDS) && has(text, ORDER_WORDS)) {
                return Focus.MOST_ORDERS;
            }
            if (has(text, BIGGEST_WORDS) && has(text, "capacity", "space", "room", "weight")) {
                return Focus.HIGHEST_CAPACITY;
            }
        }
        return Focus.NONE;
    }

    private static Focus idFocusOf(String text) {
        if (has(text, "eta", "arrive", "arrival", "when")) {
            return Focus.ETA;
        }
        if (has(text, "capacity", "weight", "how full", "space", "room")) {
            return Focus.CAPACITY;
        }
        if (has(text, ORDER_WORDS)) {
            return Focus.ORDERS;
        }
        if (has(text, DRIVER_WORDS) || hasWord(text, "who", "whose")) {
            return Focus.DRIVER;
        }
        if (has(text, VEHICLE_WORDS)) {
            return Focus.TRUCK;
        }
        return Focus.NONE;
    }

    static String idOf(String text) {
        Matcher matcher = ID.matcher(text);
        if (matcher.find()) {
            return (matcher.group(1) + "-" + matcher.group(2)).toUpperCase();
        }
        return null;
    }

    static boolean negated(String text) {
        return has(text, "without", "missing", "lack", "not ", "no ", "none", "don t", "doesn t",
                "dont", "doesnt", "unassigned", "unallocated", "empty");
    }

    private static boolean has(String text, String... words) {
        return firstIndex(text, words) >= 0;
    }

    private static boolean hasWord(String text, String... words) {
        for (String word : words) {
            if (Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private static int firstIndex(String text, String... words) {
        int best = -1;
        for (String word : words) {
            Matcher matcher = Pattern.compile("\\b" + Pattern.quote(word)).matcher(text);
            if (matcher.find() && (best == -1 || matcher.start() < best)) {
                best = matcher.start();
            }
        }
        return best;
    }
}
