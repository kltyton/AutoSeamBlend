package com.kltyton.autoseamblend.frontend.uilib.screen;

import com.kltyton.autoseamblend.authoring.workbench.WorkbenchMode;
import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDraftFields;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchMetrics;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchTheme;
import com.kltyton.autoseamblend.frontend.uilib.component.PanelComponent;
import com.kltyton.autoseamblend.frontend.uilib.component.property.NativePropertyPanel;
import com.kltyton.autoseamblend.frontend.controller.UilibWorkbenchController;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.AddTarget;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.CancelRequested;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.ExportRequested;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.SaveRequested;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.ShowMode;
import com.kltyton.autoseamblend.frontend.model.WorkbenchViewLease;
import com.kltyton.autoseamblend.frontend.uilib.layout.paint.PaintWorkspaceLayout;
import com.kltyton.autoseamblend.frontend.uilib.layout.property.PropertyWorkspaceLayout;
import com.kltyton.autoseamblend.frontend.uilib.layout.shell.WorkbenchChromeLayout;
import com.kltyton.autoseamblend.frontend.uilib.layout.shell.WorkbenchChromeLayout.Frame;
import com.kltyton.autoseamblend.frontend.uilib.layout.target.TargetLibraryLayout;
import com.kltyton.autoseamblend.frontend.model.NativePropertiesViewModel;
import com.kltyton.autoseamblend.frontend.model.NativePropertiesViewModel.SelectorCandidate;
import com.kltyton.autoseamblend.frontend.model.PaintViewModel;
import com.kltyton.autoseamblend.frontend.model.TargetRowView;
import com.kltyton.autoseamblend.frontend.model.WorkbenchViewModel;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * 中文：Fabric 与 NeoForge 共用的 UILib 工作台主体；所有领域副作用通过控制器动作端口离开视图。
 *
 * English:
 * Shared Fabric and NeoForge UILib workbench body. Every domain side effect
 * leaves the view through the controller action port.
 */
