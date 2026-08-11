package com.kltyton.autoseamblend.authoring.document.json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 中文：只在对象成员或其分隔逗号的源文本区间内应用编辑。
 * English: Applies edits only within object-member or separator-comma source spans.
 */
final class JsonObjectPatchEditor {
    private JsonObjectPatchEditor() {}

    static void validateValues(
            Map<String, Optional<String>> values,
            String valueError) throws IOException {
        for (Optional<String> value : values.values()) {
            if (value.isPresent()) {
                JsonSourceDocument.validateValue(value.orElseThrow(), valueError);
            }
        }
    }

    static String patchValues(
            String existing,
            String existingError,
            List<String> targetPath,
            Map<String, Optional<String>> values) throws IOException {
        String patched = existing;
        for (Map.Entry<String, Optional<String>> entry : values.entrySet()) {
            JsonSourceDocument document = JsonSourceDocument.parse(
                    patched, existingError);
            JsonSourceDocument.ObjectSpan object = document.objectAt(
                    document.root(), targetPath);
            if (object == null) {
                throw new IOException(existingError);
            }
            patched = entry.getValue().isEmpty()
                    ? removeMember(document, object, entry.getKey())
                    : setMember(
                            document,
                            object,
                            entry.getKey(),
                            entry.getValue().orElseThrow());
        }
        return patched;
    }

    static String setMember(
            JsonSourceDocument document,
            JsonSourceDocument.ObjectSpan object,
            String key,
            String rawValue) {
        List<JsonSourceDocument.MemberSpan> matches = object.members(key);
        if (matches.isEmpty()) {
            String separator = object.members().isEmpty()
                    ? ""
                    : object.members().get(object.members().size() - 1).commaStart() >= 0 ? "" : ",";
            String member = quote(key) + ":" + rawValue;
            return insert(
                    document.source(),
                    object.trailingTriviaStart(),
                    separator + memberPrefix(document.source(), object) + member);
        }
        ArrayList<Edit> edits = new ArrayList<>(matches.size());
        for (JsonSourceDocument.MemberSpan member : matches) {
            edits.add(new Edit(
                    member.value().start(), member.value().end(), rawValue));
        }
        return apply(document.source(), edits);
    }

    static String removeMember(
            JsonSourceDocument document,
            JsonSourceDocument.ObjectSpan object,
            String key) throws IOException {
        String patched = document.source();
        while (true) {
            JsonSourceDocument current = JsonSourceDocument.parse(
                    patched, document.error());
            JsonSourceDocument.ObjectSpan currentObject = current.objectByStart(
                    object.start());
            if (currentObject == null) {
                return patched;
            }
            JsonSourceDocument.MemberSpan member = currentObject.last(key);
            if (member == null) {
                return patched;
            }
            int memberIndex = currentObject.members().indexOf(member);
            ArrayList<Edit> edits = new ArrayList<>(2);
            edits.add(new Edit(member.keyStart(), member.value().end(), ""));
            if (memberIndex > 0) {
                JsonSourceDocument.MemberSpan previous = currentObject.members()
                        .get(memberIndex - 1);
                edits.add(new Edit(
                        previous.commaStart(), previous.commaEnd(), ""));
            } else if (member.commaStart() >= 0) {
                edits.add(new Edit(member.commaStart(), member.commaEnd(), ""));
            }
            patched = apply(patched, edits);
        }
    }

    private static String memberPrefix(
            String source, JsonSourceDocument.ObjectSpan object) {
        String objectText = source.substring(object.start(), object.end());
        String newline = objectText.contains("\r\n")
                ? "\r\n"
                : objectText.indexOf('\n') >= 0 ? "\n" : "";
        if (!newline.isEmpty()) {
            return newline + memberIndent(source, object);
        }
        if (object.trailingTriviaStart() < object.end() - 1
                || (object.start() + 1 < source.length()
                        && Character.isWhitespace(source.charAt(object.start() + 1)))) {
            return " ";
        }
        return "";
    }

    private static String memberIndent(
            String source, JsonSourceDocument.ObjectSpan object) {
        if (!object.members().isEmpty()) {
            int keyStart = object.members().get(object.members().size() - 1).keyStart();
            int lineStart = keyStart;
            while (lineStart > object.start()
                    && source.charAt(lineStart - 1) != '\n'
                    && source.charAt(lineStart - 1) != '\r') {
                lineStart--;
            }
            String indent = source.substring(lineStart, keyStart);
            if (indent.chars().allMatch(value -> value == ' ' || value == '\t')) {
                return indent;
            }
        }
        return "  ";
    }

    private static String quote(String value) {
        StringBuilder output = new StringBuilder(value.length() + 2);
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
                default -> appendUnescaped(output, character);
            }
        }
        return output.append('"').toString();
    }

    private static void appendUnescaped(StringBuilder output, char character) {
        if (character >= 0x20) {
            output.append(character);
            return;
        }
        output.append("\\u");
        String hexadecimal = Integer.toHexString(character);
        output.append("0".repeat(4 - hexadecimal.length())).append(hexadecimal);
    }

    private static String insert(String source, int index, String value) {
        return source.substring(0, index) + value + source.substring(index);
    }

    private static String apply(String source, List<Edit> edits) {
        ArrayList<Edit> descending = new ArrayList<>(edits);
        descending.sort((left, right) -> Integer.compare(right.start(), left.start()));
        StringBuilder output = new StringBuilder(source);
        for (Edit edit : descending) {
            output.replace(edit.start(), edit.end(), edit.replacement());
        }
        return output.toString();
    }

    private record Edit(int start, int end, String replacement) {}
}
