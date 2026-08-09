package com.kltyton.autoseamblend.authoring.preview;

import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：为三维场景与精确面结果解析同一组原生方块 tint；Loader 独占的动态 tint（如 NeoForge
 * IClientBlockExtensions）通过注册的钩子接入。
 *
 * English:
 * Resolves the same native block tints for the 3D scene and exact-face result;
 * Loader-exclusive dynamic tint (such as NeoForge IClientBlockExtensions) joins
 * through a registered hook.
 */
public final class BlockPreviewTint {
    private static final AtomicReference<DynamicTint>
            DYNAMIC_TINT = new AtomicReference<>();

    private BlockPreviewTint() {}

    /**
     * 中文：注册 Loader 独占的动态 tint 收集钩子（NeoForge 的 IClientBlockExtensions）。
     *
     * English: Registers the Loader-exclusive dynamic-tint collector hook
     * (NeoForge IClientBlockExtensions).
     */
    public static void installDynamicTint(DynamicTint dynamicTint) {
        DYNAMIC_TINT.set(Objects.requireNonNull(
                dynamicTint,
                "dynamicTint"));
    }

    public static int color(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            FaceSurface surface) {
        Objects.requireNonNull(surface, "surface");
        int tintIndex = surface.tintIndex();
        if (tintIndex < 0) {
            return -1;
        }
        int[] values = values(level, state, pos);
        return tintIndex < values.length
                ? opaque(values[tintIndex])
                : -1;
    }

    public static int[] values(
            BlockAndTintGetter level,
            BlockState state,
            BlockPos pos) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(pos, "pos");
        IntArrayList dynamic = new IntArrayList();
        int vanilla = Minecraft.getInstance()
                .getBlockColors()
                .getColor(
                        state,
                        level,
                        pos,
                        0);
        if (vanilla != -1) {
            dynamic.add(vanilla);
        }
        DynamicTint hook = DYNAMIC_TINT.get();
        if (hook != null) {
            hook.collect(
                    state,
                    level,
                    pos,
                    dynamic);
        }
        int[] tints = dynamic.toIntArray();
        for (int index = 0;
                index < tints.length;
                index++) {
            tints[index] = opaque(tints[index]);
        }
        return tints;
    }

    private static int opaque(int color) {
        return (color & 0xFF000000) == 0
                ? color | 0xFF000000
                : color;
    }

    /**
     * 中文：Loader 独占动态 tint 收集契约。
     *
     * English: Loader-exclusive dynamic-tint collection contract.
     */
    @FunctionalInterface
    public interface DynamicTint {
        void collect(
                BlockState state,
                BlockAndTintGetter level,
                BlockPos pos,
                IntArrayList output);
    }
}
