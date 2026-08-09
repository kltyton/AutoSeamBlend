package com.kltyton.autoseamblend.neoforge.frontend.uilib.controller;

import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringDraft;
import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDocument;
import com.kltyton.autoseamblend.authoring.workbench.WorkbenchMode;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchOperationCoordinator;
import com.kltyton.autoseamblend.frontend.model.WorkbenchViewModel;
import com.kltyton.autoseamblend.frontend.port.WorkbenchActionPort;
import com.kltyton.autoseamblend.frontend.uilib.screen.UilibWorkbenchScreen;
import com.kltyton.autoseamblend.frontend.uilib.screen.WorkbenchViewLifecycle;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.neoforge.frontend.uilib.screen.NeoForgePreviewWorkspaceRenderer;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 中文：NeoForge 仅组合原生端口与 common 工作台；会话、布局、控件和操作令牌均由 common 拥有。
 * English: NeoForge only composes its native port with the common workbench;
 * common owns the session, layout, widgets, and operation tokens.
 */
public final class UilibWorkbenchController {
    private final NeoForgeWorkbenchNativePort nativePort;
    private final com.kltyton.autoseamblend.frontend.controller.UilibWorkbenchController<ManagedAuthoringDraft>
            session;
    private final UilibWorkbenchScreen<ManagedAuthoringDraft> screen;

    private UilibWorkbenchController(
            Minecraft minecraft,
            EngineQuerySelection selection) {
        nativePort = new NeoForgeWorkbenchNativePort(
                minecraft,
                selection.family(),
                selection.engineId());
        WorkbenchViewModel<ManagedAuthoringDraft> initial = nativePort.initial();
        WorkbenchOperationCoordinator<ManagedAuthoringDraft> actions =
                new WorkbenchOperationCoordinator<>(
                        nativePort,
                        minecraft::execute,
                        () -> ReloadPublication.current().generation(),
                        nativePort.openedGeneration());
        session = new com.kltyton.autoseamblend.frontend.controller.UilibWorkbenchController<>(
                initial,
                actions,
                reason -> minecraft.setScreen(null));
        screen = new UilibWorkbenchScreen<>(
                session,
                new NeoForgePreviewWorkspaceRenderer<>(
                        nativePort::previewScene,
                        () -> nativePort.previewChanged(session)),
                WorkbenchViewLifecycle.of(
                        nativePort::ensureCandidates,
                        nativePort::tickCandidates),
                this::close);
    }

    public static int open() {
        Minecraft minecraft = Minecraft.getInstance();
        Optional<EngineQuerySelection> selection =
                EngineQueryRouter.current(minecraft);
        if (selection.isEmpty()) {
            minecraft.setScreen(blockedScreen(minecraft));
            return 1;
        }
        EngineQuerySelection selected =
                selection.orElseThrow();
        UilibWorkbenchController controller =
                new UilibWorkbenchController(
                        minecraft,
                        selected);
        minecraft.setScreen(controller.screen);
        return 1;
    }

    private static UilibWorkbenchScreen<ManagedAuthoringDraft> blockedScreen(
            Minecraft minecraft) {
        WorkbenchViewModel<ManagedAuthoringDraft> initial = new WorkbenchViewModel<>(
                WorkbenchDocument.open(List.of()),
                WorkbenchMode.TARGET_LIBRARY,
                List.of(),
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Component.translatable("gui.autoseamblend.status.engine_required"),
                Component.translatable("gui.autoseamblend.status.engine_required"),
                false,
                false);
        var session = new com.kltyton.autoseamblend.frontend.controller.UilibWorkbenchController<>(
                initial,
                (request, completions) -> new WorkbenchActionPort.Settled<>(request.current()),
                reason -> minecraft.setScreen(null));
        return new UilibWorkbenchScreen<>(
                session,
                new NeoForgePreviewWorkspaceRenderer<>(ignored -> Optional.empty(), () -> {}),
                WorkbenchViewLifecycle.none(),
                () -> {});
    }

    /** 中文：Screen 生命周期结束时关闭 common 会话与原生资源。 / English: Close the common session and native resources with the Screen lifetime. */
    public void close() {
        session.close();
        nativePort.close();
    }
}
