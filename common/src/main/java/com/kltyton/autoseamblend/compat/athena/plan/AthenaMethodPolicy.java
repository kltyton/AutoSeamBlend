package com.kltyton.autoseamblend.compat.athena.plan;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：集中 Athena 方法的静态能力谓词，避免两个 Loader 各自维护一套分支表。
 * English: Centralizes Athena's static method capability predicates so the two Loaders do not
 * maintain divergent branch tables.
 */
public final class AthenaMethodPolicy {
    private AthenaMethodPolicy() {}

    public static boolean overlay(ConnectionMethod method) {
        return Objects.requireNonNull(method, "method").overlayCapable();
    }

    /**
     * 中文：判断摘要查询是否明确选择了 Athena 并允许 AutoBlend。
     *
     * English: Tests whether a summary query explicitly selected Athena and permits AutoBlend.
     */
    public static boolean runsAthenaAutoBlend(
            Optional<EngineQuerySelection> selection) {
        return Objects.requireNonNull(selection, "selection")
                .filter(value -> value.family() == EngineFamily.ATHENA)
                .filter(EngineQuerySelection::runsAutoBlend)
                .isPresent();
    }

    /**
     * 中文：需要替换接收 Quad 纹理的原生方法。
     * English: Methods that replace the receiver Quad's native texture.
     */
    public static boolean replacement(ConnectionMethod method) {
        return switch (Objects.requireNonNull(method, "method")) {
            case CTM, CTM_COMPACT,
                    HORIZONTAL, VERTICAL,
                    HORIZONTAL_VERTICAL,
                    VERTICAL_HORIZONTAL -> true;
            default -> false;
        };
    }

    /** 中文：需要首轮生成状态精灵的逻辑方法。 / English: Logical methods that require generated state sprites in the first atlas planning pass. */
    public static boolean requiresGeneratedSprites(ConnectionMethod method) {
        return switch (Objects.requireNonNull(method, "method")) {
            case RUNTIME_BLEND, CTM, CTM_COMPACT,
                    HORIZONTAL, VERTICAL,
                    HORIZONTAL_VERTICAL,
                    VERTICAL_HORIZONTAL,
                    OVERLAY, OVERLAY_CTM -> true;
            default -> false;
        };
    }

    /** 中文：需要边框分块生成的逻辑方法。 / English: Logical methods that require border-tile generation. */
    public static boolean requiresBorderGeneration(ConnectionMethod method) {
        return switch (Objects.requireNonNull(method, "method")) {
            case CTM, CTM_COMPACT,
                    HORIZONTAL, VERTICAL,
                    HORIZONTAL_VERTICAL,
                    VERTICAL_HORIZONTAL,
                    OVERLAY_CTM -> true;
            default -> false;
        };
    }

    public static boolean usesOverlayProfile(ConnectionMethod method) {
        ConnectionMethod checked = Objects.requireNonNull(method, "method");
        return checked == ConnectionMethod.RUNTIME_BLEND
                || checked == ConnectionMethod.OVERLAY
                || checked == ConnectionMethod.OVERLAY_CTM;
    }

    public static int logicalSlotCount(ConnectionMethod method) {
        return com.kltyton.autoseamblend.selection.method.MethodSlotDomain
                .of(Objects.requireNonNull(method, "method"))
                .slots()
                .size();
    }
}
