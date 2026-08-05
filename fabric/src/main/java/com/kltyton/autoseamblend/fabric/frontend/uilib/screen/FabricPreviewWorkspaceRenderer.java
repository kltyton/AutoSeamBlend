package com.kltyton.autoseamblend.fabric.frontend.uilib.screen;

import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDraftFields;
import com.kltyton.autoseamblend.frontend.controller.UilibWorkbenchController;
import com.kltyton.autoseamblend.frontend.model.PreviewViewModel;
import com.kltyton.autoseamblend.frontend.model.WorkbenchViewModel;
import com.kltyton.autoseamblend.frontend.model.preview.PreviewSceneState;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchTheme;
import com.kltyton.autoseamblend.frontend.uilib.component.PanelComponent;
import com.kltyton.autoseamblend.frontend.uilib.layout.shell.WorkbenchChromeLayout.Frame;
import com.kltyton.autoseamblend.frontend.uilib.layout.shell.WorkbenchLayoutHost;
import com.kltyton.autoseamblend.frontend.uilib.screen.PreviewWorkspaceRenderer;
import com.kltyton.autoseamblend.frontend.uilib.component.preview.InteractiveBlockPreviewWidget;
import com.kltyton.autoseamblend.frontend.uilib.component.preview.PreviewPointerCapture;
import com.kltyton.autoseamblend.frontend.uilib.layout.preview.PreviewWorkspaceLayout;
import com.kltyton.autoseamblend.fabric.frontend.uilib.render.preview.FabricBlockScenePorts;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * 中文：NeoForge 已验收 InteractiveBlock/PIP 预览的唯一 Loader 渲染工厂。
 * English: Sole Loader renderer factory for the accepted Fabric
 * InteractiveBlock/PIP preview.
 */
public final class FabricPreviewWorkspaceRenderer<T extends WorkbenchDraftFields>
        implements PreviewWorkspaceRenderer<T> {
    private final Function<String, Optional<PreviewSceneState>> scenes;
    private final Runnable changed;
    private final PreviewPointerCapture pointer = new PreviewPointerCapture();
    private PreviewWorkspaceLayout layout;

    public FabricPreviewWorkspaceRenderer(
            Function<String, Optional<PreviewSceneState>> scenes,
            Runnable changed) {
        this.scenes = Objects.requireNonNull(scenes, "scenes");
        this.changed = Objects.requireNonNull(changed, "changed");
    }

    @Override
    public void assemble(
            WorkbenchLayoutHost host,
            UilibWorkbenchController<T> controller,
            WorkbenchViewModel<T> view,
            Frame frame) {
        Optional<PreviewSceneState> scene = view.selectedEntryKey().flatMap(scenes);
        if (scene.isEmpty()) {
            host.addComponent(new PanelComponent(
                    frame.left(),
                    frame.contentTop(),
                    Math.max(1, frame.width()),
                    Math.max(1, frame.bodyHeight()),
                    UilibWorkbenchTheme.SURFACE_INPUT,
                    PanelComponent.Relief.INSET));
            host.addText(
                    view.preview()
                            .map(PreviewViewModel::unavailableReason)
                            .orElseGet(() -> Component.translatable(
                                    "gui.autoseamblend.preview.unavailable")),
                    frame.left() + 8,
                    frame.contentTop() + 8,
                    UilibWorkbenchTheme.TEXT_BUTTON_SECONDARY);
            layout = null;
            return;
        }
        layout = new PreviewWorkspaceLayout(
                host,
                FabricBlockScenePorts.geometryCache(),
                FabricBlockScenePorts::submit);
        layout.assemble(
                scene.orElseThrow(),
                view.preview()
                        .map(PreviewViewModel::observedFace)
                        .orElse(net.minecraft.core.Direction.NORTH),
                changed,
                frame.left(),
                frame.contentTop(),
                frame.width(),
                frame.bodyHeight());
    }

    @Override
    public void clear() {
        if (layout != null) {
            layout.clear();
        }
        layout = null;
        pointer.reset();
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        InteractiveBlockPreviewWidget scene = scene();
        if (scene != null) {
            scene.updateHoveredFace(mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return pointer.click(scene(), event, doubleClick);
    }

    @Override
    public boolean mouseDragged(
            MouseButtonEvent event,
            double deltaX,
            double deltaY) {
        return pointer.drag(scene(), event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return pointer.release(scene(), event);
    }

    private InteractiveBlockPreviewWidget scene() {
        return layout == null ? null : layout.sceneWidget();
    }
}
