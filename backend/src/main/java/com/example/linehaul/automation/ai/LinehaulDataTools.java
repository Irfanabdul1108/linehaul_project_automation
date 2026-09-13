package com.example.linehaul.automation.ai;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The only way the AI layer can look at MongoDB.
 *
 * <p>It is a <b>read-only</b> window onto the real collections, closed down on purpose:</p>
 * <ul>
 *   <li>only the five Linehaul collections can be read, by name, from a fixed allow-list;</li>
 *   <li>only known fields may be filtered on or returned, so no document ever leaves in full;</li>
 *   <li>filters are equality only - no aggregation, no script expressions, no writing of any kind
 *       (there is not even an insert, update or delete method in this class);</li>
 *   <li>the result size is capped, so a prompt can never grow into "send me the whole database".</li>
 * </ul>
 *
 * <p>The model calls these tools to ground its answers in current data; the assignment itself is
 * always performed by {@code AssignmentService}, never by an AI tool.</p>
 */
@Component
public class LinehaulDataTools {

    private static final Logger log = LoggerFactory.getLogger(LinehaulDataTools.class);

    private static final int DEFAULT_LIMIT = 15;
    private static final int MAX_LIMIT = 25;

    private static final Map<String, List<String>> FIELDS = new LinkedHashMap<>();

    static {
        FIELDS.put("orders", List.of("orderId", "customer", "origin", "destination", "weight",
                "pieces", "serviceDate", "status", "eta", "routeId", "warehouseId"));
        FIELDS.put("routes", List.of("routeId", "origin", "destination", "stops", "departureTime",
                "travelDuration", "eta", "status", "orderIds", "truckId", "driverId", "warehouseId",
                "maxCapacity", "currentWeight"));
        FIELDS.put("drivers", List.of("driverId", "name", "status", "routeId", "warehouseId"));
        FIELDS.put("vehicles", List.of("truckId", "type", "capacity", "status", "routeId", "warehouseId"));
        FIELDS.put("warehouses", List.of("warehouseId", "name", "code", "hubLocation", "city", "state",
                "description"));
    }

    private final MongoTemplate mongo;

    public LinehaulDataTools(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Function declarations handed to Gemini. Every tool here is a read. */
    public List<Map<String, Object>> declarations() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(GeminiClient.function("list_warehouses",
                "List every warehouse with its hub location and city. No arguments.", null, null));
        tools.add(GeminiClient.function("list_orders", "List freight orders.",
                Map.of("warehouseId", GeminiClient.property("STRING", "optional warehouse id, e.g. WH-A"),
                        "status", GeminiClient.property("STRING", "optional exact status, e.g. READY, ASSIGNED, BLOCKED"),
                        "unassignedOnly", GeminiClient.property("BOOLEAN", "true to only see orders without a route"),
                        "limit", GeminiClient.property("INTEGER", "max rows, 1-" + MAX_LIMIT)),
                null));
        tools.add(GeminiClient.function("list_routes", "List linehaul routes with load, capacity, truck, driver and status.",
                Map.of("warehouseId", GeminiClient.property("STRING", "optional warehouse id; leave empty for the whole network"),
                        "status", GeminiClient.property("STRING", "optional exact status, e.g. DRAFT, READY, BLOCKED, DISPATCHED"),
                        "destination", GeminiClient.property("STRING", "optional destination lane name"),
                        "limit", GeminiClient.property("INTEGER", "max rows, 1-" + MAX_LIMIT)),
                null));
        tools.add(GeminiClient.function("list_drivers", "List drivers and whether they are free.",
                Map.of("warehouseId", GeminiClient.property("STRING", "optional warehouse id"),
                        "availableOnly", GeminiClient.property("BOOLEAN", "true to only see free drivers")),
                null));
        tools.add(GeminiClient.function("list_vehicles", "List trucks with type and capacity.",
                Map.of("warehouseId", GeminiClient.property("STRING", "optional warehouse id"),
                        "availableOnly", GeminiClient.property("BOOLEAN", "true to only see free trucks"),
                        "minCapacity", GeminiClient.property("INTEGER", "only trucks that can carry at least this many kg")),
                null));
        tools.add(GeminiClient.function("order_detail", "Read one order by its order id.",
                Map.of("orderId", GeminiClient.property("STRING", "the order id, e.g. LH-2001")),
                List.of("orderId")));
        tools.add(GeminiClient.function("route_detail", "Read one route by its route id, including its orders.",
                Map.of("routeId", GeminiClient.property("STRING", "the route id, e.g. LH-1029")),
                List.of("routeId")));
        tools.add(GeminiClient.function("warehouse_summary", "Counts for one warehouse: orders, unassigned orders, routes, free trucks and free drivers.",
                Map.of("warehouseId", GeminiClient.property("STRING", "the warehouse id, e.g. WH-A")),
                List.of("warehouseId")));
        tools.add(GeminiClient.function("read_records",
                        "Generic read-only lookup on one collection with equality filters. Only the collections and fields "
                                + "the tool description lists are allowed.",
                Map.of("collection", GeminiClient.property("STRING", "one of " + FIELDS.keySet()),
                        "field", GeminiClient.property("STRING", "field to filter on, optional"),
                        "value", GeminiClient.property("STRING", "value the field must equal, optional"),
                        "limit", GeminiClient.property("INTEGER", "max rows, 1-" + MAX_LIMIT)),
                List.of("collection")));
        return tools;
    }

