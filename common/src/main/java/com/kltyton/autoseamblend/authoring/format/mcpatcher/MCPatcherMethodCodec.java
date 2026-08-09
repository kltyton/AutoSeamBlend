package com.kltyton.autoseamblend.authoring.format.mcpatcher;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.math.BigInteger;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 中文：MCPatcher 公共方法别名与原生执行方法 ID。 / English: MCPatcher public-method aliases and native execution method ids. */
public final class MCPatcherMethodCodec {
    private static final Pattern NUMERIC_RANGE = Pattern.compile("([0-9]+)-([0-9]+)");
    private static final BigInteger COUNT_LIMIT = BigInteger.valueOf(48);

    private MCPatcherMethodCodec() {}

    public static Optional<ConnectionMethod> parsePublic(String raw) {
        if (raw == null) return Optional.empty();
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "auto" -> Optional.of(ConnectionMethod.AUTO);
            case "runtime_blend" -> Optional.of(ConnectionMethod.RUNTIME_BLEND);
            case "ctm" -> Optional.of(ConnectionMethod.CTM);
            case "ctm_compact" -> Optional.of(ConnectionMethod.CTM_COMPACT);
            case "horizontal" -> Optional.of(ConnectionMethod.HORIZONTAL);
            case "vertical" -> Optional.of(ConnectionMethod.VERTICAL);
            case "horizontal_vertical", "horizontal+vertical", "h+v" -> Optional.of(ConnectionMethod.HORIZONTAL_VERTICAL);
            case "vertical_horizontal", "vertical+horizontal", "v+h" -> Optional.of(ConnectionMethod.VERTICAL_HORIZONTAL);
            case "top" -> Optional.of(ConnectionMethod.TOP);
            case "overlay" -> Optional.of(ConnectionMethod.OVERLAY);
            case "overlay_ctm" -> Optional.of(ConnectionMethod.OVERLAY_CTM);
            case "fixed" -> Optional.of(ConnectionMethod.FIXED);
            case "none" -> Optional.of(ConnectionMethod.NONE);
            default -> Optional.empty();
        };
    }

    public static String nativeMethod(ConnectionMethod method) {
        return switch (method) {
            case AUTO -> throw new IllegalArgumentException("auto must be resolved before native execution");
            case RUNTIME_BLEND, OVERLAY -> "overlay";
            case CTM -> "ctm";
            case CTM_COMPACT -> "ctm_compact";
            case HORIZONTAL -> "horizontal";
            case VERTICAL -> "vertical";
            case HORIZONTAL_VERTICAL -> "horizontal+vertical";
            case VERTICAL_HORIZONTAL -> "vertical+horizontal";
            case TOP -> "top";
            case OVERLAY_CTM -> "overlay_ctm";
            case FIXED, NONE -> "fixed";
        };
    }

    public static boolean requiresExecutionView(String raw) {
        if (raw == null) return false;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "auto", "runtime_blend", "horizontal_vertical", "vertical_horizontal", "h+v", "v+h", "none" -> true;
            default -> false;
        };
    }

    /** 中文：只从显式 tiles 约束推导兼容的原生 parser 载体；无约束或有歧义时留给精确表面 AUTO。 / English: Derives a native parser carrier only from explicit tiles constraints; missing or ambiguous constraints remain exact-surface AUTO. */
    public static Optional<ConnectionMethod> resolveExplicitAutoConstraint(Properties properties) {
        String tiles = properties.getProperty("tiles");
        if (tiles == null) return Optional.empty();
        String normalized = tiles.trim();
        if (normalized.equals("<skip>") || normalized.equals("<skip>.png")) {
            return Optional.of(ConnectionMethod.NONE);
        }
        int count = expandedTileCount(normalized);
        return switch (count) {
            case 47 -> Optional.of(properties.containsKey("layer") ? ConnectionMethod.OVERLAY_CTM : ConnectionMethod.CTM);
            case 17 -> Optional.of(ConnectionMethod.OVERLAY);
            case 7 -> Optional.of(ConnectionMethod.HORIZONTAL_VERTICAL);
            case 5 -> Optional.of(ConnectionMethod.CTM_COMPACT);
            case 4 -> Optional.of(ConnectionMethod.HORIZONTAL);
            case 1 -> Optional.of(ConnectionMethod.FIXED);
            default -> Optional.empty();
        };
    }

    private static int expandedTileCount(String raw) {
        if (raw.isBlank()) return 0;
        BigInteger count = BigInteger.ZERO;
        for (String token : raw.split("[ ,]+")) {
            if (token.isBlank()) continue;
            Matcher range = NUMERIC_RANGE.matcher(token);
            if (range.matches()) {
                BigInteger minimum = new BigInteger(range.group(1));
                BigInteger maximum = new BigInteger(range.group(2));
                if (minimum.compareTo(maximum) <= 0) {
                    count = count.add(maximum.subtract(minimum).add(BigInteger.ONE));
                    if (count.compareTo(COUNT_LIMIT) >= 0) return COUNT_LIMIT.intValueExact();
                    continue;
                }
            }
            count = count.add(BigInteger.ONE);
        }
        return count.intValueExact();
    }
}
