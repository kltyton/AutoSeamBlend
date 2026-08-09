package com.kltyton.autoseamblend.frontend.model.preview;

import com.kltyton.autoseamblend.frontend.model.PreviewViewModel;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;

/**
 * 中文：当真实预览由独立 Renderer 提交时，仅携带动作校验所需的已捕获代次。
 * English: Carries only the captured generation required for action validation
 * when a separate renderer submits the real preview.
 */
public record GenerationOnlyPreviewSurface(long generation)
        implements PreviewViewModel.RuntimeSurface {
    public GenerationOnlyPreviewSurface {
        if (generation < 0) {
            throw new IllegalArgumentException(
                    "preview generation must be nonnegative");
        }
    }

    @Override
    public void extractScene(
            GuiGraphics graphics,
            PreviewViewModel.Viewport viewport,
            PreviewViewModel.Camera camera) {}

    @Override
    public void extractFace(
            GuiGraphics graphics,
            PreviewViewModel.Viewport viewport,
            Direction face) {}

    @Override
    public Optional<PreviewViewModel.Hit> pick(
            double mouseX,
            double mouseY,
            PreviewViewModel.Viewport viewport,
            PreviewViewModel.Camera camera) {
        return Optional.empty();
    }
}
