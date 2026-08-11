package com.kltyton.autoseamblend.frontend.uilib.screen;

import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDraftFields;
import com.kltyton.autoseamblend.frontend.controller.UilibWorkbenchController;
import com.kltyton.autoseamblend.frontend.model.WorkbenchViewModel;
import com.kltyton.autoseamblend.frontend.uilib.layout.shell.WorkbenchChromeLayout.Frame;
import com.kltyton.autoseamblend.frontend.uilib.layout.shell.WorkbenchLayoutHost;

/**
 * 中文：隔离 Loader 原生预览控件与 PIP 渲染，同时让工作台生命周期和模式编排保持在 common。
 * English: Isolates Loader-native preview widgets and PIP rendering while the
 * common workbench retains lifecycle and mode orchestration.
 */
public interface PreviewWorkspaceRenderer<T extends WorkbenchDraftFields> {
    void assemble(
            WorkbenchLayoutHost host,
            UilibWorkbenchController<T> controller,
            WorkbenchViewModel<T> view,
            Frame frame);

    default void clear() {}

    default void mouseMoved(double mouseX, double mouseY) {}

    default boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button) {
        return false;
    }

    default boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double deltaX,
            double deltaY) {
        return false;
    }

    default boolean mouseReleased(
            double mouseX,
            double mouseY,
            int button) {
        return false;
    }

    default void tick(WorkbenchViewModel<T> view) {}
}
