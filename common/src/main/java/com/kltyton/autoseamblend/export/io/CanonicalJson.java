package com.kltyton.autoseamblend.export.io;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class CanonicalJson {
    private CanonicalJson() {}

    public static String stringify(Object value) {
        StringBuilder output = new StringBuilder();
        append(output, value, 0);
        output.append('\n');
        return output.toString();
    }

    private static void append(StringBuilder output, Object value, int indent) {
        if (value == null) {
            output.append("null");
        } else if (value instanceof String string) {
            appendString(output, string);
        } else if (value instanceof Number || value instanceof Boolean) {
            output.append(value);
        } else if (value instanceof Map<?, ?> map) {
            appendMap(output, map, indent);
        } else if (value instanceof Iterable<?> iterable) {
            appendIterable(output, iterable, indent);
        } else if (value.getClass().isArray()) {
            ArrayList<Object> values = new ArrayList<>();
            for (int index = 0; index < Array.getLength(value); index++) values.add(Array.get(value, index));
            appendIterable(output, values, indent);
        } else {
            appendString(output, value.toString());
        }
    }

    private static void appendMap(StringBuilder output, Map<?, ?> map, int indent) {
        List<Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
        entries.sort(Comparator.comparing(entry -> entry.getKey().toString()));
        output.append('{');
        if (!entries.isEmpty()) output.append('\n');
        for (int index = 0; index < entries.size(); index++) {
            Map.Entry<?, ?> entry = entries.get(index);
            indent(output, indent + 2);
            appendString(output, entry.getKey().toString());
            output.append(": ");
            append(output, entry.getValue(), indent + 2);
            if (index + 1 < entries.size()) output.append(',');
            output.append('\n');
        }
        if (!entries.isEmpty()) indent(output, indent);
        output.append('}');
    }

    private static void appendIterable(StringBuilder output, Iterable<?> iterable, int indent) {
        ArrayList<Object> values = new ArrayList<>();
        iterable.forEach(values::add);
        output.append('[');
        if (!values.isEmpty()) output.append('\n');
        for (int index = 0; index < values.size(); index++) {
            indent(output, indent + 2);
            append(output, values.get(index), indent + 2);
            if (index + 1 < values.size()) output.append(',');
            output.append('\n');
        }
        if (!values.isEmpty()) indent(output, indent);
        output.append(']');
    }

    private static void appendString(StringBuilder output, String value) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20) output.append(String.format("\\u%04x", (int) character));
                    else output.append(character);
                }
            }
        }
        output.append('"');
    }

    private static void indent(StringBuilder output, int count) {
        output.append(" ".repeat(count));
    }
}
