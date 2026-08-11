package com.kltyton.autoseamblend.forge.frontend.uilib.screen;

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
import com.kltyton.autoseamblend.forge.frontend.uilib.render.preview.ForgeBlockScenePorts;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.network.chat.Component;

/**
 * 中文：Forge 已验收 InteractiveBlock/PIP 预览的唯一 Loader 渲染工厂。
 * English: Sole Loader renderer factory for the accepted Forge
 * InteractiveBlock/PIP preview.
 */
public final class ForgePreviewWorkspaceRenderer<T extends WorkbenchDraftFields>
        implements PreviewWorkspaceRenderer<T> {
    private final Function<String, Optional<PreviewSceneState>> scenes;
    private final Runnable changed;
    private final PreviewPointerCapture pointer = new PreviewPointerCapture();
    private PreviewWorkspaceLayout layout;

    public ForgePreviewWorkspaceRenderer(
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
                ForgeBlockScenePorts.geometryCache(),
                ForgeBlockScenePorts::submit);
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
        // 中文：工作台每次重建（候选扫描每 tick 发布、悬停面变化）都会调用 clear()，
        // 若在此重置 pointer，中键/右键拖拽捕获会在第一个 drag 事件后丢失，旋转/平移
        // 只生效一次。捕获状态保留到 release() 或下一次 click()，新 widget 共享同一
        // PreviewSceneState，拖拽可跨重建继续累加 yaw/pitch。
        //
        // English: Every workbench rebuild (candidate scan publishes per tick,
        // hovered-face changes) calls clear(); resetting the pointer here would
        // drop the middle/right drag capture right after the first drag event,
        // making rotation/pan effective only once. The capture survives until
        // release() or the next click(), and the rebuilt widget shares the same
        // PreviewSceneState, so drags keep accumulating yaw/pitch across rebuilds.
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        InteractiveBlockPreviewWidget scene = scene();
        if (scene != null) {
            scene.updateHoveredFace(mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button) {
        return pointer.click(
                scene(),
                mouseX,
                mouseY,
                button);
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double deltaX,
            double deltaY) {
        return pointer.drag(
                scene(),
                mouseX,
                mouseY,
                button,
                deltaX,
                deltaY);
    }

    @Override
    public boolean mouseReleased(
            double mouseX,
            double mouseY,
            int button) {
        return pointer.release(
                scene(),
                mouseX,
                mouseY,
                button);
    }

    private InteractiveBlockPreviewWidget scene() {
        return layout == null ? null : layout.sceneWidget();
    }
}
