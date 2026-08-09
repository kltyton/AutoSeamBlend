package com.kltyton.autoseamblend.texture.generation;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.selection.method.MethodSlotDomain;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 中文：把已捕获的原生槽位表转换为项目自有像素配方；原生表采集仍由各 Loader 负责。
 * English: Converts captured native slot maps into project-owned pixel recipes; each Loader still captures its native tables.
 */
public final class ContinuitySlotRecipePlanner {
    private final Map<ConnectionMethod, List<GeneratedTileRecipe>> recipes;

    private ContinuitySlotRecipePlanner(NativeSlotMapSnapshot maps) {
        this.recipes = buildRecipes(Objects.requireNonNull(maps, "maps"));
    }

    public static ContinuitySlotRecipePlanner create(NativeSlotMapSnapshot maps) {
        return new ContinuitySlotRecipePlanner(maps);
    }

    /**
     * 中文：把 Loader 转换出的原生数组一次封装为 common 配方规划器。
     * English: Builds the common recipe planner from the native arrays converted by a Loader.
     */
    public static ContinuitySlotRecipePlanner create(
            int[] ctm,
            int[] compactRepresentatives,
            int[] horizontal,
            int[] vertical,
            int[] horizontalVerticalPrimary,
            int[] horizontalVerticalSecondary,
            int[] verticalHorizontalPrimary,
            int[] verticalHorizontalSecondary) {
        return create(
                new NativeSlotMapSnapshot(
                        ctm,
                        compactRepresentatives,
                        horizontal,
                        vertical,
                        horizontalVerticalPrimary,
                        horizontalVerticalSecondary,
                        verticalHorizontalPrimary,
                        verticalHorizontalSecondary));
    }

    public GeneratedTileRecipe recipe(ConnectionMethod method, int slot) {
        Objects.requireNonNull(method, "method");
        List<GeneratedTileRecipe> methodRecipes = recipes.get(method);
        if (methodRecipes == null) {
            throw new IllegalArgumentException(
                    method + " has no materialized Continuity slots");
        }
        if (slot < 0 || slot >= methodRecipes.size()) {
            throw new IllegalArgumentException("slot " + slot + " is outside " + method);
        }
        return methodRecipes.get(slot);
    }

    public List<Integer> slots(ConnectionMethod method) {
        Objects.requireNonNull(method, "method");
        if (method == ConnectionMethod.NONE) {
            return List.of();
        }
        if (method == ConnectionMethod.AUTO) {
            throw new IllegalArgumentException("auto must be resolved before slot lookup");
        }
        return MethodSlotDomain.of(method).slots();
    }

