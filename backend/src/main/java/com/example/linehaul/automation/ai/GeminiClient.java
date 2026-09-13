package com.example.linehaul.automation.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Talks to the Google Gemini REST API:
 * {@code POST {base-url}/models/{model}:generateContent?key=...}.
 *
 * <p>Two deliberate choices make this class boring on purpose:</p>
 * <ul>
 *   <li>it uses only the JDK HTTP client and this project's own {@link JsonText} codec, so the AI layer
 *       adds no dependency and no coupling to a particular JSON library;</li>
 *   <li>nothing here can fail into a request thread. Missing key, unreachable network, refused quota,
 *       truncated answer, unexpected shape - all of them end as {@link Optional#empty()}, and the
 *       caller keeps the deterministic result it already computed. The application therefore works
 *       exactly the same with no key at all, only without the model's prose.</li>
 * </ul>
 *
 * <p>A small guard protects the free tier: at most {@code maxCallsPerMinute} calls per minute, and a
 * cooling-off period after repeated failures, so a quota problem never turns into a slow UI.</p>
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final String FALLBACK_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final String FALLBACK_MODEL = "gemini-2.5-flash-lite";

    private final HttpClient http;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final Duration timeout;
    private final int maxCallsPerMinute;
    private final int failureCooldownSeconds;

    private final Deque<Long> recentCalls = new ArrayDeque<>();
    private volatile long cooldownUntilEpochMs;
    private volatile int consecutiveFailures;

    public GeminiClient(@Value("${app.gemini.api-key:}") String apiKey,
                        @Value("${app.gemini.model:gemini-2.5-flash-lite}") String model,
                        @Value("${app.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
                        @Value("${app.gemini.timeout-seconds:12}") int timeoutSeconds,
                        @Value("${app.gemini.max-calls-per-minute:10}") int maxCallsPerMinute,
                        @Value("${app.gemini.failure-cooldown-seconds:60}") int failureCooldownSeconds) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? FALLBACK_MODEL : model.trim();
        String givenBase = baseUrl == null || baseUrl.isBlank() ? FALLBACK_BASE_URL : baseUrl.trim();
        this.baseUrl = givenBase.endsWith("/") ? givenBase.substring(0, givenBase.length() - 1) : givenBase;
        this.timeout = Duration.ofSeconds(Math.max(2, timeoutSeconds));
        this.maxCallsPerMinute = Math.max(1, maxCallsPerMinute);
        this.failureCooldownSeconds = Math.max(5, failureCooldownSeconds);
        this.http = HttpClient.newBuilder().connectTimeout(this.timeout).build();

        if (this.apiKey.isEmpty()) {
            log.info("Gemini is not configured (GEMINI_API_KEY is empty). The AI layer stays off and the "
                    + "deterministic engine answers on its own.");
        } else {
            log.info("Gemini AI layer enabled with model {}", this.model);
        }
    }

    public boolean isEnabled() {
        return !apiKey.isEmpty();
    }

    public String getModel() {
        return model;
    }

    /**
     * One generation call.
     *
     * @param jsonMode when true the model is told to answer with a JSON object only
     */
    public Optional<String> generate(String systemInstruction, String prompt, boolean jsonMode) {
        Map<String, Object> request = baseRequest(systemInstruction, prompt, jsonMode);
        return call(request).map(GeminiClient::textOf).filter(text -> !text.isBlank());
    }

    /**
     * A short tool-calling conversation: the model may ask for data several times; every request is
     * answered by {@code tools} (read-only) before a final text answer is produced.
     */
    public Optional<String> generateWithTools(String systemInstruction, String prompt,
                                              List<Map<String, Object>> toolDeclarations,
                                              ToolRunner tools, int maxTurns) {
        List<Map<String, Object>> contents = new ArrayList<>();
        contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", prompt))));

        for (int turn = 0; turn < Math.max(1, maxTurns); turn++) {
            Map<String, Object> request = baseRequest(systemInstruction, null, false);
            request.put("contents", contents);
            request.put("tools", List.of(Map.of("functionDeclarations", toolDeclarations)));
            request.put("toolConfig", Map.of("functionCallingConfig", Map.of("mode", "AUTO")));

            Optional<Map<String, Object>> answer = call(request);
            if (answer.isEmpty()) {
                return Optional.empty();
            }
            List<Map<String, Object>> parts = partsOf(answer.get());
            List<Map<String, Object>> calls = functionCalls(parts);
            String text = joinText(parts);
            if (calls.isEmpty()) {
                return text.isBlank() ? Optional.empty() : Optional.of(text);
            }

            contents.add(Map.of("role", "model", "parts", parts));
            List<Map<String, Object>> responses = new ArrayList<>();
            for (Map<String, Object> call : calls) {
                String name = String.valueOf(call.getOrDefault("name", ""));
                Map<String, Object> arguments = call.get("args") instanceof Map<?, ?> map ? copyOfMap(map) : Map.of();
                Object result;
                try {
                    result = tools.run(name, arguments);
                } catch (Exception toolProblem) {
                    log.debug("AI tool {} failed: {}", name, toolProblem.getMessage());
                    result = Map.of("error", "this tool could not be run");
                }
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("name", name);
                payload.put("response", Map.of("result", result == null ? Map.of("error", "no data") : result));
                responses.add(Map.of("functionResponse", payload));
            }
            contents.add(Map.of("role", "user", "parts", responses));
        }
        log.debug("Gemini reached the tool-turn limit without a final answer");
        return Optional.empty();
    }

    /** POSTs one request body and hands back the first candidate's {@code content} node. */
    private Optional<Map<String, Object>> call(Map<String, Object> body) {
        if (!isEnabled() || !tryAcquireQuota()) {
            return Optional.empty();
        }
        long started = System.nanoTime();
        try {
            // the key travels in the query string, exactly as the Gemini REST reference documents it
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/models/" + model + ":generateContent?key=" + apiKey))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JsonText.write(body)))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                onFailure("HTTP " + response.statusCode() + " " + brief(response.body()));
                return Optional.empty();
            }
            Map<String, Object> parsed = JsonText.parseObject(response.body());
            if (parsed == null) {
                onFailure("a response that was not JSON");
                return Optional.empty();
            }
            Object candidates = parsed.get("candidates");
            if (!(candidates instanceof List<?> rows) || rows.isEmpty() || !(rows.get(0) instanceof Map<?, ?> first)) {
                onFailure(String.valueOf(parsed.getOrDefault("promptFeedback", "no candidate was returned")));
                return Optional.empty();
            }
            if (!(first.get("content") instanceof Map<?, ?> content)) {
                onFailure("no content in the first candidate (finishReason=" + first.get("finishReason") + ")");
                return Optional.empty();
            }
            onSuccess();
            log.debug("Gemini answered in {} ms", (System.nanoTime() - started) / 1_000_000);
            return Optional.of(copyOfMap(content));
        } catch (Exception problem) {
            onFailure(problem.getClass().getSimpleName() + ": " + safeMessage(problem));
            return Optional.empty();
        }
    }

    private Map<String, Object> baseRequest(String systemInstruction, String prompt, boolean jsonMode) {
        Map<String, Object> request = new LinkedHashMap<>();
        if (systemInstruction != null && !systemInstruction.isBlank()) {
            request.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))));
        }
        if (prompt != null) {
            request.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))));
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("temperature", 0);
        config.put("topP", 1);
        config.put("maxOutputTokens", 1200);
        if (jsonMode) {
            config.put("responseMimeType", "application/json");
        }
        request.put("generationConfig", config);
        return request;
    }

    /**
     * Free-tier friendly guard: a sliding one-minute window plus a cooldown after repeated failures.
     * Calls that do not fit the window are simply not made - the caller falls back silently.
     */
    private synchronized boolean tryAcquireQuota() {
        long now = System.currentTimeMillis();
        if (now < cooldownUntilEpochMs) {
            return false;
        }
        while (!recentCalls.isEmpty() && now - recentCalls.peekFirst() > Duration.ofMinutes(1).toMillis()) {
            recentCalls.pollFirst();
        }
        if (recentCalls.size() >= maxCallsPerMinute) {
            log.debug("Gemini call skipped: {} call(s) in the last minute already", recentCalls.size());
            return false;
        }
        recentCalls.addLast(now);
        return true;
    }

    private void onSuccess() {
        consecutiveFailures = 0;
    }

    private void onFailure(String detail) {
        consecutiveFailures++;
        log.warn("Gemini call failed ({}). The application keeps working with the deterministic engine.", detail);
        if (consecutiveFailures >= 2) {
            cooldownUntilEpochMs = System.currentTimeMillis() + Duration.ofSeconds(failureCooldownSeconds).toMillis();
            consecutiveFailures = 0;
            log.info("The AI layer will rest for {} s before trying again.", failureCooldownSeconds);
        }
    }

    private static String brief(String body) {
        if (body == null) {
            return "";
        }
        String flat = body.replaceAll("\\s+", " ").trim();
        return flat.length() > 180 ? flat.substring(0, 180) + "..." : flat;
    }

    private static String safeMessage(Exception problem) {
        String message = problem.getMessage();
        if (message == null) {
            return "no message";
        }
        return message.replaceAll("AIza[0-9A-Za-z_-]{6,}", "AIza***");
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> partsOf(Map<String, Object> content) {
        Object parts = content.get("parts");
        List<Map<String, Object>> clean = new ArrayList<>();
        if (parts instanceof List<?> rows) {
            for (Object row : rows) {
                if (row instanceof Map<?, ?> map) {
                    clean.add((Map<String, Object>) map);
                }
            }
        }
        return clean;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> functionCalls(List<Map<String, Object>> parts) {
        List<Map<String, Object>> calls = new ArrayList<>();
        for (Map<String, Object> part : parts) {
            if (part.get("functionCall") instanceof Map<?, ?> map) {
                calls.add((Map<String, Object>) map);
            }
        }
        return calls;
    }

    private static String joinText(List<Map<String, Object>> parts) {
        StringBuilder text = new StringBuilder();
        for (Map<String, Object> part : parts) {
            Object value = part.get("text");
            if (value != null) {
                text.append(value);
            }
        }
        return text.toString().trim();
    }

    private static String textOf(Map<String, Object> content) {
        return joinText(partsOf(content));
    }

    private static Map<String, Object> copyOfMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copy;
    }

    /** A Gemini {@code functionDeclarations} entry for one read-only tool. */
    public static Map<String, Object> function(String name, String description,
                                                Map<String, Object> properties, List<String> required) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "OBJECT");
        parameters.put("properties", properties == null ? Map.of() : properties);
        if (required != null && !required.isEmpty()) {
            parameters.put("required", required);
        }
        Map<String, Object> declaration = new LinkedHashMap<>();
        declaration.put("name", name);
        declaration.put("description", description);
        declaration.put("parameters", parameters);
        return declaration;
    }

    /** A JSON-schema property entry for a tool parameter. */
    public static Map<String, Object> property(String type, String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    /** Invoked for every function call the model makes; must only ever read. */
    public interface ToolRunner {
        Object run(String name, Map<String, Object> arguments);
    }
}
