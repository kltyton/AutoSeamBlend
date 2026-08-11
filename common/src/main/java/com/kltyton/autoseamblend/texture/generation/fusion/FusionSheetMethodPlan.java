package com.kltyton.autoseamblend.texture.generation.fusion;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.selection.method.MethodSlotDomain;
import com.kltyton.autoseamblend.texture.mapping.Ctm47Mapper;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * 中文：保存 Fusion 方法与原生 connecting layout 的公共语义，不引用 Fusion 类型。
 *
 * English: Common semantic mapping from Fusion methods to native connecting layouts without
 * referencing Fusion types.
 */
public final class FusionSheetMethodPlan {
    private FusionSheetMethodPlan() {}

    public static Layout layout(ConnectionMethod method) {
        return switch (Objects.requireNonNull(method, "method")) {
            case RUNTIME_BLEND, OVERLAY -> Layout.OVERLAY;
            case HORIZONTAL -> Layout.HORIZONTAL;
            case VERTICAL -> Layout.VERTICAL;
            case CTM, CTM_COMPACT, HORIZONTAL_VERTICAL, VERTICAL_HORIZONTAL, TOP, OVERLAY_CTM ->
                    Layout.FULL;
            default -> throw new IllegalArgumentException(
                    method + " does not use a Fusion connecting sheet");
        };
    }

    /**
     * 中文：公开 compact 方法仍使用 Fusion full layout 的 47 个组合状态，以保持 NeoForge
     * 已验收语义；不要退回 Fusion 的五状态 whole-face compact layout。
     *
     * English: The public compact method intentionally uses Fusion's full layout with 47
     * composed states, preserving the accepted NeoForge semantics rather than its five-state
     * whole-face compact layout.
     */
    public static List<Integer> logicalSlots(ConnectionMethod method) {
        Objects.requireNonNull(method, "method");
        return method == ConnectionMethod.CTM_COMPACT
                ? java.util.stream.IntStream.range(0, Ctm47Mapper.TILE_COUNT).boxed().toList()
                : MethodSlotDomain.of(method).slots();
    }

    /** 中文：返回需要替换型 connecting processor 的公开方法。 / English: Returns methods that use a replacement connecting processor. */
    public static boolean isReplacement(ConnectionMethod method) {
        return switch (Objects.requireNonNull(method, "method")) {
            case CTM, CTM_COMPACT, HORIZONTAL, VERTICAL,
                    HORIZONTAL_VERTICAL, VERTICAL_HORIZONTAL -> true;
            default -> false;
        };
    }

    /** 中文：返回需要生成 Fusion 物理精灵的公开方法。 / English: Returns methods that require generated Fusion physical sprites. */
    public static boolean requiresGeneratedSprites(ConnectionMethod method) {
        return switch (Objects.requireNonNull(method, "method")) {
            case RUNTIME_BLEND, CTM, CTM_COMPACT, HORIZONTAL, VERTICAL,
                    HORIZONTAL_VERTICAL, VERTICAL_HORIZONTAL, OVERLAY, OVERLAY_CTM -> true;
            case TOP, FIXED, NONE -> false;
            case AUTO -> throw new IllegalArgumentException(
                    "auto must be resolved before Fusion sprite planning");
        };
    }

    /** 中文：返回需要边缘配方生成的公开方法。 / English: Returns methods that require border recipe generation. */
    public static boolean requiresBorderGeneration(ConnectionMethod method) {
        return switch (Objects.requireNonNull(method, "method")) {
            case CTM, CTM_COMPACT, HORIZONTAL, VERTICAL,
                    HORIZONTAL_VERTICAL, VERTICAL_HORIZONTAL, OVERLAY_CTM -> true;
            default -> false;
        };
    }

    /** 中文：返回使用 overlay 遮罩的公开方法。 / English: Returns methods that use an overlay mask profile. */
    public static boolean usesOverlayProfile(ConnectionMethod method) {
        return Objects.requireNonNull(method, "method").overlayCapable();
    }

    /**
     * 中文：集中实现原生布局处理器返回的物理槽位收集；适配器只提供外部处理器调用。
     *
     * English: Centralizes physical-slot collection from a native layout handler; adapters only
     * supply the external handler invocation.
     */
    public static int[] collectSelected(Consumer<IntConsumer> nativeEmitter) {
        Objects.requireNonNull(nativeEmitter, "nativeEmitter");
        ArrayList<Integer> selected = new ArrayList<>(4);
        nativeEmitter.accept(selected::add);
        return selected.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * 中文：按 Fusion 原生字段顺序读取八方向状态，保留对角连接的独立语义。
     *
     * English: Reads a Fusion-native eight-direction state in field order while preserving
     * independent diagonals.
     */
    public static NeighborConnections fromNativeFlags(
            boolean left,
            boolean bottomLeft,
            boolean bottom,
            boolean bottomRight,
            boolean right,
            boolean topRight,
            boolean top,
            boolean topLeft) {
        int bits = 0;
        bits |= left ? 1 : 0;
        bits |= bottomLeft ? 1 << 1 : 0;
        bits |= bottom ? 1 << 2 : 0;
        bits |= bottomRight ? 1 << 3 : 0;
        bits |= right ? 1 << 4 : 0;
        bits |= topRight ? 1 << 5 : 0;
        bits |= top ? 1 << 6 : 0;
        bits |= topLeft ? 1 << 7 : 0;
        return NeighborConnections.fromBits(bits);
    }

    /**
     * 中文：生成 Fusion 构造器所需的参数顺序；不得在 Loader 层重新推导位编号。
     *
     * English: Produces constructor arguments in Fusion's native order; Loader code must not
     * re-derive the bit numbering.
     */
    public static NativeConnectionFlags toNativeFlags(NeighborConnections value) {
        Objects.requireNonNull(value, "value");
        int bits = value.bits();
        return new NativeConnectionFlags(
                connected(bits, 6),
                connected(bits, 5),
                connected(bits, 4),
                connected(bits, 3),
                connected(bits, 2),
                connected(bits, 1),
                connected(bits, 0),
                connected(bits, 7));
    }

    private static boolean connected(int bits, int bit) {
        return (bits & (1 << bit)) != 0;
    }

    /**
     * 中文：与 Fusion TextureConnections 构造器一一对应的八个参数。
     *
     * English: The eight parameters corresponding one-for-one to Fusion's TextureConnections
     * constructor.
     */
    public record NativeConnectionFlags(
            boolean top,
            boolean topRight,
            boolean right,
            boolean bottomRight,
            boolean bottom,
            boolean bottomLeft,
            boolean left,
            boolean topLeft) {}

    public enum Layout {
        OVERLAY,
        HORIZONTAL,
        VERTICAL,
        FULL
    }
}