    /** Executes one tool call. Never throws: a bad call returns an error object the model can read. */
    public Object run(String name, Map<String, Object> arguments) {
        try {
            return switch (name == null ? "" : name) {
                case "list_warehouses" -> read("warehouses", List.of(), 0);
                case "list_orders" -> orders(arguments);
                case "list_routes" -> routes(arguments);
                case "list_drivers" -> drivers(arguments);
                case "list_vehicles" -> vehicles(arguments);
                case "order_detail" -> one("orders", "orderId", text(arguments, "orderId"));
                case "route_detail" -> one("routes", "routeId", text(arguments, "routeId"));
                case "warehouse_summary" -> summary(text(arguments, "warehouseId"));
                case "read_records" -> generic(arguments);
                default -> error("Unknown tool '" + name + "'. Only read-only Linehaul tools exist.");
            };
        } catch (Exception problem) {
            log.warn("AI tool {} could not run: {}", name, problem.getMessage());
            return error("The data could not be read for this request.");
        }
    }

    private Object orders(Map<String, Object> args) throws Exception {
        List<Bson> filters = new ArrayList<>();
        String warehouse = text(args, "warehouseId");
        if (!warehouse.isEmpty()) {
            filters.add(Filters.eq("warehouseId", warehouse.toUpperCase(Locale.ROOT)));
        }
        String status = text(args, "status");
        if (!status.isEmpty()) {
            filters.add(caseInsensitive("status", status.toUpperCase(Locale.ROOT)));
        }
        if (bool(args, "unassignedOnly")) {
            filters.add(Filters.or(Filters.eq("routeId", null), Filters.exists("routeId", false),
                    Filters.eq("routeId", "")));
        }
        return read("orders", filters, limit(args));
    }

    private Object routes(Map<String, Object> args) throws Exception {
        List<Bson> filters = new ArrayList<>();
        String warehouse = text(args, "warehouseId");
        if (!warehouse.isEmpty()) {
            filters.add(Filters.eq("warehouseId", warehouse.toUpperCase(Locale.ROOT)));
        }
        String status = text(args, "status");
        if (!status.isEmpty()) {
            filters.add(caseInsensitive("status", status.toUpperCase(Locale.ROOT)));
        }
        String destination = text(args, "destination");
        if (!destination.isEmpty()) {
            filters.add(caseInsensitive("destination", destination));
        }
        return read("routes", filters, limit(args));
    }

    private Object drivers(Map<String, Object> args) throws Exception {
        List<Bson> filters = new ArrayList<>();
        String warehouse = text(args, "warehouseId");
        if (!warehouse.isEmpty()) {
            filters.add(Filters.eq("warehouseId", warehouse.toUpperCase(Locale.ROOT)));
        }
        if (bool(args, "availableOnly")) {
            filters.add(caseInsensitive("status", "AVAILABLE"));
        }
        return read("drivers", filters, limit(args));
    }

    private Object vehicles(Map<String, Object> args) throws Exception {
        List<Bson> filters = new ArrayList<>();
        String warehouse = text(args, "warehouseId");
        if (!warehouse.isEmpty()) {
            filters.add(Filters.eq("warehouseId", warehouse.toUpperCase(Locale.ROOT)));
        }
        if (bool(args, "availableOnly")) {
            filters.add(caseInsensitive("status", "AVAILABLE"));
        }
        int minCapacity = integer(args, "minCapacity");
        if (minCapacity > 0) {
            filters.add(Filters.gte("capacity", minCapacity));
        }
        return read("vehicles", filters, limit(args));
    }