    private static Map<ConnectionMethod, List<GeneratedTileRecipe>> buildRecipes(
            NativeSlotMapSnapshot maps) {
        EnumMap<ConnectionMethod, List<GeneratedTileRecipe>> recipes =
                new EnumMap<>(ConnectionMethod.class);
        recipes.put(ConnectionMethod.RUNTIME_BLEND, overlay(ConnectionMethod.RUNTIME_BLEND));
        recipes.put(
                ConnectionMethod.CTM,
                fromNativeMap(
                        ConnectionMethod.CTM,
                        maps.ctm(),
                        NativeSlotMapSnapshot.CTM_LENGTH,
                        ContinuitySlotRecipePlanner::identityBits,
                        ContinuitySlotRecipePlanner::normalizedCtm));
        recipes.put(ConnectionMethod.CTM_COMPACT, compact(maps.compactRepresentatives()));
        recipes.put(
                ConnectionMethod.HORIZONTAL,
                fromNativeMap(
                        ConnectionMethod.HORIZONTAL,
                        maps.horizontal(),
                        NativeSlotMapSnapshot.SINGLE_AXIS_LENGTH,
                        ContinuitySlotRecipePlanner::horizontalBits,
                        ignored -> true));
        recipes.put(
                ConnectionMethod.VERTICAL,
                fromNativeMap(
                        ConnectionMethod.VERTICAL,
                        maps.vertical(),
                        NativeSlotMapSnapshot.SINGLE_AXIS_LENGTH,
                        ContinuitySlotRecipePlanner::verticalBits,
                        ignored -> true));
        recipes.put(
                ConnectionMethod.HORIZONTAL_VERTICAL,
                prioritized(
                        ConnectionMethod.HORIZONTAL_VERTICAL,
                        maps.horizontalVerticalPrimary(),
                        maps.horizontalVerticalSecondary(),
                        true));
        recipes.put(
                ConnectionMethod.VERTICAL_HORIZONTAL,
                prioritized(
                        ConnectionMethod.VERTICAL_HORIZONTAL,
                        maps.verticalHorizontalPrimary(),
                        maps.verticalHorizontalSecondary(),
                        false));
        recipes.put(ConnectionMethod.TOP, List.of(GeneratedTileRecipe.Source.INSTANCE));
        recipes.put(ConnectionMethod.OVERLAY, overlay(ConnectionMethod.OVERLAY));
        recipes.put(
                ConnectionMethod.OVERLAY_CTM,
                fromNativeMap(
                        ConnectionMethod.OVERLAY_CTM,
                        maps.ctm(),
                        NativeSlotMapSnapshot.CTM_LENGTH,
                        ContinuitySlotRecipePlanner::identityBits,
                        ContinuitySlotRecipePlanner::normalizedCtm));
        recipes.put(ConnectionMethod.FIXED, List.of(GeneratedTileRecipe.Source.INSTANCE));
        if (recipes.size() != ConnectionMethod.values().length - 2) {
            throw new IllegalStateException("Continuity recipe domain is incomplete");
        }
        recipes.forEach(
                (method, values) -> {
                    int expected = MethodSlotDomain.of(method).slots().size();
                    if (values.size() != expected) {
                        throw new IllegalStateException(
                                method
                                        + " recipe count "
                                        + values.size()
                                        + " differs from "
                                        + expected);
                    }
                });
        return Map.copyOf(recipes);
    }

    private static List<GeneratedTileRecipe> overlay(ConnectionMethod method) {
        return MethodSlotDomain.of(method).slots().stream()
                .map(GeneratedTileRecipe.OverlayMask17::new)
                .map(GeneratedTileRecipe.class::cast)
                .toList();
    }

    private static List<GeneratedTileRecipe> compact(int[] representatives) {
        return MethodSlotDomain.of(ConnectionMethod.CTM_COMPACT).slots().stream()
                .map(
                        slot ->
                                new GeneratedTileRecipe.BorderConnections(
                                        NeighborConnections.fromBits(representatives[slot])))
                .map(GeneratedTileRecipe.class::cast)
                .toList();
    }

    private static List<GeneratedTileRecipe> prioritized(
            ConnectionMethod method, int[] primary, int[] secondary, boolean horizontalFirst) {
        requireLength(
                method,
                primary,
                NativeSlotMapSnapshot.PRIORITIZED_PRIMARY_LENGTH);
        requireLength(
                method,
                secondary,
                NativeSlotMapSnapshot.PRIORITIZED_SECONDARY_LENGTH);
        LinkedHashMap<Integer, Integer> bits = new LinkedHashMap<>();
        merge(
                bits,
                invert(
                        method,
                        primary,
                        value -> horizontalFirst ? horizontalBits(value) : verticalBits(value),
                        value -> value != 0));
        merge(
                bits,
                invert(
                        method,
                        secondary,
                        value ->
                                horizontalFirst
                                        ? horizontalVerticalBits(value)
                                        : verticalHorizontalBits(value),
                        value ->
                                horizontalFirst
                                        ? validHorizontalVertical(value)
                                        : validVerticalHorizontal(value)));
        return recipes(method, bits);
    }

    private static List<GeneratedTileRecipe> fromNativeMap(
            ConnectionMethod method,
            int[] nativeMap,
            int expectedLength,
            BitsTranslator translator,
            StatePredicate accepted) {
        requireLength(method, nativeMap, expectedLength);
        return recipes(method, invert(method, nativeMap, translator, accepted));
    }

