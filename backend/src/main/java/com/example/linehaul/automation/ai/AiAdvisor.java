package com.example.linehaul.automation.ai;

import com.example.linehaul.dto.BatchAssignmentReport;
import com.example.linehaul.dto.RouteRecommendation;
import com.example.linehaul.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The reasoning layer. It asks Gemini to order and explain the candidates the backend has already
 * validated, and to answer dispatcher questions from live data.
 *
 * <p>Two rules shape everything below:</p>
 * <ol>
 *   <li><b>the model never talks to MongoDB directly</b> - it may only call the read-only tools in
 *       {@link LinehaulDataTools};</li>
 *   <li><b>the model never decides what is possible</b> - eligibility, capacity and the write itself
 *       stay in {@code RouteMatchingService} / {@code AssignmentService}. Anything the model returns
 *       that is not in the candidate list is thrown away.</li>
 * </ol>
 */
@Service
public class AiAdvisor {

    private static final Logger log = LoggerFactory.getLogger(AiAdvisor.class);

    private static final String RANK_SYSTEM = """
            You are the linehaul planning assistant of a freight dispatcher.
            You are given ONE unassigned order and a list of routes that a deterministic engine has
            already validated: every route listed reaches the order destination, passes a valid loading
            stop for the order's warehouse, and has room for the weight.
            Your job is only to order these routes and explain each choice in one short sentence.
            Rules you must follow:
            - Refer to routes by the exact routeId given. Never invent a routeId, driver, truck, capacity,
              ETA, warehouse or order id.
            - Do not change or question the capacity figures, they come from the database.
            - Prefer an exact lane match, then plenty of remaining capacity, then a fast arrival.
            - A cross-warehouse route is fine, but say that the order is loaded at the intermediate stop.
            - If a route carries a warning, mention the risk in the "risk" field.
            Answer with JSON only, in exactly this shape:
            {"routes":[{"routeId":"...","reason":"...","risk":"..."}],"summary":"one sentence for the dispatcher"}
            """;

    private static final String BATCH_SYSTEM = """
            You are the linehaul planning assistant. You receive the outcome of an automatic assignment
            run that a deterministic engine already applied to the database.
            Summarise it for a dispatcher in at most three short sentences: what was assigned, why the
            rest was not, and what the dispatcher should do next. Never invent order ids, route ids or
            numbers that are not in the data you received. Answer with plain text only.
            """;

    private static final String CHAT_SYSTEM = """
            You are the Linehaul assistant. You answer questions about the live linehaul data of a
            freight operation: orders, routes, drivers, trucks and warehouses.
            You have read-only tools that query the real database. Always call a tool before stating a
            fact about orders, routes, drivers, trucks or warehouses - never answer from memory and
            never invent an id, name, number, capacity or time.
            If the data does not contain the answer, say exactly what is missing instead of guessing.
            Keep answers short: plain text, one or two sentences plus a few bullet lines when listing.
            Only report figures the tools returned. You cannot change anything; if the dispatcher asks
            for an assignment, tell them to use the Assign Order button on the order.
            """;

    private final GeminiClient client;

    public AiAdvisor(GeminiClient client) {
        this.client = client;
    }

    public boolean isEnabled() {
        return client.isEnabled();
    }

    public String modelName() {
        return client.getModel();
    }

    /** What the AI layer did for a recommendation, so the UI can be honest about it. */
    public enum Status {
        DISABLED, SKIPPED, APPLIED, UNAVAILABLE
    }

    public static class Ranking {
        private final List<String> orderedRouteIds;
        private final Map<String, String> reasons;
        private final Map<String, String> risks;
        private final String summary;

        public Ranking(List<String> orderedRouteIds, Map<String, String> reasons, Map<String, String> risks, String summary) {
            this.orderedRouteIds = orderedRouteIds;
            this.reasons = reasons;
            this.risks = risks;
            this.summary = summary;
        }

        public List<String> orderedRouteIds() {
            return orderedRouteIds;
        }

