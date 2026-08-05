package com.kltyton.autoseamblend.frontend.uilib.component.preview;

import com.daqem.uilib.api.widget.IWidget;
import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDraftFields;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.CycleReceiver;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.ObserveFace;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.ToggleNeighbor;
import com.kltyton.autoseamblend.frontend.controller.UilibWorkbenchController;
import com.kltyton.autoseamblend.frontend.model.PreviewViewModel;
import com.kltyton.autoseamblend.frontend.model.PreviewViewModel.Camera;
import com.kltyton.autoseamblend.frontend.model.PreviewViewModel.Hit;
import com.kltyton.autoseamblend.frontend.model.PreviewViewModel.Viewport;
import com.kltyton.autoseamblend.frontend.model.preview.PreviewCameraState;
import com.kltyton.autoseamblend.frontend.uilib.component.preview.PreviewPointerState;
import com.kltyton.autoseamblend.frontend.uilib.component.preview.PreviewWidgetChrome;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.NotNull;

/**
 * 中文：只呈现 runtime 适配器提交的同 generation 场景/单面结果，不提供 Atlas 或模拟回退。
 *
 * English:
 * Renders only same-generation scene/face results supplied by the runtime
 * adapter; it never falls back to an atlas or simulated output.
 */
public final class RuntimePreviewWidget<T extends WorkbenchDraftFields>
        extends AbstractWidget
        implements IWidget {
    private final UilibWorkbenchController<T> controller;
    private final boolean exactFace;
    private final PreviewCameraState camera = new PreviewCameraState();
    private final PreviewPointerState pointer = new PreviewPointerState();

    public RuntimePreviewWidget(
            int width,
            int height,
            UilibWorkbenchController<T> controller,
            boolean exactFace) {
        super(
                0,
                0,
                width,
                height,
                PreviewWidgetChrome.narration(exactFace));
        this.controller = Objects.requireNonNull(
                controller,
                "controller");
        this.exactFace = exactFace;
        active = !exactFace;
    }

    @Override
    protected void extractWidgetRenderState(
            @NotNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        PreviewWidgetChrome.drawCanvas(
                graphics,
                getX(),
                getY(),
                getWidth(),
                getHeight());
        PreviewViewModel preview = controller.view()
                .preview()
                .orElse(null);
        if (preview == null || preview.surface().isEmpty()) {
            var reason = preview == null
                    ? PreviewWidgetChrome.unavailable()
                    : preview.unavailableReason();
            PreviewWidgetChrome.drawUnavailable(
                    graphics,
                    getX(),
                    getY(),
                    reason);
            PreviewWidgetChrome.drawBorder(
                    graphics,
                    getX(),
                    getY(),
                    getWidth(),
                    getHeight(),
                    isFocused());
            return;
        }
        Viewport viewport = viewport();
        graphics.enableScissor(
                viewport.x(),
                viewport.y(),
                viewport.x() + viewport.width(),
                viewport.y() + viewport.height());
        if (exactFace) {
            preview.surface()
                    .orElseThrow()
                    .extractFace(
                            graphics,
                            viewport,
                            preview.observedFace());
        } else {
            preview.surface()
                    .orElseThrow()
                    .extractScene(
                            graphics,
                            viewport,
                            camera());
        }
        graphics.disableScissor();
        PreviewWidgetChrome.drawBorder(
                graphics,
                getX(),
                getY(),
                getWidth(),
                getHeight(),
                isFocused());
    }

    public void updateHoveredFace(
            double mouseX,
            double mouseY) {
        if (exactFace) {
            return;
        }
        if (controller.view()
                .preview()
                .flatMap(PreviewViewModel::surface)
                .isEmpty()) {
            return;
        }
        Direction observed = isMouseOver(mouseX, mouseY)
                ? hit(mouseX, mouseY)
                .map(Hit::face)
                        .orElse(Direction.NORTH)
                : Direction.NORTH;
        if (controller.view()
                .preview()
                .map(PreviewViewModel::observedFace)
                .filter(observed::equals)
                .isEmpty()) {
            controller.dispatch(new ObserveFace(observed));
        }
    }

    public boolean captureClick(
            MouseButtonEvent event,
            boolean doubleClick) {
        if (exactFace
                || !isMouseOver(event.x(), event.y())
                || !PreviewPointerState.validButton(event.button())) {
            return false;
        }
        if (!pointer.begin(event.button())) {
            return false;
        }
        if (event.button() == PreviewPointerState.LEFT_BUTTON) {
            hit(event.x(), event.y())
                    .ifPresent(value -> value.neighbor()
                            .ifPresentOrElse(
                                    position -> controller.dispatch(
                                            new ToggleNeighbor(position)),
                                    () -> controller.dispatch(
                                            new CycleReceiver())));
        }
        return true;
    }

    public boolean captureDrag(
            MouseButtonEvent event,
            double deltaX,
            double deltaY) {
        if (!pointer.captures(event.button())) {
            return false;
        }
        if (pointer.captures(PreviewPointerState.MIDDLE_BUTTON)) {
            camera.rotate(deltaX, deltaY);
            return true;
        }
        if (pointer.captures(PreviewPointerState.RIGHT_BUTTON)) {
            camera.pan(deltaX, deltaY);
            return true;
        }
        return pointer.captures(PreviewPointerState.LEFT_BUTTON);
    }

    public boolean captureRelease(
            MouseButtonEvent event) {
        if (!pointer.captures(event.button())) {
            return false;
        }
        pointer.release();
        return true;
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double horizontal,
            double vertical) {
        if (exactFace
                || !isMouseOver(mouseX, mouseY)
                || vertical == 0.0D) {
            return false;
        }
        camera.zoom(vertical > 0.0D ? 1.0D : -1.0D);
        return true;
    }

    public void resetCamera() {
        camera.reset();
    }

    private Optional<Hit> hit(
            double mouseX,
            double mouseY) {
        return controller.view()
                .preview()
                .flatMap(PreviewViewModel::surface)
                .flatMap(surface -> surface.pick(
                        mouseX,
                        mouseY,
                        viewport(),
                        camera()));
    }

    private Viewport viewport() {
        return new Viewport(
                getX(),
                getY(),
                Math.max(1, getWidth()),
                Math.max(1, getHeight()));
    }

    private Camera camera() {
        return camera.snapshot();
    }

    @Override
    protected void updateWidgetNarration(
            @NotNull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
