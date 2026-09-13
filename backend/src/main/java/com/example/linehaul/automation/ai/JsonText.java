package com.example.linehaul.automation.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The JSON codec of the AI layer: {@link #parse(String)} reads an answer the model produced,
 * {@link #write(Object)} renders the request that goes to it.
 *
 * <p>Why is this not a library? So that the AI layer needs no dependency beyond what the project
 * already ships, and so that reading stays <em>lenient</em> where it has to be: models sometimes wrap
 * their JSON in markdown fences or a sentence of prose, and {@link #parse(String)} skips that. A
 * broken answer can only ever mean "no answer" - parse returns {@code null} instead of throwing, so
 * the caller keeps the deterministic result it already had. Writing is the opposite: our own payloads
 * are plain maps, lists, strings, integers and booleans, and they are always well formed.</p>
 */
public final class JsonText {

    /** Renders Map / List / String / Number / Boolean / null as compact JSON. */
    public static String write(Object value) {
        StringBuilder json = new StringBuilder();
        appendValue(json, value);
        return json.toString();
    }

    private static void appendValue(StringBuilder json, Object value) {
        if (value == null) {
            json.append("null");
        } else if (value instanceof Map<?, ?> map) {
            json.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                appendQuoted(json, String.valueOf(entry.getKey()));
                json.append(':');
                appendValue(json, entry.getValue());
            }
            json.append('}');
        } else if (value instanceof Iterable<?> rows) {
            json.append('[');
            boolean first = true;
            for (Object row : rows) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                appendValue(json, row);
            }
            json.append(']');
        } else if (value instanceof Number || value instanceof Boolean) {
            json.append(value);
        } else {
            appendQuoted(json, String.valueOf(value));
        }
    }

    private static void appendQuoted(StringBuilder json, String text) {
        json.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (c < 0x20) {
                        json.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
                }
            }
        }
        json.append('"');
    }

    private final String text;
    private int at;

    private JsonText(String text) {
        this.text = text;
    }

    /** Reads the first JSON object or array in {@code raw}, ignoring markdown fences and prose. */
    public static Object parse(String raw) {
        if (raw == null) {
            return null;
        }
        int start = firstStructure(raw);
        if (start < 0) {
            return null;
        }
        try {
            JsonText reader = new JsonText(raw);
            reader.at = start;
            return reader.value();
        } catch (Exception problem) {
            return null;
        }
    }

    /** Convenience for the common case of an object answer. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String raw) {
        Object parsed = parse(raw);
        return parsed instanceof Map ? (Map<String, Object>) parsed : null;
    }

    private static int firstStructure(String raw) {
        int object = raw.indexOf('{');
        int array = raw.indexOf('[');
        if (object < 0) {
            return array;
        }
        if (array < 0) {
            return object;
        }
        return Math.min(object, array);
    }

    private Object value() {
        skipSpace();
        char c = peek();
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object() {
        Map<String, Object> result = new LinkedHashMap<>();
        expect('{');
        skipSpace();
        if (peek() == '}') {
            at++;
            return result;
        }
        while (true) {
            skipSpace();
            String key = string();
            skipSpace();
            expect(':');
            result.put(key, value());
            skipSpace();
            char next = take();
            if (next == ',') {
                continue;
            }
            if (next == '}') {
                return result;
            }
            throw new IllegalStateException("expected , or }");
        }
    }

    private List<Object> array() {
        List<Object> result = new ArrayList<>();
        expect('[');
        skipSpace();
        if (peek() == ']') {
            at++;
            return result;
        }
        while (true) {
            result.add(value());
            skipSpace();
            char next = take();
            if (next == ',') {
                continue;
            }
            if (next == ']') {
                return result;
            }
            throw new IllegalStateException("expected , or ]");
        }
    }

    private String string() {
        skipSpace();
        expect('"');
        StringBuilder text = new StringBuilder();
        while (at < this.text.length()) {
            char c = this.text.charAt(at++);
            if (c == '"') {
                return text.toString();
            }
            if (c != '\\') {
                text.append(c);
                continue;
            }
            if (at >= this.text.length()) {
                break;
            }
            char escaped = this.text.charAt(at++);
            switch (escaped) {
                case 'n' -> text.append('\n');
                case 't' -> text.append('\t');
                case 'r' -> text.append('\r');
                case 'b' -> text.append('\b');
                case 'f' -> text.append('\f');
                case 'u' -> {
                    if (at + 4 <= this.text.length()) {
                        text.append((char) Integer.parseInt(this.text.substring(at, at + 4), 16));
                        at += 4;
                    }
                }
                default -> text.append(escaped);
            }
        }
        throw new IllegalStateException("unterminated string");
    }

    private Object number() {
        int start = at;
        while (at < text.length() && "+-.eE0123456789".indexOf(text.charAt(at)) >= 0) {
            at++;
        }
        if (start == at) {
            throw new IllegalStateException("not a value");
        }
        String raw = text.substring(start, at);
        try {
            if (raw.contains(".") || raw.contains("e") || raw.contains("E")) {
                return Double.parseDouble(raw);
            }
            return Long.parseLong(raw);
        } catch (NumberFormatException problem) {
            throw new IllegalStateException("bad number " + raw);
        }
    }

    private Object readLiteral(String literal, Object value) {
        if (text.startsWith(literal, at)) {
            at += literal.length();
            return value;
        }
        throw new IllegalStateException("unexpected token at " + at);
    }

    private void skipSpace() {
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
            at++;
        }
    }

    private char peek() {
        if (at >= text.length()) {
            throw new IllegalStateException("unexpected end of JSON");
        }
        return text.charAt(at);
    }

    /** Reads and consumes the next character. */
    private char take() {
        char c = peek();
        at++;
        return c;
    }

    private void expect(char c) {
        if (peek() != c) {
            throw new IllegalStateException("expected " + c + " at " + at);
        }
        at++;
    }
}