    private Object generic(Map<String, Object> args) throws Exception {
        String collection = text(args, "collection").toLowerCase(Locale.ROOT);
        if (!FIELDS.containsKey(collection)) {
            return error("Collection '" + collection + "' cannot be read. Allowed: " + FIELDS.keySet());
        }
        List<Bson> filters = new ArrayList<>();
        String field = text(args, "field");
        String value = text(args, "value");
        if (!field.isEmpty() && !value.isEmpty()) {
            if (!FIELDS.get(collection).contains(field)) {
                return error("Field '" + field + "' cannot be filtered on for " + collection + ".");
            }
            filters.add(caseInsensitive(field, value));
        }
        return read(collection, filters, limit(args));
    }

    private Object one(String collection, String idField, String id) throws Exception {
        if (id == null || id.isBlank()) {
            return error("An id is required for this tool.");
        }
        List<Map<String, Object>> rows = read(collection, List.of(caseInsensitive(idField, id.trim())), 1);
        if (rows.isEmpty()) {
            return Map.of("error", "No " + collection + " record with " + idField + " = " + id);
        }
        return rows.get(0);
    }

    private Object summary(String warehouseId) throws Exception {
        String depot = warehouseId == null ? "" : warehouseId.trim().toUpperCase(Locale.ROOT);
        Bson scope = depot.isEmpty() ? new Document() : Filters.eq("warehouseId", depot);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("warehouseId", depot.isEmpty() ? "ALL" : depot);
        result.put("orders", mongo.getCollection("orders").countDocuments(scope));
        result.put("unassignedOrders", mongo.getCollection("orders").countDocuments(
                Filters.and(scope, Filters.or(Filters.eq("routeId", null), Filters.exists("routeId", false),
                        Filters.eq("routeId", "")))));
        result.put("routes", mongo.getCollection("routes").countDocuments(scope));
        result.put("drivers", mongo.getCollection("drivers").countDocuments(
                depot.isEmpty() ? caseInsensitive("status", "AVAILABLE")
                        : Filters.and(scope, caseInsensitive("status", "AVAILABLE"))));
        result.put("vehicles", mongo.getCollection("vehicles").countDocuments(
                depot.isEmpty() ? caseInsensitive("status", "AVAILABLE")
                        : Filters.and(scope, caseInsensitive("status", "AVAILABLE"))));
        return result;
    }

    private List<Map<String, Object>> read(String collection, List<Bson> filters, int limit) {
        List<String> allowed = FIELDS.get(collection);
        if (allowed == null) {
            return List.of(Map.of("error", "Collection '" + collection + "' cannot be read."));
        }
        Bson filter = filters.isEmpty() ? new Document() : Filters.and(filters);
        List<Document> documents = new ArrayList<>();
        mongo.getCollection(collection)
                .find(filter)
                .projection(Projections.include(allowed))
                .limit(limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT))
                .into(documents);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Document document : documents) {
            rows.add(clean(document, allowed));
        }
        return rows;
    }

    private Map<String, Object> clean(Document document, List<String> allowed) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (String field : allowed) {
            Object value = document.get(field);
            if (value == null) {
                continue;
            }
            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                row.put(field, value);
            } else if (value instanceof List<?> list) {
                List<Object> scalars = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof String || item instanceof Number || item instanceof Boolean) {
                        scalars.add(item);
                    } else if (item != null) {
                        scalars.add(String.valueOf(item));
                    }
                }
                row.put(field, scalars);
            } else {
                row.put(field, String.valueOf(value));
            }
        }
        return row;
    }

    /** Case-insensitive equality, because ids such as {@code lh-1029} are typed by hand. */
    private Bson caseInsensitive(String field, String value) {
        if (value == null || value.isEmpty()) {
            return Filters.eq(field, value);
        }
        return Filters.regex(field, "^" + Pattern.quote(value) + "$", "i");
    }

    private int limit(Map<String, Object> args) {
        int wanted = integer(args, "limit");
        if (wanted <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(wanted, MAX_LIMIT);
    }

    private static String text(Map<String, Object> args, String key) {
        Object value = args == null ? null : args.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean bool(Map<String, Object> args, String key) {
        Object value = args == null ? null : args.get(key);
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value != null && "true".equalsIgnoreCase(String.valueOf(value).trim());
    }

    private static int integer(Map<String, Object> args, String key) {
        Object value = args == null ? null : args.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", message);
        return payload;
    }
}
