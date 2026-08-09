package com.kltyton.autoseamblend.frontend.model;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * 中文：把真实运行时预览的一次 generation 捕获与纯交互状态组合成不可变视图模型。
 *
 * English:
 * Combines one captured runtime-preview generation with immutable interaction
 * state. The surface must render both views from the same captured geometry.
 */
public record PreviewViewModel(
        Optional<RuntimeSurface> surface,
        Component unavailableReason,
        Set<NeighborPosition> neighbors,
        Direction observedFace,
        int receiverVariant) {
    public PreviewViewModel {
        surface = Objects.requireNonNull(surface, "surface");
        unavailableReason = Objects.requireNonNull(
                unavailableReason,
                "unavailableReason");
        neighbors = Set.copyOf(
                Objects.requireNonNull(neighbors, "neighbors"));
        observedFace = Objects.requireNonNull(
                observedFace,
                "observedFace");
        if (receiverVariant < 0) {
            throw new IllegalArgumentException(
                    "preview receiver variant must be nonnegative");
        }
        surface.ifPresent(value -> {
            if (value.generation() < 0) {
                throw new IllegalArgumentException(
                        "preview generation must be nonnegative");
            }
        });
    }

    /**
     * 中文：由运行时适配器提供的 generation 固定渲染面；空缺时视图只显示不可用状态。
     *
     * English:
     * Generation-pinned render surface supplied by the runtime integration.
     * The view shows an unavailable state instead of fabricating a preview.
     */
    public interface RuntimeSurface {
        long generation();

        void extractScene(
                GuiGraphics graphics,
                Viewport viewport,
                Camera camera);

        void extractFace(
                GuiGraphics graphics,
                Viewport viewport,
                Direction face);

        Optional<Hit> pick(
                double mouseX,
                double mouseY,
                Viewport viewport,
                Camera camera);
    }

    /** 中文：运行时画布边界。 / English: Runtime-canvas bounds. */
    public record Viewport(int x, int y, int width, int height) {
        public Viewport {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException(
                        "preview viewport must be positive");
            }
        }
    }

    /** 中文：仅属于视图的相机状态。 / English: View-only camera state. */
    public record Camera(
            float yaw,
            float pitch,
            float zoom,
            float panX,
            float panY) {
        public Camera {
            if (!Float.isFinite(yaw)
                    || !Float.isFinite(pitch)
                    || !Float.isFinite(zoom)
                    || !Float.isFinite(panX)
                    || !Float.isFinite(panY)
                    || zoom <= 0.0F) {
                throw new IllegalArgumentException(
                        "invalid preview camera");
            }
        }
    }

    /** 中文：同一运行时几何拾取出的首个可见面。 / English: First visible face picked from the same runtime geometry. */
    public record Hit(
            Optional<NeighborPosition> neighbor,
            Direction face) {
        public Hit {
            neighbor = Objects.requireNonNull(
                    neighbor,
                    "neighbor");
            face = Objects.requireNonNull(face, "face");
        }
    }

    /** 中文：DESIGN.md 固定的十个可编辑邻接位置。 / English: Ten editable neighbor positions fixed by DESIGN.md. */
    public enum NeighborPosition {
        FRONT,
        BACK,
        UP,
        DOWN,
        LEFT,
        RIGHT,
        LEFT_FRONT,
        RIGHT_FRONT,
        LEFT_BACK,
        RIGHT_BACK
    }
}