        public Map<String, String> reasons() {
            return reasons;
        }

        public Map<String, String> risks() {
            return risks;
        }

        public String summary() {
            return summary;
        }
    }

    /**
     * Asks Gemini to order the given candidates. The result is filtered down to the ids that were
     * offered, so a hallucinated route can never reach the dispatcher.
     */
    public Optional<Ranking> rank(Order order, List<RouteRecommendation> candidates, StatusHolder status) {
        if (!isEnabled() || candidates.size() < 2) {
            status.set(candidates.size() < 2 ? Status.SKIPPED : Status.DISABLED);
            return Optional.empty();
        }

        String answer;
        try {
            answer = client.generate(RANK_SYSTEM, rankPrompt(order, candidates), true).orElse(null);
        } catch (Exception problem) {
            log.debug("Gemini ranking was not available: {}", problem.getMessage());
            status.set(Status.UNAVAILABLE);
            return Optional.empty();
        }
        if (answer == null) {
            status.set(Status.UNAVAILABLE);
            return Optional.empty();
        }

        Map<String, Object> parsed = JsonText.parseObject(answer);
        if (parsed == null) {
            log.debug("Gemini returned an answer that was not valid JSON; keeping the engine ranking");
            status.set(Status.UNAVAILABLE);
            return Optional.empty();
        }

        List<String> allowed = new ArrayList<>();
        for (RouteRecommendation candidate : candidates) {
            allowed.add(candidate.getRouteId());
        }

        List<String> ordered = new ArrayList<>();
        Map<String, String> reasons = new LinkedHashMap<>();
        Map<String, String> risks = new LinkedHashMap<>();
        Object routes = parsed.get("routes");
        if (routes instanceof List<?> rows) {
            for (Object row : rows) {
                if (!(row instanceof Map<?, ?> entry)) {
                    continue;
                }
                String routeId = trim(entry.get("routeId"));
                if (routeId == null || !allowed.contains(routeId) || ordered.contains(routeId)) {
                    continue;
                }
                ordered.add(routeId);
                String reason = trim(entry.get("reason"));
                if (reason != null && !reason.isBlank()) {
                    reasons.put(routeId, reason);
                }
                String risk = trim(entry.get("risk"));
                if (risk != null && !risk.isBlank()) {
                    risks.put(routeId, risk);
                }
            }
        }
        if (ordered.isEmpty()) {
            status.set(Status.UNAVAILABLE);
            return Optional.empty();
        }
        for (String routeId : allowed) {
            if (!ordered.contains(routeId)) {
                ordered.add(routeId);
            }
        }
        status.set(Status.APPLIED);
        String summary = trim(parsed.get("summary"));
        return Optional.of(new Ranking(ordered, reasons, risks, summary));
    }

