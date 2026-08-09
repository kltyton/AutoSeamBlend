package com.kltyton.autoseamblend.authoring.document.json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 中文：保存 JSON 对象成员及值在原文本中的不可变偏移索引。
 * English: Keeps an immutable source-offset index for JSON members and values.
 */
record JsonSourceDocument(String source, ObjectSpan root, String error) {
    static JsonSourceDocument parse(String source, String error) throws IOException {
        return new StructureParser(source, error,
                JsonLexicalScanner.scan(source, error), true)
                .parse();
    }

    static void validateValue(String source, String error) throws IOException {
        new StructureParser(source, error,
                JsonLexicalScanner.scanStrict(source, error), false)
                .parseValue();
    }

    String raw(ValueSpan value) {
        return source.substring(value.start(), value.end());
    }

    ObjectSpan objectAt(ObjectSpan start, String key) {
        MemberSpan member = start.last(key);
        return member != null && member.value() instanceof ObjectSpan object
                ? object
                : null;
    }

    ObjectSpan objectAt(ObjectSpan start, List<String> path) {
        ObjectSpan current = start;
        for (String key : path) {
            current = objectAt(current, key);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    ObjectSpan objectByStart(int start) {
        return objectByStart(root, start);
    }

    private static ObjectSpan objectByStart(ValueSpan value, int start) {
        if (value instanceof ObjectSpan object) {
            if (object.start() == start) {
                return object;
            }
            for (MemberSpan member : object.members()) {
                ObjectSpan nested = objectByStart(member.value(), start);
                if (nested != null) {
                    return nested;
                }
            }
        } else if (value instanceof ArraySpan array) {
            for (ValueSpan item : array.values()) {
                ObjectSpan nested = objectByStart(item, start);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    sealed interface ValueSpan permits ObjectSpan, ArraySpan, ScalarSpan {
        int start();
        int end();
    }

    record ObjectSpan(int start, int end, int trailingTriviaStart,
                      List<MemberSpan> members) implements ValueSpan {
        static ObjectSpan empty() {
            return new ObjectSpan(0, 2, 1, List.of());
        }

        MemberSpan last(String key) {
            for (int index = members.size() - 1; index >= 0; index--) {
                MemberSpan member = members.get(index);
                if (member.name().equals(key)) {
                    return member;
                }
            }
            return null;
        }

        List<MemberSpan> members(String key) {
            ArrayList<MemberSpan> matches = new ArrayList<>();
            for (MemberSpan member : members) {
                if (member.name().equals(key)) {
                    matches.add(member);
                }
            }
            return List.copyOf(matches);
        }

        List<MemberSpan> effectiveMembers() {
            LinkedHashMap<String, MemberSpan> effective = new LinkedHashMap<>();
            for (MemberSpan member : members) {
                effective.put(member.name(), member);
            }
            return List.copyOf(effective.values());
        }
    }

    record ArraySpan(int start, int end, List<ValueSpan> values) implements ValueSpan {}

    record ScalarSpan(int start, int end) implements ValueSpan {}

    record MemberSpan(String name, int keyStart, ValueSpan value,
                      int commaStart, int commaEnd) {}

    private static final class StructureParser {
        private final String source;
        private final String error;
        private final List<JsonLexicalScanner.Token> tokens;
        private final boolean allowTrailingComma;
        private int index;

        private StructureParser(
                String source,
                String error,
                List<JsonLexicalScanner.Token> tokens,
                boolean allowTrailingComma) {
            this.source = source;
            this.error = error;
            this.tokens = tokens;
            this.allowTrailingComma = allowTrailingComma;
        }

        private JsonSourceDocument parse() throws IOException {
            ValueSpan value = value();
            expect(JsonLexicalScanner.Type.END);
            if (!(value instanceof ObjectSpan object)) {
                throw invalid();
            }
            return new JsonSourceDocument(source, object, error);
        }

        private void parseValue() throws IOException {
            value();
            expect(JsonLexicalScanner.Type.END);
        }

        private ValueSpan value() throws IOException {
            return switch (peek().type()) {
                case LEFT_BRACE -> object();
                case LEFT_BRACKET -> array();
                case STRING, NUMBER, TRUE, FALSE, NULL -> {
                    JsonLexicalScanner.Token token = take();
                    yield new ScalarSpan(token.start(), token.end());
                }
                default -> throw invalid();
            };
        }

        private ObjectSpan object() throws IOException {
            JsonLexicalScanner.Token open = expect(JsonLexicalScanner.Type.LEFT_BRACE);
            ArrayList<MemberSpan> members = new ArrayList<>();
            if (peek().type() == JsonLexicalScanner.Type.RIGHT_BRACE) {
                JsonLexicalScanner.Token close = take();
                return new ObjectSpan(open.start(), close.end(), open.end(), List.of());
            }
            while (true) {
                JsonLexicalScanner.Token key = expect(JsonLexicalScanner.Type.STRING);
                expect(JsonLexicalScanner.Type.COLON);
                ValueSpan memberValue = value();
                if (peek().type() == JsonLexicalScanner.Type.COMMA) {
                    JsonLexicalScanner.Token comma = take();
                    members.add(new MemberSpan(key.decoded(), key.start(), memberValue,
                            comma.start(), comma.end()));
                    if (peek().type() == JsonLexicalScanner.Type.RIGHT_BRACE) {
                        if (!allowTrailingComma) {
                            throw invalid();
                        }
                        JsonLexicalScanner.Token close = take();
                        return new ObjectSpan(open.start(), close.end(), comma.end(),
                                List.copyOf(members));
                    }
                    continue;
                }
                members.add(new MemberSpan(
                        key.decoded(), key.start(), memberValue, -1, -1));
                JsonLexicalScanner.Token close = expect(
                        JsonLexicalScanner.Type.RIGHT_BRACE);
                return new ObjectSpan(open.start(), close.end(), memberValue.end(),
                        List.copyOf(members));
            }
        }

        private ArraySpan array() throws IOException {
            JsonLexicalScanner.Token open = expect(JsonLexicalScanner.Type.LEFT_BRACKET);
            ArrayList<ValueSpan> values = new ArrayList<>();
            if (peek().type() == JsonLexicalScanner.Type.RIGHT_BRACKET) {
                JsonLexicalScanner.Token close = take();
                return new ArraySpan(open.start(), close.end(), List.of());
            }
            while (true) {
                values.add(value());
                if (peek().type() == JsonLexicalScanner.Type.COMMA) {
                    take();
                    if (peek().type() == JsonLexicalScanner.Type.RIGHT_BRACKET) {
                        if (!allowTrailingComma) {
                            throw invalid();
                        }
                        JsonLexicalScanner.Token close = take();
                        return new ArraySpan(
                                open.start(), close.end(), List.copyOf(values));
                    }
                    continue;
                }
                JsonLexicalScanner.Token close = expect(
                        JsonLexicalScanner.Type.RIGHT_BRACKET);
                return new ArraySpan(open.start(), close.end(), List.copyOf(values));
            }
        }

        private JsonLexicalScanner.Token expect(JsonLexicalScanner.Type type)
                throws IOException {
            if (peek().type() != type) {
                throw invalid();
            }
            return take();
        }

        private JsonLexicalScanner.Token peek() {
            return tokens.get(index);
        }

        private JsonLexicalScanner.Token take() {
            return tokens.get(index++);
        }

        private IOException invalid() {
            return new IOException(error);
        }
    }
}