    private static Map<Integer, Integer> invert(
            ConnectionMethod method,
            int[] nativeMap,
            BitsTranslator translator,
            StatePredicate accepted) {
        List<Integer> domain = MethodSlotDomain.of(method).slots();
        LinkedHashMap<Integer, Integer> bySlot = new LinkedHashMap<>();
        for (int nativeBits = 0; nativeBits < nativeMap.length; nativeBits++) {
            int slot = nativeMap[nativeBits];
            if (!domain.contains(slot)) {
                throw new IllegalStateException(method + " native map returned slot " + slot);
            }
            if (accepted.test(nativeBits)) {
                bySlot.putIfAbsent(slot, translator.translate(nativeBits));
            }
        }
        LinkedHashMap<Integer, Integer> ordered = new LinkedHashMap<>();
        for (int slot : domain) {
            Integer bits = bySlot.get(slot);
            if (bits != null) {
                ordered.put(slot, bits);
            }
        }
        return ordered;
    }

    private static List<GeneratedTileRecipe> recipes(
            ConnectionMethod method, Map<Integer, Integer> bitsBySlot) {
        List<Integer> domain = MethodSlotDomain.of(method).slots();
        if (!List.copyOf(bitsBySlot.keySet()).equals(domain)) {
            throw new IllegalStateException(method + " native maps do not cover every slot");
        }
        return domain.stream()
                .map(
                        slot ->
                                stateRecipe(
                                        method,
                                        NeighborConnections.fromBits(bitsBySlot.get(slot))))
                .map(GeneratedTileRecipe.class::cast)
                .toList();
    }

    private static GeneratedTileRecipe stateRecipe(
            ConnectionMethod method, NeighborConnections connections) {
        if (method == ConnectionMethod.OVERLAY_CTM) {
            return new GeneratedTileRecipe.BlendConnections(connections);
        }
        return new GeneratedTileRecipe.BorderConnections(connections);
    }

    private static void merge(Map<Integer, Integer> target, Map<Integer, Integer> source) {
        source.forEach(target::putIfAbsent);
    }

    private static void requireLength(ConnectionMethod method, int[] values, int expected) {
        if (values.length != expected) {
            throw new IllegalStateException(
                    method
                            + " Continuity map length changed from "
                            + expected
                            + " to "
                            + values.length);
        }
    }

    private static int identityBits(int bits) {
        return bits;
    }

    private static boolean normalizedCtm(int bits) {
        return NeighborConnections.fromBits(bits).normalizedCtmBits() == bits;
    }

    private static int horizontalBits(int bits) {
        return ((bits & 1) != 0 ? 1 : 0) | ((bits & 2) != 0 ? 1 << 4 : 0);
    }

    private static int verticalBits(int bits) {
        return ((bits & 1) != 0 ? 1 << 2 : 0) | ((bits & 2) != 0 ? 1 << 6 : 0);
    }

    private static int horizontalVerticalBits(int bits) {
        return translateSixBits(bits, new int[] {1, 2, 3, 5, 6, 7});
    }

    private static int verticalHorizontalBits(int bits) {
        return translateSixBits(bits, new int[] {0, 1, 3, 4, 5, 7});
    }

    private static int translateSixBits(int bits, int[] commonBits) {
        int translated = 0;
        for (int bit = 0; bit < commonBits.length; bit++) {
            if ((bits & 1 << bit) != 0) {
                translated |= 1 << commonBits[bit];
            }
        }
        return translated;
    }

    private static boolean validHorizontalVertical(int bits) {
        return implies(bits, 0, 1)
                && implies(bits, 2, 1)
                && implies(bits, 3, 4)
                && implies(bits, 5, 4);
    }

    private static boolean validVerticalHorizontal(int bits) {
        return implies(bits, 1, 0)
                && implies(bits, 5, 0)
                && implies(bits, 2, 3)
                && implies(bits, 4, 3);
    }

    private static boolean implies(int bits, int dependent, int required) {
        return (bits & 1 << dependent) == 0 || (bits & 1 << required) != 0;
    }

    @FunctionalInterface
    private interface BitsTranslator {
        int translate(int bits);
    }

    @FunctionalInterface
    private interface StatePredicate {
        boolean test(int bits);
    }
}