public final class UilibWorkbenchScreen<T extends WorkbenchDraftFields>
        extends AbstractUilibWorkbenchScreen
        implements NativePropertyPanel.Host {
    private static final int GAP = UilibWorkbenchMetrics.PANEL_GAP;

    private final UilibWorkbenchController<T> controller;
    private final AtomicBoolean removed = new AtomicBoolean();
    private final Runnable removalCallback;
    private final WorkbenchChromeLayout chrome;
    private final TargetLibraryLayout targets;
    private final NativePropertyPanel propertyPanel;
    private final PropertyWorkspaceLayout<NativePropertiesViewModel, SelectorCandidate>
            propertyLayout;
    private final PaintWorkspaceLayout<T> paintLayout;
    private boolean confirmCancel;
    private final PreviewWorkspaceRenderer<T> previewRenderer;
    private final WorkbenchViewLifecycle<T> lifecycle;
    private Frame frame;

    public UilibWorkbenchScreen(
            UilibWorkbenchController<T> controller,
            PreviewWorkspaceRenderer<T> previewRenderer,
            WorkbenchViewLifecycle<T> lifecycle,
            Runnable removalCallback) {
        super(Component.translatable("gui.autoseamblend.title"));
        this.controller = Objects.requireNonNull(
                controller,
                "controller");
        this.previewRenderer = Objects.requireNonNull(
                previewRenderer,
                "previewRenderer");
        this.lifecycle = Objects.requireNonNull(
                lifecycle,
                "lifecycle");
        this.removalCallback = Objects.requireNonNull(
                removalCallback,
                "removalCallback");
        chrome = new WorkbenchChromeLayout(this);
        targets = new TargetLibraryLayout(this);
        propertyPanel = new NativePropertyPanel(this, this::dispatch);
        propertyLayout = new PropertyWorkspaceLayout<>(propertyPanel::layout);
        paintLayout = new PaintWorkspaceLayout<>(this, controller);
        controller.setListener(ignored -> rebuild());
    }

    @Override
    protected void beforeWorkbenchClear() {
        propertyPanel.captureScrollState();
    }

    @Override
    protected void afterWorkbenchClear() {
        previewRenderer.clear();
    }

    @Override
    protected void assembleWorkbench() {
        WorkbenchViewModel<T> view = controller.view();
        if (view.mode() != WorkbenchMode.NATIVE_PROPERTIES) {
            propertyPanel.close();
        }
        chrome.setEngine(view.engineStatus());
        chrome.setStatus(view.operationStatus());
        frame = chrome.begin(
                WorkbenchChromeLayout.modeTitle(view.mode()),
                view.mode() == WorkbenchMode.TARGET_LIBRARY);
        switch (view.mode()) {
            case TARGET_LIBRARY -> layoutTargets(view);
            case CONNECTION_PREVIEW -> layoutPreview(view);
            case TEXTURE_PAINT -> layoutPaint(view);
            case NATIVE_PROPERTIES -> layoutProperties(view);
        }
    }

    @Override
    protected void afterWorkbenchRebuildTick() {
        previewRenderer.tick(controller.view());
    }

    @Override
    protected void beforeWorkbenchRebuildTick() {
        lifecycle.tick(controller);
    }

    @Override
    public void onClose() {
        if (removed.get()) {
            return;
        }
        WorkbenchViewModel<T> view = controller.view();
        if (view.mode() != WorkbenchMode.TARGET_LIBRARY) {
            showMode(WorkbenchMode.TARGET_LIBRARY);
            return;
        }
        requestCancel(view);
    }

    /**
     * 中文：Minecraft 主动移除 Screen 时只释放组合资源；不得再次 setScreen，避免 removed 递归。
     * English: Minecraft removal only releases the composition; it never calls
     * setScreen again, preventing removed recursion.
     */
    @Override
    public void removed() {
        if (!removed.compareAndSet(false, true)) {
            return;
        }
        try {
            super.removed();
        } finally {
            controller.close();
            removalCallback.run();
        }
    }

    @Override
    protected void previewMouseMoved(
            double mouseX,
            double mouseY) {
        previewRenderer.mouseMoved(mouseX, mouseY);
    }

    @Override
    protected boolean previewMouseClicked(
            MouseButtonEvent event,
            boolean doubleClick) {
        return previewRenderer.mouseClicked(event, doubleClick);
    }

    @Override
    protected boolean previewMouseDragged(
            MouseButtonEvent event,
            double deltaX,
            double deltaY) {
        return previewRenderer.mouseDragged(
                event,
                deltaX,
                deltaY);
    }

    @Override
    protected boolean previewMouseReleased(
            MouseButtonEvent event) {
        return previewRenderer.mouseReleased(event);
    }

    private void layoutTargets(WorkbenchViewModel<T> view) {
        targets.assemble(
                view.targets(),
                view.availableTargets(),
                frame.contentTop(),
                frame.bodyBottom(),
                () -> lifecycle.pickerRequested(controller),
                blockId -> controller.dispatch(new AddTarget(blockId)),
                entryKey -> showMode(entryKey, WorkbenchMode.CONNECTION_PREVIEW),
                entryKey -> showMode(entryKey, WorkbenchMode.TEXTURE_PAINT),
                entryKey -> showMode(entryKey, WorkbenchMode.NATIVE_PROPERTIES));
        chrome.status(frame);
        chrome.footer(
                frame,
                action(
                        confirmCancel && view.dirty()
                                ? "gui.autoseamblend.action.confirm_cancel"
                                : "gui.autoseamblend.action.cancel_exit",
                        () -> requestCancel(view),
                        canCancel(view)),
                action(
                        "gui.autoseamblend.action.export_pack",
                        () -> controller.dispatch(new ExportRequested()),
                        view.canSubmit()),
                action(
                        "gui.autoseamblend.action.save_exit",
                        () -> controller.dispatch(new SaveRequested()),
                        view.canSubmit()));
    }

    private void layoutPreview(WorkbenchViewModel<T> view) {
        previewRenderer.assemble(
                this,
                controller,
                view,
                frame);
        modeFooter(
                "gui.autoseamblend.action.exit_preview",
                () -> showMode(WorkbenchMode.TARGET_LIBRARY),
                "gui.autoseamblend.action.edit_texture",
                () -> showMode(WorkbenchMode.TEXTURE_PAINT),
                "gui.autoseamblend.action.edit_properties",
                () -> showMode(WorkbenchMode.NATIVE_PROPERTIES),
                view.canSubmit());
    }

    private void layoutPaint(WorkbenchViewModel<T> view) {
        PaintViewModel paint = view.paint().orElse(null);
        if (paint == null) {
            unavailableBody(Component.translatable(
                    "gui.autoseamblend.status.pixel_unavailable",
                    Component.translatable(
                            "gui.autoseamblend.status.target_unavailable")));
            modeFooter(
                    "gui.autoseamblend.action.back_target",
                    () -> showMode(WorkbenchMode.TARGET_LIBRARY),
                    "gui.autoseamblend.action.preview",
                    () -> showMode(WorkbenchMode.CONNECTION_PREVIEW),
                    "gui.autoseamblend.action.edit_properties",
                    () -> showMode(WorkbenchMode.NATIVE_PROPERTIES),
                    false);
            return;
        }
        paintLayout.open(paint);
        paintLayout.assemble(
                paint,
                new WorkbenchViewLease(
                        controller.layoutGeneration(),
                        view.mode()),
                frame);
        modeFooter(
                "gui.autoseamblend.action.back_target",
                () -> showMode(WorkbenchMode.TARGET_LIBRARY),
                "gui.autoseamblend.action.preview",
                () -> showMode(WorkbenchMode.CONNECTION_PREVIEW),
                "gui.autoseamblend.action.edit_properties",
                () -> showMode(WorkbenchMode.NATIVE_PROPERTIES),
                view.canSubmit());
    }

    private void layoutProperties(WorkbenchViewModel<T> view) {
        NativePropertiesViewModel properties = view.properties().orElse(null);
        if (properties == null) {
            propertyPanel.close();
            unavailableBody(Component.translatable(
                    "gui.autoseamblend.status.property_unavailable",
                    Component.translatable(
                            "gui.autoseamblend.status.target_unavailable")));
            modeFooter(
                    "gui.autoseamblend.action.back_target",
                    () -> showMode(WorkbenchMode.TARGET_LIBRARY),
                    "gui.autoseamblend.action.preview",
                    () -> showMode(WorkbenchMode.CONNECTION_PREVIEW),
                    "gui.autoseamblend.action.edit_texture",
                    () -> showMode(WorkbenchMode.TEXTURE_PAINT),
                    false);
            return;
        }
        String entryKey = view.selectedEntryKey().orElseThrow(() ->
                new IllegalStateException("property mode requires a selected entry"));
        TargetRowView row = view.targets().stream()
                .filter(candidate -> candidate.entryKey().equals(entryKey))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "selected property entry is absent from the target library"));
        if (!propertyPanel.isOpenFor(entryKey)) {
            propertyPanel.open(entryKey, properties);
        } else {
            // 中文：同一属性会话只同步新快照，不重置输入、选择器子页或滚动位置。
            // English: The same property session only syncs its new snapshot; local UI state persists.
        }
        propertyLayout.assemble(
                row,
                properties,
                view.propertyCandidates(),
                frame.contentTop(),
                frame.footerTop(),
                () -> showMode(WorkbenchMode.TARGET_LIBRARY),
                () -> showMode(WorkbenchMode.CONNECTION_PREVIEW),
                () -> showMode(WorkbenchMode.TEXTURE_PAINT));
    }

    private void unavailableBody(Component message) {
        addComponent(new PanelComponent(
                frame.left(),
                frame.contentTop(),
                Math.max(1, frame.width()),
                Math.max(1, frame.bodyHeight()),
                UilibWorkbenchTheme.SURFACE_INPUT,
                PanelComponent.Relief.INSET));
        addText(
                message,
                frame.left() + GAP,
                frame.contentTop() + GAP,
                UilibWorkbenchTheme.TEXT_BUTTON_SECONDARY);
    }

    private void modeFooter(
            String leftKey,
            Runnable left,
            String middleKey,
            Runnable middle,
            String rightKey,
            Runnable right,
            boolean enabled) {
        chrome.footer(
                frame,
                action(leftKey, left, true),
                action(middleKey, middle, enabled),
                action(rightKey, right, enabled));
    }

    private void requestCancel(WorkbenchViewModel<T> view) {
        if (controller.pendingOperation().isPresent()) {
            controller.dispatch(new CancelRequested(false));
            return;
        }
        if (!view.dirty() || confirmCancel) {
            controller.dispatch(new CancelRequested(confirmCancel));
            return;
        }
        confirmCancel = true;
        rebuild();
    }

    private boolean canCancel(WorkbenchViewModel<T> view) {
        return controller.pendingOperation()
                .map(token -> token.cancellable())
                .orElse(!view.operationInProgress());
    }

    private void showMode(WorkbenchMode mode) {
        showMode(
                controller.view()
                        .selectedEntryKey()
                        .orElseThrow(() -> new IllegalStateException(
                                "selected workbench entry is unavailable")),
                mode);
    }

    private void showMode(
            String entryKey,
            WorkbenchMode mode) {
        confirmCancel = false;
        controller.dispatch(new ShowMode(entryKey, mode));
    }

    @Override
    public boolean actionsEnabled() {
        return controller.view().canSubmit();
    }

    public boolean dispatch(WorkbenchAction action) {
        return controller.dispatch(action);
    }

    @Override
    public void footer(
            int top,
            String backKey,
            Runnable back,
            Runnable preview,
            Runnable paint) {
        chrome.footer(
                frame,
                action(backKey, back, true),
                action(
                        "gui.autoseamblend.action.preview",
                        preview,
                        actionsEnabled()),
                action(
                        "gui.autoseamblend.action.edit_texture",
                        paint,
                        actionsEnabled()));
    }

}
