package com.example.linehaul.automation.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The answers of the model arrive as text, and a broken answer must mean "no answer" - never an
 * exception in the middle of a dispatcher action.
 */
class JsonTextTest {

    @Test
    void readsAnObject() {
        Map<String, Object> parsed = JsonText.parseObject("""
                {"routes":[{"routeId":"LH-1029","reason":"Best fit"}],"score":88,"ok":true,"gone":null}""");

        assertEquals("LH-1029", first(parsed).get("routeId"));
        assertEquals(88L, parsed.get("score"));
        assertEquals(Boolean.TRUE, parsed.get("ok"));
        assertTrue(parsed.containsKey("gone"));
    }

    @Test
    void skipsMarkdownFencesAndProse() {
        Map<String, Object> parsed = JsonText.parseObject("Here you go:\n```json\n{\"a\":\"b\"}\n```");

        assertEquals("b", parsed.get("a"));
    }

    @Test
    void unescapeSimpleSequences() {
        Map<String, Object> parsed = JsonText.parseObject("{\"text\":\"line one\\nsecond\\u0041\"}");

        assertEquals("line one\nsecondA", parsed.get("text"));
    }

    @Test
    void brokenInputIsNotAnAnswer() {
        assertNull(JsonText.parseObject(null));
        assertNull(JsonText.parseObject(""));
        assertNull(JsonText.parseObject("no json here"));
        assertNull(JsonText.parseObject("{\"unclosed\": "));
        assertNull(JsonText.parseObject("[1,2"));
    }

    @Test
    void writesWhatItCanReadBack() {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("model", "gemini-2.5-flash-lite");
        payload.put("temperature", 0);
        payload.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", "he said \"hi\"\nbye")))));
        payload.put("jsonMode", true);
        payload.put("nothing", null);

        String written = JsonText.write(payload);

        assertTrue(written.startsWith("{\"model\":\"gemini-2.5-flash-lite\""), written);
        assertTrue(written.endsWith("\"nothing\":null}"), written);
        Map<String, Object> read = JsonText.parseObject(written);
        assertEquals(Boolean.TRUE, read.get("jsonMode"));
        assertEquals(0L, read.get("temperature"));
        assertNull(read.get("nothing"));
        assertEquals("he said \"hi\"\nbye", firstText(read));
    }

    @Test
    void escapesControlCharacters() {
        String written = JsonText.write(Map.of("text", "a\u0007b"));

        assertEquals("{\"text\":\"a\\u0007b\"}", written);
    }

    @SuppressWarnings("unchecked")
    private static String firstText(Map<String, Object> read) {
        List<Object> contents = (List<Object>) read.get("contents");
        Map<String, Object> turn = (Map<String, Object>) contents.get(0);
        List<Object> parts = (List<Object>) turn.get("parts");
        return String.valueOf(((Map<String, Object>) parts.get(0)).get("text"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> first(Map<String, Object> parsed) {
        List<Object> rows = (List<Object>) parsed.get("routes");
        return (Map<String, Object>) rows.get(0);
    }
}
