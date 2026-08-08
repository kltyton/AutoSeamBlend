package com.kltyton.autoseamblend.fabric.frontend.uilib.controller;

import com.kltyton.autoseamblend.foundation.diagnostic.FailureDetails;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringDraft;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringProject;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringRule;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringProjectDrafts;
import com.kltyton.autoseamblend.authoring.property.NativePropertyPatch;
import com.kltyton.autoseamblend.authoring.project.WorkbenchTargetCatalog;
import com.kltyton.autoseamblend.authoring.selector.MinecraftNativeBlockSelectorResolver;
import com.kltyton.autoseamblend.authoring.template.ManagedAuthoringTemplates;
import com.kltyton.autoseamblend.authoring.document.NativeDocumentOperations;
import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDocument;
import com.kltyton.autoseamblend.authoring.workbench.WorkbenchMode;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.export.managed.ManagedExportProfile;
import com.kltyton.autoseamblend.export.managed.ManagedExportRequest;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchCandidateScanPlanner;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchOperationCoordinator;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchSourceConflictReducer;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchViewReducer;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchViewMappings;
import com.kltyton.autoseamblend.frontend.uilib.component.property.NativePropertyDocumentActions;
import com.kltyton.autoseamblend.frontend.uilib.component.property.NativePropertyDocumentViewProjection;
import com.kltyton.autoseamblend.frontend.model.NativePropertiesViewModel;
import com.kltyton.autoseamblend.frontend.model.PaintViewModel;
import com.kltyton.autoseamblend.frontend.model.PreviewViewModel;
import com.kltyton.autoseamblend.frontend.model.TargetRowView;
import com.kltyton.autoseamblend.frontend.model.WorkbenchViewModel;
import com.kltyton.autoseamblend.frontend.model.preview.GenerationOnlyPreviewSurface;
import com.kltyton.autoseamblend.frontend.model.preview.PreviewSceneState;
import com.kltyton.autoseamblend.frontend.paint.TexturePaintDocument;
import com.kltyton.autoseamblend.frontend.port.WorkbenchActionPort;
import com.kltyton.autoseamblend.authoring.export.SystemExportDestinationPicker;
import com.kltyton.autoseamblend.authoring.export.ExportDraft;
import com.kltyton.autoseamblend.authoring.export.ExportDraftPlanning;
import com.kltyton.autoseamblend.authoring.export.ExportDrafts;
import com.kltyton.autoseamblend.authoring.export.ManagedExportService;
import com.kltyton.autoseamblend.fabric.authoring.export.FabricExportMetadata;
import com.kltyton.autoseamblend.fabric.engine.registry.FabricEngineRegistry;
import com.kltyton.autoseamblend.authoring.materialize.ConnectionTextureSources;
import com.kltyton.autoseamblend.authoring.materialize.TextureSourceSnapshot;
import com.kltyton.autoseamblend.authoring.property.NativePropertyDocumentLoader;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.authoring.storage.ManagedSaveCoordinator;
import com.kltyton.autoseamblend.frontend.paint.TexturePaintAdapter;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.publication.GenerationPublicationState;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * 中文：Fabric 原生工作台端口；只保留注册表、原生文档、真实预览/绘画和保存导出服务的适配。
 * English: Fabric native workbench port; only registry, native-document,
 * real preview/paint, and persistence/export adapters remain here.
 */