    private String rankPrompt(Order order, List<RouteRecommendation> candidates) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("ORDER\n");
        prompt.append("id=").append(order.getOrderId())
                .append(", origin=").append(order.getOrigin())
                .append(", destination=").append(order.getDestination())
                .append(", weightKg=").append(order.getWeight())
                .append(", pieces=").append(order.getPieces())
                .append(", serviceDate=").append(order.getServiceDate() == null ? "-" : order.getServiceDate())
                .append(", warehouseId=").append(order.getWarehouseId() == null ? "-" : order.getWarehouseId())
                .append("\n\nCANDIDATE ROUTES (all already validated)\n");
        for (RouteRecommendation candidate : candidates) {
            prompt.append("- routeId=").append(candidate.getRouteId())
                    .append(" | lane=").append(joinLanes(candidate))
                    .append(" | match=").append(candidate.getMatchType())
                    .append(" | warehouse=").append(candidate.getWarehouseName() == null
                            ? candidate.getWarehouseId() : candidate.getWarehouseName())
                    .append(candidate.isCrossWarehouse() ? " (cross-warehouse)" : " (local)")
                    .append(" | loadedKg=").append(candidate.getCurrentWeight())
                    .append(" of ").append(candidate.getMaxCapacity())
                    .append(" | freeKg=").append(candidate.getAvailableCapacity())
                    .append(" | fillAfter=").append(candidate.getFillPercentAfter()).append("%")
                    .append(" | truck=").append(candidate.getTruckId() == null ? "not assigned"
                            : candidate.getTruckId() + " (" + (candidate.getTruckCapacity() == null
                            ? "capacity unknown" : candidate.getTruckCapacity() + " kg") + ")")
                    .append(" | driver=").append(candidate.getDriverId() == null ? "not assigned"
                            : candidate.getDriverId() + " " + (candidate.getDriverName() == null ? "" : candidate.getDriverName()))
                    .append(" | status=").append(candidate.getStatus())
                    .append(" | orderArrival=").append(candidate.isEtaKnown()
                            ? candidate.getOrderEta() + " after " + candidate.getTravelTime() : "unknown")
                    .append(" | engineScore=").append(candidate.getScore());
            if (!candidate.getWarnings().isEmpty()) {
                prompt.append(" | warnings=").append(String.join("; ", candidate.getWarnings()));
            }
            prompt.append('\n');
        }
        return prompt.toString();
    }

    private String joinLanes(RouteRecommendation candidate) {
        List<String> sequence = candidate.getStopSequence();
        if (sequence == null || sequence.isEmpty()) {
            return candidate.getOrigin() + " > " + candidate.getDestination();
        }
        return String.join(" > ", sequence);
    }

    /** Optional commentary on an "Assign All Orders" run. */
    public Optional<String> batchSummary(BatchAssignmentReport report) {
        if (!isEnabled() || report == null || report.getTotalOrders() == 0) {
            return Optional.empty();
        }
        StringBuilder prompt = new StringBuilder();
        prompt.append("RUN FOR ").append(report.getWarehouseName() == null ? report.getWarehouseId()
                : report.getWarehouseName()).append('\n');
        prompt.append("orders=").append(report.getTotalOrders())
                .append(" assigned=").append(report.getAssigned())
                .append(" newRouteRequired=").append(report.getNewRouteRequired())
                .append(" needsReview=").append(report.getNeedsReview()).append('\n');
        for (BatchAssignmentReport.Outcome outcome : report.getOutcomes()) {
            prompt.append("- ").append(outcome.getOrderId())
                    .append(" ").append(outcome.getAction());
            if (outcome.getRouteId() != null) {
                prompt.append(" route=").append(outcome.getRouteId());
            }
            if (outcome.getReason() != null) {
                prompt.append(" reason=").append(outcome.getReason());
            }
            prompt.append('\n');
        }
        return client.generate(BATCH_SYSTEM, prompt.toString(), false)
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .filter(text -> text.length() < 1200);
    }

    /** Answers a dispatcher question with the read-only database tools. */
    public Optional<String> chat(String question, String warehouseHint,
                                 List<Map<String, Object>> declarations,
                                 GeminiClient.ToolRunner tools) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        String prompt = question + (warehouseHint == null || warehouseHint.isBlank()
                ? "" : "\n\nThe dispatcher has selected warehouse " + warehouseHint
                + ". Prefer that warehouse, but say so when you answer for the whole network.");
        return client.generateWithTools(CHAT_SYSTEM, prompt, declarations, tools, 4)
                .map(String::trim)
                .filter(text -> !text.isEmpty());
    }

    private static String trim(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /** Mutable holder so the caller can report what the AI layer did. */
    public static class StatusHolder {
        private Status status = Status.DISABLED;

        public void set(Status status) {
            this.status = status;
        }

        public Status get() {
            return status;
        }

        public String label() {
            return switch (status) {
                case APPLIED -> "gemini";
                case SKIPPED -> "single-candidate";
                case UNAVAILABLE -> "unavailable";
                case DISABLED -> "disabled";
            };
        }
    }
}
