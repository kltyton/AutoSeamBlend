package com.kltyton.autoseamblend.authoring.document.json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 中文：验证 JSON token，并跳过但不删除空白与注释。
 * English: Validates JSON tokens while skipping, but never deleting, trivia and comments.
 */
final class JsonLexicalScanner {
    enum Type {
        LEFT_BRACE,
        RIGHT_BRACE,
        LEFT_BRACKET,
        RIGHT_BRACKET,
        COLON,
        COMMA,
        STRING,
        NUMBER,
        TRUE,
        FALSE,
        NULL,
        END
    }

    record Token(Type type, int start, int end, String decoded) {}

    private final String source;
    private final String error;
    private final boolean allowExtensions;
    private int index;

    private JsonLexicalScanner(
            String source, String error, boolean allowExtensions) {
        this.source = source;
        this.error = error;
        this.allowExtensions = allowExtensions;
    }

    static List<Token> scan(String source, String error) throws IOException {
        return new JsonLexicalScanner(source, error, true).scan();
    }

    static List<Token> scanStrict(String source, String error) throws IOException {
        return new JsonLexicalScanner(source, error, false).scan();
    }

    private List<Token> scan() throws IOException {
        ArrayList<Token> tokens = new ArrayList<>();
        while (true) {
            skipTrivia();
            if (index == source.length()) {
                tokens.add(new Token(Type.END, index, index, null));
                return List.copyOf(tokens);
            }
            tokens.add(scanToken());
        }
    }

    private Token scanToken() throws IOException {
        int start = index;
        return switch (source.charAt(index)) {
            case '{' -> single(Type.LEFT_BRACE);
            case '}' -> single(Type.RIGHT_BRACE);
            case '[' -> single(Type.LEFT_BRACKET);
            case ']' -> single(Type.RIGHT_BRACKET);
            case ':' -> single(Type.COLON);
            case ',' -> single(Type.COMMA);
            case '"' -> string();
            case 't' -> literal(Type.TRUE, "true");
            case 'f' -> literal(Type.FALSE, "false");
            case 'n' -> literal(Type.NULL, "null");
            default -> number(start);
        };
    }

    private Token single(Type type) {
        int start = index++;
        return new Token(type, start, index, null);
    }

    private Token string() throws IOException {
        int start = index++;
        StringBuilder decoded = new StringBuilder();
        while (index < source.length()) {
            char character = source.charAt(index++);
            if (character == '"') {
                return new Token(Type.STRING, start, index, decoded.toString());
            }
            if (character < 0x20) {
                throw invalid();
            }
            if (character != '\\') {
                decoded.append(character);
                continue;
            }
            if (index == source.length()) {
                throw invalid();
            }
            char escaped = source.charAt(index++);
            switch (escaped) {
                case '"', '\\', '/' -> decoded.append(escaped);
                case 'b' -> decoded.append('\b');
                case 'f' -> decoded.append('\f');
                case 'n' -> decoded.append('\n');
                case 'r' -> decoded.append('\r');
                case 't' -> decoded.append('\t');
                case 'u' -> decoded.append(unicodeEscape());
                default -> throw invalid();
            }
        }
        throw invalid();
    }

    private char unicodeEscape() throws IOException {
        if (index + 4 > source.length()) {
            throw invalid();
        }
        int value = 0;
        for (int offset = 0; offset < 4; offset++) {
            int digit = Character.digit(source.charAt(index++), 16);
            if (digit < 0) {
                throw invalid();
            }
            value = value * 16 + digit;
        }
        return (char) value;
    }

    private Token literal(Type type, String literal) throws IOException {
        int start = index;
        if (!source.startsWith(literal, index)) {
            throw invalid();
        }
        index += literal.length();
        return new Token(type, start, index, null);
    }

    private Token number(int start) throws IOException {
        take('-');
        if (take('0')) {
            if (asciiDigitAt(index)) {
                throw invalid();
            }
        } else {
            requireDigit(true);
            consumeDigits();
        }
        if (take('.')) {
            requireDigit(false);
            consumeDigits();
        }
        if (index < source.length()
                && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
            index++;
            if (index < source.length()
                    && (source.charAt(index) == '+' || source.charAt(index) == '-')) {
                index++;
            }
            requireDigit(false);
            consumeDigits();
        }
        if (index == start || (index == start + 1 && source.charAt(start) == '-')) {
            throw invalid();
        }
        return new Token(Type.NUMBER, start, index, null);
    }

    private void consumeDigits() {
        while (asciiDigitAt(index)) {
            index++;
        }
    }

    private void requireDigit(boolean nonZero) throws IOException {
        char minimum = nonZero ? '1' : '0';
        if (index >= source.length()
                || source.charAt(index) < minimum
                || source.charAt(index) > '9') {
            throw invalid();
        }
        index++;
    }

    private boolean asciiDigitAt(int position) {
        return position < source.length()
                && source.charAt(position) >= '0'
                && source.charAt(position) <= '9';
    }

    private void skipTrivia() throws IOException {
        while (index < source.length()) {
            char character = source.charAt(index);
            if (jsonWhitespace(character)
                    || (allowExtensions
                            && (Character.isWhitespace(character)
                                    || character == '\uFEFF'))) {
                index++;
                continue;
            }
            if (!allowExtensions) {
                return;
            }
            if (character != '/' || index + 1 >= source.length()) {
                return;
            }
            char next = source.charAt(index + 1);
            if (next == '/') {
                index += 2;
                while (index < source.length()
                        && source.charAt(index) != '\n'
                        && source.charAt(index) != '\r') {
                    index++;
                }
                continue;
            }
            if (next != '*') {
                return;
            }
            int end = source.indexOf("*/", index + 2);
            if (end < 0) {
                throw invalid();
            }
            index = end + 2;
        }
    }

    private static boolean jsonWhitespace(char character) {
        return character == ' '
                || character == '\t'
                || character == '\r'
                || character == '\n';
    }

    private boolean take(char expected) {
        if (index < source.length() && source.charAt(index) == expected) {
            index++;
            return true;
        }
        return false;
    }

    private IOException invalid() {
        return new IOException(error);
    }
}