final class FabricWorkbenchNativePort
        implements WorkbenchOperationCoordinator.DomainOperations<ManagedAuthoringDraft> {
    private static final int SCAN_BATCH = 64;
    private static final long SCAN_BUDGET = 2_000_000L;

    private final Minecraft minecraft;
    private final EngineFamily family;
    private final String engineId;
    private final long openedGeneration;
    private final LinkedHashMap<String, WorkbenchTargetCatalog.Entry> sources =
            new LinkedHashMap<>();
    private final LinkedHashMap<String, NativePropertyDocumentLoader> properties =
            new LinkedHashMap<>();
    private final LinkedHashMap<String, TexturePaintAdapter> paints =
            new LinkedHashMap<>();
    private final LinkedHashMap<String, PreviewSceneState> scenes =
            new LinkedHashMap<>();
    private final LinkedHashMap<String, Direction> paintFaces =
            new LinkedHashMap<>();
    private final Map<WorkbenchActionPort.OperationToken, ManagedExportService.ExportHandle>
            exports = new ConcurrentHashMap<>();
    private final Map<WorkbenchActionPort.OperationToken, AtomicBoolean> cancellations =
            new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private WorkbenchCandidateScanPlanner<Block> planner;
    private final ArrayList<TargetRowView> candidates = new ArrayList<>();
    private final ArrayList<NativePropertiesViewModel.SelectorCandidate> selectors =
            new ArrayList<>();
    private Set<String> candidateExistingReceivers = Set.of();

    FabricWorkbenchNativePort(Minecraft minecraft, EngineFamily family, String engineId) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.family = Objects.requireNonNull(family, "family");
        this.engineId = Objects.requireNonNull(engineId, "engineId");
        openedGeneration = ReloadPublication.current().generation();
    }

    long openedGeneration() {
        return openedGeneration;
    }

    /** 中文：Screen 生命周期结束时取消原生导出句柄；English: Cancel native export handles when the Screen lifetime ends. */
    void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        cancellations.values().forEach(flag -> flag.set(true));
        exports.values().forEach(ManagedExportService.ExportHandle::cancel);
        cancellations.clear();
        exports.clear();
        planner = null;
    }

    WorkbenchViewModel<ManagedAuthoringDraft> initial() {
        ArrayList<WorkbenchDocument.Item<ManagedAuthoringDraft>> items = new ArrayList<>();
        ReloadPublication.Generation runtime = ReloadPublication.current();
        for (WorkbenchTargetCatalog.Entry entry :
                WorkbenchTargetCatalog.current(
                        family,
                        runtime,
                        (block, fallback, generation) -> EngineQueryRouter
                                .select(block.defaultBlockState(), generation)
                                .map(selection -> selection.family())
                                .orElse(fallback))) {
            sources.put(entry.entryKey(), entry);
            items.add(new WorkbenchDocument.Item<>(
                    entry.entryKey(),
                    entry.entryId(),
                    entry.documentPath(),
                    entry.family(),
                    entry.draft(),
                    entry.method(),
                    entry.compatibility(),
                    entry.managed(),
                    entry.configured(),
                    false,
                    false));
        }
        WorkbenchDocument<ManagedAuthoringDraft> document = WorkbenchDocument.open(items);
        return new WorkbenchViewModel<>(
                document,
                WorkbenchMode.TARGET_LIBRARY,
                rows(document),
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Component.translatable(
                        "gui.autoseamblend.engine_value",
                        Component.literal(engineId),
                        Component.literal(family.formatId())),
                Component.translatable("gui.autoseamblend.status.ready"),
                true,
                false);
    }

    @Override
    public WorkbenchViewModel<ManagedAuthoringDraft> apply(
            WorkbenchAction action,
            WorkbenchViewModel<ManagedAuthoringDraft> frozen,
            long activeGeneration) {
        try {
            if (action instanceof WorkbenchAction.AddTarget add) {
                return addTarget(frozen, add.blockId(), activeGeneration);
            }
            if (action instanceof WorkbenchAction.ShowMode show) {
                return showMode(frozen, show.entryKey(), show.mode(), activeGeneration);
            }
            if (WorkbenchViewMappings.isPreviewAction(action)) {
                String key = frozen.selectedEntryKey().orElseThrow();
                applySceneAction(key, action);
                return WorkbenchViewReducer.preview(frozen, action);
            }
            if (WorkbenchViewMappings.isNativePropertyAction(action)) {
                return nativePropertyEdit(frozen, action);
            }
            if (WorkbenchViewMappings.isPaintAction(action)) {
                return paintEdit(frozen, action);
            }
            return frozen;
        } catch (IOException | RuntimeException failure) {
            return rejected(
                    frozen,
                    FailureDetails.message(failure, "WORKBENCH_ACTION_FAILED"));
        }
    }

    @Override
    public WorkbenchViewModel<ManagedAuthoringDraft> rejected(
            WorkbenchViewModel<ManagedAuthoringDraft> frozen,
            String diagnosticCode) {
        return WorkbenchViewReducer.status(
                frozen,
                Component.translatable(
                        "gui.autoseamblend.status.failed",
                        Component.literal(diagnosticCode)),
                true,
                false);
    }

    @Override
    public WorkbenchViewModel<ManagedAuthoringDraft> pending(
            WorkbenchViewModel<ManagedAuthoringDraft> frozen,
            WorkbenchActionPort.OperationToken token) {
        return WorkbenchViewMappings.pending(frozen, token);
    }

    @Override
    public CompletionStage<WorkbenchOperationCoordinator.OperationResult<ManagedAuthoringDraft>> start(
            WorkbenchOperationCoordinator.FrozenOperation<ManagedAuthoringDraft> operation) {
        if (operation.token().kind() == WorkbenchActionPort.OperationToken.Kind.SAVE) {
            return save(operation);
        }
        return export(operation);
    }

    @Override
    public boolean cancel(WorkbenchActionPort.OperationToken token) {
        if (!token.cancellable()) {
            return false;
        }
        cancellations.computeIfAbsent(token, ignored -> new AtomicBoolean()).set(true);
        ManagedExportService.ExportHandle handle = exports.get(token);
        if (handle != null) {
            handle.cancel();
        }
        return true;
    }

    @Override
    public WorkbenchOperationCoordinator.OperationResult<ManagedAuthoringDraft> failed(
            WorkbenchViewModel<ManagedAuthoringDraft> frozen,
            Throwable failure) {
        String code = FailureDetails.message(failure, "WORKBENCH_OPERATION_FAILED");
        return new WorkbenchOperationCoordinator.OperationResult<>(
                rejected(frozen, code),
                new WorkbenchActionPort.Failed(code));
    }

    private WorkbenchViewModel<ManagedAuthoringDraft> addTarget(
            WorkbenchViewModel<ManagedAuthoringDraft> frozen,
            String blockId,
            long activeGeneration) {
        GenerationPublicationState.requireCurrentWorkbenchGeneration(
                activeGeneration, ReloadPublication.current().generation());
        Optional<WorkbenchTargetCatalog.Entry> added =
                WorkbenchTargetCatalog.newManaged(
                        blockId,
                        family,
                        ReloadPublication.current());
        if (added.isEmpty()) {
            return rejected(frozen, "TARGET_UNAVAILABLE");
        }
        WorkbenchTargetCatalog.Entry entry = added.orElseThrow();
        WorkbenchDocument.Item<ManagedAuthoringDraft> item = new WorkbenchDocument.Item<>(
                entry.entryKey(),
                entry.entryId(),
                entry.documentPath(),
                entry.family(),
                entry.draft(),
                entry.method(),
                entry.compatibility(),
                false,
                false,
                true,
                true);
        WorkbenchDocument<ManagedAuthoringDraft> document = frozen.document().add(item);
        if (document == frozen.document()) {
            return rejected(frozen, "TARGET_ALREADY_PRESENT");
        }
        sources.put(entry.entryKey(), entry);
        List<TargetRowView> available =
                WorkbenchViewMappings.availableCandidates(
                        rows(document),
                        frozen.availableTargets());
        return WorkbenchViewReducer.copy(
                frozen,
                document,
                WorkbenchMode.TARGET_LIBRARY,
                rows(document),
                available,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Component.translatable("gui.autoseamblend.status.target_added"),
                true,
                false);
    }

    private WorkbenchViewModel<ManagedAuthoringDraft> showMode(
            WorkbenchViewModel<ManagedAuthoringDraft> frozen,
            String entryKey,
            WorkbenchMode mode,
            long activeGeneration) {
        GenerationPublicationState.requireCurrentWorkbenchGeneration(
                activeGeneration, ReloadPublication.current().generation());
        if (mode == WorkbenchMode.TARGET_LIBRARY) {
            scenes.remove(entryKey);
            return WorkbenchViewReducer.copy(
                    frozen,
                    frozen.document(),
                    mode,
                    frozen.targets(),
                    frozen.availableTargets(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Component.translatable("gui.autoseamblend.status.ready"),
                    true,
                    false);
        }
        WorkbenchDocument.Item<ManagedAuthoringDraft> item = requireItem(frozen, entryKey);
        Optional<PreviewViewModel> preview = Optional.empty();
        Optional<PaintViewModel> paint = Optional.empty();
        Optional<NativePropertiesViewModel> propertyView = Optional.empty();
        if (mode == WorkbenchMode.CONNECTION_PREVIEW) {
            if (item.draft().isEmpty()) {
                return rejected(frozen, "TARGET_SURFACE_REQUIRED");
            }
            try {
                NativePropertyDocumentLoader property = ensureProperty(entryKey, frozen);
                ManagedAuthoringDraft draft = item.draft().orElseThrow();
                Block block = block(draft.targetBlockId());
                List<net.minecraft.world.level.block.state.BlockState> receivers =
                        MinecraftNativeBlockSelectorResolver.representativeStates(
                                property.document().matchingSelector());
                List<net.minecraft.world.level.block.state.BlockState> connections =
                        MinecraftNativeBlockSelectorResolver.representativeStates(
                                property.document().connectionSelector());
                PreviewSceneState scene = previewScene(draft, block, receivers, connections);
                scenes.put(entryKey, scene);
                preview = Optional.of(WorkbenchViewMappings.withSurface(
                        WorkbenchViewMappings.unavailable(Component.empty(), Direction.NORTH),
                        new GenerationOnlyPreviewSurface(activeGeneration)));
            } catch (IOException | RuntimeException failure) {
                scenes.remove(entryKey);
                preview = Optional.of(WorkbenchViewMappings.unavailable(
                        Component.translatable(
                                "gui.autoseamblend.preview.unavailable",
                                Component.literal(FailureDetails.message(
                                        failure,
                                        "PREVIEW_UNAVAILABLE"))),
                        Direction.NORTH));
            }
        } else if (mode == WorkbenchMode.TEXTURE_PAINT) {
            try {
                TexturePaintAdapter paintAdapter = ensurePaint(entryKey, frozen);
                paint = Optional.of(paintView(paintAdapter.document(), entryKey));
            } catch (IOException | RuntimeException failure) {
                return rejected(
                        frozen,
                        FailureDetails.message(failure, "PAINT_UNAVAILABLE"));
            }
        } else if (mode == WorkbenchMode.NATIVE_PROPERTIES) {
            try {
                NativePropertyDocumentLoader document = ensureProperty(entryKey, frozen);
                propertyView = Optional.of(propertyView(document));
                ensureCandidatesForProperties();
            } catch (IOException | RuntimeException failure) {
                return rejected(
                        frozen,
                        FailureDetails.message(failure, "PROPERTIES_UNAVAILABLE"));
            }
        }
        return WorkbenchViewReducer.copy(
                frozen,
                frozen.document(),
                mode,
                frozen.targets(),
                frozen.availableTargets(),
                Optional.of(entryKey),
                preview,
                paint,
                propertyView,
                Component.translatable("gui.autoseamblend.status.ready"),
                true,
                false);
    }

    private TexturePaintAdapter ensurePaint(
            String key,
            WorkbenchViewModel<ManagedAuthoringDraft> view) throws IOException {
        TexturePaintAdapter existing = paints.get(key);
        if (existing != null) {
            return existing;
        }
        WorkbenchDocument.Item<ManagedAuthoringDraft> item = requireItem(view, key);
        ManagedAuthoringDraft draft = item.draft().orElseThrow(
                () -> new IllegalStateException("TARGET_RECEIVER_REQUIRED"));
        TexturePaintAdapter created =
                ConnectionTextureSources.capture(
                                minecraft,
                                item.family(),
                                draft,
                                ensureProperty(key, view))
                        .map(TexturePaintAdapter::new)
                        .orElseThrow(() -> new IOException("CONNECTION_TEXTURE_UNAVAILABLE"));
        if (!created.document().available()) {
            throw new IOException("CONNECTION_TEXTURE_UNAVAILABLE");
        }
        paints.put(key, created);
        paintFaces.putIfAbsent(key, Direction.NORTH);
        return created;
    }

    private NativePropertyDocumentLoader ensureProperty(
            String key,
            WorkbenchViewModel<ManagedAuthoringDraft> view) throws IOException {
        NativePropertyDocumentLoader existing = properties.get(key);
        if (existing != null) {
            return existing;
        }
        WorkbenchDocument.Item<ManagedAuthoringDraft> item = requireItem(view, key);
        NativePropertyDocumentLoader loaded = item.draft().isPresent()
                ? NativePropertyDocumentLoader.load(
                        minecraft,
                        item.family(),
                        item.draft().orElseThrow(),
                        item.documentPath())
                : NativePropertyDocumentLoader.loadTargetless(
                        minecraft,
                        item.family(),
                        item.documentPath());
        properties.put(key, loaded);
        return loaded;
    }

    private static PreviewSceneState previewScene(
            ManagedAuthoringDraft draft,
            Block block,
            List<net.minecraft.world.level.block.state.BlockState> receivers,
            List<net.minecraft.world.level.block.state.BlockState> connections) {
        if (draft.resolvedMethod().overlayCapable()) {
            return PreviewSceneState.additiveOverlay(
                    block.defaultBlockState(), receivers, connections);
        }
        if (draft.resolvedMethod() == ConnectionMethod.NONE
                || draft.resolvedMethod() == ConnectionMethod.FIXED
                || draft.resolvedMethod() == ConnectionMethod.TOP) {
            return PreviewSceneState.passthrough(block.defaultBlockState(), receivers);
        }
        return PreviewSceneState.connection(
                block.defaultBlockState(), receivers, connections);
    }

    private WorkbenchViewModel<ManagedAuthoringDraft> nativePropertyEdit(
            WorkbenchViewModel<ManagedAuthoringDraft> frozen,
            WorkbenchAction action) throws IOException {
        String key = frozen.selectedEntryKey().orElseThrow();
        NativePropertyDocumentLoader nextProperty = propertyEdit(ensureProperty(key, frozen), action);
        properties.put(key, nextProperty);
        WorkbenchDocument.Item<ManagedAuthoringDraft> item = requireItem(frozen, key);
        WorkbenchDocument.Item<ManagedAuthoringDraft> updated = syncItem(item, nextProperty);
        WorkbenchDocument<ManagedAuthoringDraft> document = frozen.document().replace(updated);
        return WorkbenchViewReducer.copy(
                frozen,
                document,
                frozen.mode(),
                rows(document),
                frozen.availableTargets(),
                frozen.selectedEntryKey(),
                frozen.preview(),
                frozen.paint(),
                Optional.of(propertyView(nextProperty)),
                Component.translatable("gui.autoseamblend.status.modified"),
                true,
                false);
    }

    private WorkbenchDocument.Item<ManagedAuthoringDraft> syncItem(
            WorkbenchDocument.Item<ManagedAuthoringDraft> item,
            NativePropertyDocumentLoader property) {
        com.kltyton.autoseamblend.authoring.property.NativePropertyDocument document =
                property.document();
        WorkbenchDocument.Item<ManagedAuthoringDraft> updated = item;
        if (item.draft().isPresent()) {
            ManagedAuthoringDraft old = item.draft().orElseThrow();
            ConnectionMethod requested = document.authoringMethod();
            ConnectionMethod resolved = requested == ConnectionMethod.AUTO
                    ? ManagedAuthoringProjectDrafts.resolvedAuto(block(old.targetBlockId()))
                            .filter(method -> method != ConnectionMethod.AUTO)
                            .orElseThrow(() -> new IllegalStateException("AUTO_SURFACE_UNRESOLVED"))
                    : requested;
            updated = item.withDraft(new ManagedAuthoringDraft(
                    old.targetBlockId(),
                    old.sourceTextureId(),
                    old.originalModelId(),
                    requested,
                    resolved,
                    document.authoringCompatibility(),
                    old.pane()));
        } else {
            updated = item.withTargetlessMethod(document.authoringMethod())
                    .withTargetlessCompatibility(document.authoringCompatibility());
        }
        return updated.withEntryId(document.displayEntryId());
    }

    private static NativePropertyDocumentLoader propertyEdit(
            NativePropertyDocumentLoader source,
            WorkbenchAction action) {
        return source.withDocument(
                NativePropertyDocumentActions.reduce(source.document(), action));
    }

    private void applySceneAction(String key, WorkbenchAction action) {
        PreviewSceneState scene = scenes.get(key);
        if (scene == null) {
            throw new IllegalStateException("PREVIEW_UNAVAILABLE");
        }
        if (action instanceof WorkbenchAction.ToggleNeighbor value) {
            scene.toggle(toPreviewPosition(value.position()));
        } else if (action instanceof WorkbenchAction.ObserveFace value) {
            scene.setHoveredFace(new PreviewSceneState.HoveredFace(Optional.empty(), value.face()));
        } else if (action instanceof WorkbenchAction.CycleReceiver) {
            scene.cycleCenter();
        } else if (action instanceof WorkbenchAction.ClearNeighbors) {
            scene.clearNeighbors();
        }
    }

    private WorkbenchViewModel<ManagedAuthoringDraft> paintEdit(
            WorkbenchViewModel<ManagedAuthoringDraft> frozen,
            WorkbenchAction action) {
        String key = frozen.selectedEntryKey().orElseThrow();
        TexturePaintAdapter adapter = paints.get(key);
        if (adapter == null) {
            return rejected(frozen, "PAINT_UNAVAILABLE");
        }
        TexturePaintDocument document = adapter.document();
        if (action instanceof WorkbenchAction.ChoosePaintTool value) document.setTool(value.tool());
        if (action instanceof WorkbenchAction.ChoosePaintColor value) document.setColor(value.straightArgb());
        if (action instanceof WorkbenchAction.SelectPaintSlot value) document.selectSlot(value.slot());
        if (action instanceof WorkbenchAction.SelectPaintFace value) paintFaces.put(key, value.face());
        if (action instanceof WorkbenchAction.PaintStrokeStarted) document.beginStroke();
        if (action instanceof WorkbenchAction.PaintPixel value) document.apply(value.x(), value.y());
        if (action instanceof WorkbenchAction.PaintStrokeEnded) document.endStroke();
        if (action instanceof WorkbenchAction.CycleBrushSize) document.cycleBrushSize();
        if (action instanceof WorkbenchAction.UndoPaint) document.undo();
        if (action instanceof WorkbenchAction.RedoPaint) document.redo();
        WorkbenchDocument.Item<ManagedAuthoringDraft> item = requireItem(frozen, key);
        WorkbenchDocument<ManagedAuthoringDraft> next =
                WorkbenchViewMappings.syncPaintDocument(
                        frozen.document(),
                        item,
                        document);
        PaintViewModel paint = paintView(document, key);
        return WorkbenchViewReducer.copy(
                frozen,
                next,
                frozen.mode(),
                rows(next),
                frozen.availableTargets(),
                frozen.selectedEntryKey(),
                frozen.preview(),
                Optional.of(paint),
                frozen.properties(),
                document.dirty()
                        ? Component.translatable("gui.autoseamblend.status.modified")
                        : frozen.operationStatus(),
                true,
                false);
    }

    void paintChanged(
            com.kltyton.autoseamblend.frontend.controller.UilibWorkbenchController<ManagedAuthoringDraft> session) {
        String key = session.view().selectedEntryKey().orElse(null);
        if (key == null) {
            return;
        }
        TexturePaintAdapter adapter = paints.get(key);
        if (adapter == null) {
            return;
        }
        WorkbenchDocument.Item<ManagedAuthoringDraft> item = requireItem(session.view(), key);
        WorkbenchDocument<ManagedAuthoringDraft> next =
                WorkbenchViewMappings.syncPaintDocument(
                        session.view().document(),
                        item,
                        adapter.document());
        WorkbenchViewModel<ManagedAuthoringDraft> view = WorkbenchViewReducer.copy(
                session.view(),
                next,
                session.view().mode(),
                rows(next),
                session.view().availableTargets(),
                session.view().selectedEntryKey(),
                session.view().preview(),
                Optional.of(paintView(adapter.document(), key)),
                session.view().properties(),
                adapter.document().dirty()
                        ? Component.translatable("gui.autoseamblend.status.modified")
                        : session.view().operationStatus(),
                true,
                false);
        session.publish(session.publicationVersion(), view);
    }

    void previewChanged(
            com.kltyton.autoseamblend.frontend.controller.UilibWorkbenchController<ManagedAuthoringDraft> session) {
        String key = session.view().selectedEntryKey().orElse(null);
        PreviewSceneState scene = key == null ? null : scenes.get(key);
        PreviewViewModel current = session.view().preview().orElse(null);
        if (scene == null || current == null) {
            return;
        }
        Set<PreviewViewModel.NeighborPosition> neighbors = new LinkedHashSet<>();
        scene.neighbors().keySet().forEach(position -> neighbors.add(fromPreviewPosition(position)));
        Direction face = scene.hoveredFace().map(PreviewSceneState.HoveredFace::face)
                .orElse(current.observedFace());
        WorkbenchViewModel<ManagedAuthoringDraft> view = WorkbenchViewMappings.withPreview(
                session.view(), neighbors, face, current.receiverVariant());
        session.publish(session.publicationVersion(), view);
    }

    Optional<PreviewSceneState> previewScene(String key) {
        return Optional.ofNullable(scenes.get(key));
    }

    Optional<TexturePaintAdapter> paint(String key) {
        return Optional.ofNullable(paints.get(key));
    }

    List<TargetRowView> propertyRows(
            List<NativePropertiesViewModel.SelectorCandidate> values) {
        return values.stream()
                .map(value -> row(
                        "property:" + value.blockId(),
                        value.blockId(),
                        Optional.of(value.blockId()),
                        family,
                        ConnectionMethod.AUTO,
                        true,
                        false,
                        false,
                        false,
                        true,
                        value.displayName(),
                        value.icon()))
                .toList();
    }

    void ensureCandidates(
            com.kltyton.autoseamblend.frontend.controller.UilibWorkbenchController<ManagedAuthoringDraft> session) {
        if (closed.get() || planner != null) {
            return;
        }
        candidates.clear();
        selectors.clear();
        LinkedHashSet<String> existing = new LinkedHashSet<>();
        sources.values().forEach(entry -> entry.receiverBlockId().ifPresent(existing::add));
        candidateExistingReceivers = Set.copyOf(existing);
        planner = new WorkbenchCandidateScanPlanner<>(SCAN_BATCH, SCAN_BUDGET);
        planner.begin(BuiltInRegistries.BLOCK);
        WorkbenchViewModel<ManagedAuthoringDraft> view = session.view();
        WorkbenchViewModel<ManagedAuthoringDraft> loading = WorkbenchViewReducer.copy(
                view,
                view.document(),
                view.mode(),
                view.targets(),
                WorkbenchViewMappings.availableCandidates(
                        current.targets(),
                        List.copyOf(candidates)),
                List.copyOf(selectors),
                view.selectedEntryKey(),
                view.preview(),
                view.paint(),
                view.properties(),
                Component.translatable("gui.autoseamblend.status.candidates_loading"),
                true,
                false);
        // 中文：扫描启动只发布一次不可变 loading 快照；English: Publish one immutable loading snapshot at scan start.
        session.publish(session.publicationVersion(), loading);
    }

    void tickCandidates(
            com.kltyton.autoseamblend.frontend.controller.UilibWorkbenchController<ManagedAuthoringDraft> session) {
        if (closed.get() || planner == null) {
            return;
        }
        WorkbenchCandidateScanPlanner.Slice slice = planner.tick(
                System::nanoTime,
                this::indexCandidate);
        if (slice.complete()) {
            planner = null;
            candidateExistingReceivers = Set.of();
        }
        WorkbenchViewModel<ManagedAuthoringDraft> current = session.view();
        WorkbenchViewModel<ManagedAuthoringDraft> next = WorkbenchViewReducer.copy(
                current,
                current.document(),
                current.mode(),
                current.targets(),
                List.copyOf(candidates),
                List.copyOf(selectors),
                current.selectedEntryKey(),
                current.preview(),
                current.paint(),
                current.properties(),
                slice.complete()
                        ? Component.translatable("gui.autoseamblend.status.ready")
                        : Component.translatable("gui.autoseamblend.status.candidates_loading"),
                true,
                false);
        session.publish(session.publicationVersion(), next);
    }

    private void ensureCandidatesForProperties() {
        if (planner == null) {
            candidates.clear();
            selectors.clear();
            LinkedHashSet<String> existing = new LinkedHashSet<>();
            sources.values().forEach(entry -> entry.receiverBlockId().ifPresent(existing::add));
            candidateExistingReceivers = Set.copyOf(existing);
            planner = new WorkbenchCandidateScanPlanner<>(SCAN_BATCH, SCAN_BUDGET);
            planner.begin(BuiltInRegistries.BLOCK);
        }
    }

    private void indexCandidate(Block block) {
        if (block == Blocks.AIR) {
            return;
        }
        String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
        Component name = block.getName();
        ItemStack icon = new ItemStack(block);
        selectors.add(new NativePropertiesViewModel.SelectorCandidate(blockId, name, icon));
        if (candidateExistingReceivers.contains(blockId)
                || ManagedAuthoringProjectDrafts.forBlock(block, family).isEmpty()) {
            return;
        }
        candidates.add(row(
                blockId,
                blockId,
                Optional.of(blockId),
                family,
                ConnectionMethod.AUTO,
                true,
                true,
                false,
                true,
                true));
    }

    private CompletionStage<WorkbenchOperationCoordinator.OperationResult<ManagedAuthoringDraft>> save(
            WorkbenchOperationCoordinator.FrozenOperation<ManagedAuthoringDraft> operation) {
        try {
            List<ManagedAuthoringProject> projects = projects(operation.view());
            Map<String, byte[]> editedFiles = editedFiles();
            List<NativePropertyPatch> patches = propertyPatches(operation.view());
            return ManagedSaveCoordinator.instance(
                            minecraft,
                            NativeDocumentOperations.shared())
                    .save(projects, editedFiles, patches)
                    .thenApply(result -> {
                        if (!result.success()) {
                            return failed(operation.view(), new IllegalStateException(result.detail()));
                        }
                        WorkbenchViewModel<ManagedAuthoringDraft> settled = WorkbenchViewReducer.copy(
                                operation.view(),
                                operation.view().document()
                                        .markPersisted(operation.token().submittedRevision()),
                                operation.view().mode(),
                                rows(operation.view().document()),
                                operation.view().availableTargets(),
                                operation.view().selectedEntryKey(),
                                operation.view().preview(),
                                operation.view().paint(),
                                operation.view().properties(),
                                Component.translatable("gui.autoseamblend.status.saved"),
                                true,
                                false);
                        return new WorkbenchOperationCoordinator.OperationResult<>(
                                settled,
                                new WorkbenchActionPort.SaveReceipt(
                                        operation.token().submittedRevision(),
                                        1,
                                        result.workspaceCreated(),
                                        result.changedPaths(),
                                        result.selectionChanged()));
                    });
        } catch (IOException | RuntimeException failure) {
            return CompletableFuture.completedFuture(failed(operation.view(), failure));
        }
    }

    private CompletionStage<WorkbenchOperationCoordinator.OperationResult<ManagedAuthoringDraft>> export(
            WorkbenchOperationCoordinator.FrozenOperation<ManagedAuthoringDraft> operation) {
        WorkbenchActionPort.OperationToken token = operation.token();
        AtomicBoolean cancelled = cancellations.computeIfAbsent(token, ignored -> new AtomicBoolean());
        CompletableFuture<WorkbenchOperationCoordinator.OperationResult<ManagedAuthoringDraft>> result =
                new CompletableFuture<>();
        new SystemExportDestinationPicker(minecraft).choose().completion().whenComplete((selected, failure) ->
                minecraft.execute(() -> {
                    if (failure != null) {
                        cancellations.remove(token);
                        result.complete(failed(operation.view(), failure));
                        return;
                    }
                    if (cancelled.get() || selected == null || selected.isEmpty()) {
                        cancellations.remove(token);
                        result.complete(cancelled(operation.view(), "EXPORT_CANCELLED"));
                        return;
                    }
                    try {
                        Map<String, List<ExportDraft>>
                                workspace = ExportDrafts.workspaceTargets(
                                        minecraft,
                                        FabricEngineRegistry.RUNTIME.current(),
                                        exportWorkspaceTargets(operation.view()),
                                        editedSourceOverrides());
                        ManagedExportRequest request = new ManagedExportRequest(
                                ManagedExportProfile.BAKED,
                                selected.orElseThrow(),
                                false,
                                false);
                        ManagedExportService.ExportHandle handle =
                                ManagedExportService.instance(
                                                minecraft,
                                        FabricEngineRegistry.RUNTIME.current(),
                                        FabricExportMetadata::metadata)
                                        .exportWorkspace(request, workspace);
                        exports.put(token, handle);
                        handle.future().whenComplete((exported, exportFailure) -> {
                            exports.remove(token);
                            cancellations.remove(token);
                            if (cancelled.get()) {
                                result.complete(cancelled(operation.view(), "EXPORT_CANCELLED"));
                                return;
                            }
                            if (exportFailure != null || exported == null) {
                                result.complete(failed(operation.view(),
                                        exportFailure == null
                                                ? new IllegalStateException("EXPORT_FAILED")
                                                : exportFailure));
                                return;
                            }
                            WorkbenchViewModel<ManagedAuthoringDraft> settled = WorkbenchViewReducer.status(
                                    operation.view(),
                                    Component.translatable(
                                            "gui.autoseamblend.status.exported",
                                            Component.literal(exported.destination().toString()),
                                            exported.partitions().size()),
                                    true,
                                    false);
                            result.complete(new WorkbenchOperationCoordinator.OperationResult<>(
                                    settled,
                                    new WorkbenchActionPort.BakedExportReceipt(
                                            token.submittedRevision(),
                                            exported.destination().toString(),
                                            exported.partitions().keySet().stream().toList())));
                        });
                    } catch (IOException | RuntimeException exception) {
                        cancellations.remove(token);
                        result.complete(failed(operation.view(), exception));
                    }
                }));
        return result;
    }

    private WorkbenchOperationCoordinator.OperationResult<ManagedAuthoringDraft> cancelled(
            WorkbenchViewModel<ManagedAuthoringDraft> frozen,
            String code) {
        return new WorkbenchOperationCoordinator.OperationResult<>(
                WorkbenchViewReducer.status(
                        frozen,
                        Component.translatable("gui.autoseamblend.status.export_cancelled"),
                        true,
                        false),
                new WorkbenchActionPort.Cancelled(code));
    }

    private List<ManagedAuthoringProject> projects(
            WorkbenchViewModel<ManagedAuthoringDraft> view) {
        LinkedHashMap<EngineFamily, ArrayList<ManagedAuthoringRule>> grouped = new LinkedHashMap<>();
        view.document().items().values().stream()
                .filter(item -> item.managedAtOpen() || item.newlyAdded() || item.modified())
                .forEach(item -> item.draft().ifPresent(draft -> grouped
                        .computeIfAbsent(item.family(), ignored -> new ArrayList<>())
                        .add(ManagedAuthoringProjectDrafts.createRule(draft))));
        ArrayList<ManagedAuthoringProject> result = new ArrayList<>();
        grouped.forEach((key, rules) -> result.add(ManagedAuthoringTemplates.create(key, rules)));
        return List.copyOf(result);
    }

    private List<NativePropertyPatch> propertyPatches(
            WorkbenchViewModel<ManagedAuthoringDraft> view) throws IOException {
        ArrayList<NativePropertyPatch> patches = new ArrayList<>();
        for (Map.Entry<String, WorkbenchDocument.Item<ManagedAuthoringDraft>> entry :
                view.document().items().entrySet()) {
            WorkbenchDocument.Item<ManagedAuthoringDraft> item = entry.getValue();
            if (item.managedAtOpen() || item.newlyAdded() || item.modified()) {
                patches.add(ensureProperty(entry.getKey(), view)
                        .document()
                        .managedPatch());
            }
        }
        return List.copyOf(patches);
    }

    private Map<String, byte[]> editedFiles() throws IOException {
        ArrayList<Map<String, byte[]>> files = new ArrayList<>();
        for (TexturePaintAdapter paint : paints.values()) {
            files.add(paint.editedFiles());
        }
        return WorkbenchSourceConflictReducer.merge(files, Arrays::equals, "FACE_TEXTURE_EDIT_CONFLICT");
    }

    private Map<String, TextureSourceSnapshot> editedSourceOverrides() {
        ArrayList<Map<String, TextureSourceSnapshot>> values = new ArrayList<>();
        for (TexturePaintAdapter paint : paints.values()) {
            values.add(paint.editedSources());
        }
        return WorkbenchSourceConflictReducer.merge(
                values,
                (left, right) -> WorkbenchSourceConflictReducer.equivalent(
                        sourceShape(left), sourceShape(right)),
                "FACE_TEXTURE_EDIT_CONFLICT");
    }

    private List<ExportDraftPlanning.WorkspaceTarget> exportWorkspaceTargets(
            WorkbenchViewModel<ManagedAuthoringDraft> view) throws IOException {
        ArrayList<ExportDraftPlanning.WorkspaceTarget> result = new ArrayList<>();
        for (Map.Entry<String, WorkbenchDocument.Item<ManagedAuthoringDraft>> entry :
                view.document().items().entrySet()) {
            result.add(new ExportDraftPlanning.WorkspaceTarget(
                    entry.getValue().draft(),
                    Optional.of(ensureProperty(entry.getKey(), view)
                            .document()
                            .snapshot())));
        }
        return List.copyOf(result);
    }

    private static WorkbenchSourceConflictReducer.TextureSourceShape sourceShape(
            TextureSourceSnapshot value) {
        return new WorkbenchSourceConflictReducer.TextureSourceShape(
                value.sheetWidth(),
                value.sheetHeight(),
                value.frameWidth(),
                value.frameHeight(),
                value.frameIndices(),
                value.firstFrameStraightArgb(),
                value.sourceMetadata());
    }

    private static WorkbenchDocument.Item<ManagedAuthoringDraft> requireItem(
            WorkbenchViewModel<ManagedAuthoringDraft> view,
            String key) {
        return view.document().item(key).orElseThrow(
                () -> new IllegalArgumentException("TARGET_SURFACE_REQUIRED"));
    }

    private static Block block(String id) {
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
        if (block == null || block == Blocks.AIR) {
            throw new IllegalArgumentException("TARGET_BLOCK_UNKNOWN");
        }
        return block;
    }

    private List<TargetRowView> rows(WorkbenchDocument<ManagedAuthoringDraft> document) {
        return document.items().entrySet().stream().map(entry -> {
            WorkbenchTargetCatalog.Entry source = sources.get(entry.getKey());
            WorkbenchDocument.Item<ManagedAuthoringDraft> item = entry.getValue();
            return row(
                    entry.getKey(),
                    item.entryId(),
                    source == null ? Optional.empty() : source.receiverBlockId(),
                    item.family(),
                    item.method(),
                    item.compatibility(),
                    source != null && source.managed(),
                    source != null && source.configured(),
                    item.draft().isPresent(),
                    true);
        }).toList();
    }

    private static TargetRowView row(
            String key,
            String entryId,
            Optional<String> receiver,
            EngineFamily family,
            ConnectionMethod method,
            boolean compatibility,
            boolean managed,
            boolean configured,
            boolean surface,
            boolean editable) {
        Block block = receiver.map(FabricWorkbenchNativePort::block).orElse(null);
        return row(
                key,
                entryId,
                receiver,
                family,
                method,
                compatibility,
                managed,
                configured,
                surface,
                editable,
                block == null ? Component.literal(entryId) : block.getName(),
                block == null ? ItemStack.EMPTY : new ItemStack(block));
    }

    private static TargetRowView row(
            String key,
            String entryId,
            Optional<String> receiver,
            EngineFamily family,
            ConnectionMethod method,
            boolean compatibility,
            boolean managed,
            boolean configured,
            boolean surface,
            boolean editable,
            Component displayName,
            ItemStack icon) {
        return WorkbenchViewMappings.targetRow(
                new WorkbenchViewMappings.TargetRowProjection(
                        key,
                        entryId,
                        receiver,
                        displayName,
                        icon,
                        family,
                        method,
                        compatibility,
                        managed,
                        configured,
                        surface,
                        surface,
                        editable,
                        editable));
    }

    private PaintViewModel paintView(TexturePaintDocument value, String key) {
        // 中文：绘画视图投影由 common 派生真实可编辑/撤销/重做状态，Loader 只提供当前面。
        // English: Paint view projection derives real editable/undo/redo state in
        // common; the loader only supplies the current face.
        return WorkbenchViewMappings.paint(
                value,
                paintFaces.getOrDefault(key, Direction.NORTH),
                Component.translatable(
                        "gui.autoseamblend.status.ready"));
    }

    private NativePropertiesViewModel propertyView(NativePropertyDocumentLoader value) {
        return NativePropertyDocumentViewProjection.project(value.document());
    }

    private static PreviewViewModel.NeighborPosition fromPreviewPosition(
            com.kltyton.autoseamblend.authoring.preview.PreviewNeighborPosition value) {
        return switch (value) {
            case FRONT -> PreviewViewModel.NeighborPosition.FRONT;
            case BACK -> PreviewViewModel.NeighborPosition.BACK;
            case UP -> PreviewViewModel.NeighborPosition.UP;
            case DOWN -> PreviewViewModel.NeighborPosition.DOWN;
            case LEFT -> PreviewViewModel.NeighborPosition.LEFT;
            case RIGHT -> PreviewViewModel.NeighborPosition.RIGHT;
            case LEFT_FRONT -> PreviewViewModel.NeighborPosition.LEFT_FRONT;
            case RIGHT_FRONT -> PreviewViewModel.NeighborPosition.RIGHT_FRONT;
            case LEFT_BACK -> PreviewViewModel.NeighborPosition.LEFT_BACK;
            case RIGHT_BACK -> PreviewViewModel.NeighborPosition.RIGHT_BACK;
        };
    }

    private static com.kltyton.autoseamblend.authoring.preview.PreviewNeighborPosition toPreviewPosition(
            PreviewViewModel.NeighborPosition value) {
        return switch (value) {
            case FRONT -> com.kltyton.autoseamblend.authoring.preview.PreviewNeighborPosition.FRONT;
            case BACK -> com.kltyton.autoseamblend.authoring.preview.PreviewNeighborPosition.BACK;
            case UP -> com.kltyton.autoseamblend.authoring.preview.PreviewNeighborPosition.UP;
            case DOWN -> com.kltyton.autoseamblend.authoring.preview.PreviewNeighborPosition.DOWN;
            case LEFT -> com.kltyton.autoseamblend.authoring.preview.PreviewNeighborPosition.LEFT;
            case RIGHT -> com.kltyton.autoseamblend.authoring.preview.PreviewNeighborPosition.RIGHT;
            case LEFT_FRONT -> com.kltyton.autoseamblend.authoring.preview.PreviewNeighborPosition.LEFT_FRONT;
            case RIGHT_FRONT -> com.kltyton.autoseamblend.authoring.preview.PreviewNeighborPosition.RIGHT_FRONT;
            case LEFT_BACK -> com.kltyton.autoseamblend.authoring.preview.PreviewNeighborPosition.LEFT_BACK;
            case RIGHT_BACK -> com.kltyton.autoseamblend.authoring.preview.PreviewNeighborPosition.RIGHT_BACK;
        };
    }

}
