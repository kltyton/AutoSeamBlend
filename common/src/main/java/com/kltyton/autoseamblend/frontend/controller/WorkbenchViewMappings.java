package com.kltyton.autoseamblend.frontend.controller;

import com.kltyton.autoseamblend.authoring.workbench.PaintTool;
import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDocument;
import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDraftFields;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.frontend.model.NativePropertiesViewModel;
import com.kltyton.autoseamblend.frontend.model.PaintViewModel;
import com.kltyton.autoseamblend.frontend.model.PreviewViewModel;
import com.kltyton.autoseamblend.frontend.model.TargetRowView;
import com.kltyton.autoseamblend.frontend.model.WorkbenchViewModel;
import com.kltyton.autoseamblend.frontend.paint.TexturePaintDocument;
import com.kltyton.autoseamblend.frontend.port.WorkbenchActionPort;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;

/**
 * 中文：跨 Loader 的工作台 DTO 投影与动作分类；不触及注册表、原生文档或渲染服务。
 *
 * English: Loader-neutral workbench DTO projection and action classification;
 * it never touches registries, native documents, or rendering services.
 */
public final class WorkbenchViewMappings {
    private WorkbenchViewMappings() {}

    /** 中文：使用同一切片规划器完成启动期候选物化。 / English: Materializes startup candidates through the shared sliced planner. */
    public static <C> List<C> scan(Iterable<C> source) {
        WorkbenchCandidateScanPlanner<C> planner =
                new WorkbenchCandidateScanPlanner<>(64, 2_000_000L);
        planner.begin(Objects.requireNonNull(source, "source"));
        ArrayList<C> result = new ArrayList<>();
        while (planner.active()) {
            planner.tick(System::nanoTime, result::add);
        }
        return List.copyOf(result);
    }

    public static boolean isPreviewAction(WorkbenchAction action) {
        return action instanceof WorkbenchAction.ToggleNeighbor
                || action instanceof WorkbenchAction.ObserveFace
                || action instanceof WorkbenchAction.CycleReceiver
                || action instanceof WorkbenchAction.ClearNeighbors;
    }

    public static boolean isNativePropertyAction(WorkbenchAction action) {
        return action instanceof WorkbenchAction.SetNativeProperty
                || action instanceof WorkbenchAction.SetNativeEntryId
                || action instanceof WorkbenchAction.ToggleNativeFace
                || action instanceof WorkbenchAction.CycleNativeConnectionBasis
                || action instanceof WorkbenchAction.CycleNativeRenderLayer
                || action instanceof WorkbenchAction.SetNativeTintBlock
                || action instanceof WorkbenchAction.AddNativeSelectorBlock
                || action instanceof WorkbenchAction.RemoveNativeSelectorEntry
                || action instanceof WorkbenchAction.MoveNativeSelectorEntry
                || action instanceof WorkbenchAction.ToggleNativeSelectorProperty
                || action instanceof WorkbenchAction.CycleAthenaConnection;
    }

    public static boolean isPaintAction(WorkbenchAction action) {
        return action instanceof WorkbenchAction.ChoosePaintTool
                || action instanceof WorkbenchAction.ChoosePaintColor
                || action instanceof WorkbenchAction.SelectPaintSlot
                || action instanceof WorkbenchAction.SelectPaintFace
                || action instanceof WorkbenchAction.PaintStrokeStarted
                || action instanceof WorkbenchAction.PaintPixel
                || action instanceof WorkbenchAction.PaintStrokeEnded
                || action instanceof WorkbenchAction.CycleBrushSize
                || action instanceof WorkbenchAction.UndoPaint
                || action instanceof WorkbenchAction.RedoPaint;
    }

    /** 中文：统一保存/导出等待态文案与交互门。 / English: Uniformly maps save/export pending status and its interaction gate. */
    public static <T extends WorkbenchDraftFields> WorkbenchViewModel<T> pending(
            WorkbenchViewModel<T> source,
            WorkbenchActionPort.OperationToken token) {
        Objects.requireNonNull(token, "token");
        String status = token.kind() == WorkbenchActionPort.OperationToken.Kind.SAVE
                ? "gui.autoseamblend.status.saving"
                : "gui.autoseamblend.status.export_picker_open";
        return WorkbenchViewReducer.status(
                Objects.requireNonNull(source, "source"),
                Component.translatable(status),
                false,
                true);
    }

    public static TargetRowView targetRow(TargetRowProjection value) {
        Objects.requireNonNull(value, "value");
        return new TargetRowView(
                value.entryKey(),
                value.entryId(),
                value.receiverBlockId(),
                value.displayName(),
                value.icon(),
                value.family(),
                value.method(),
                value.compatibility(),
                value.managed(),
                value.configured(),
                value.previewEnabled(),
                value.paintEnabled(),
                value.propertiesEnabled(),
                value.editable());
    }

    public static TargetRowView availableTargetRow(TargetRowProjection value) {
        Objects.requireNonNull(value, "value");
        return targetRow(new TargetRowProjection(
                value.entryKey(),
                value.entryId(),
                value.receiverBlockId(),
                value.displayName(),
                value.icon(),
                value.family(),
                ConnectionMethod.AUTO,
                true,
                false,
                false,
                false,
                false,
                false,
                value.editable()));
    }

    /** 中文：从候选行中过滤接收方块已存在于目标库的行，避免同一方块重复添加。 / English: Filters candidate rows whose receiver block already exists in the target library so a block cannot be added twice. */
    public static List<TargetRowView> availableCandidates(
            List<TargetRowView> targets,
            List<TargetRowView> candidates) {
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(candidates, "candidates");
        LinkedHashSet<String> existing = new LinkedHashSet<>();
        targets.forEach(row -> row.receiverBlockId()
                .ifPresent(existing::add));
        return candidates.stream()
                .filter(row -> row.receiverBlockId()
                        .filter(existing::contains)
                        .isEmpty())
                .toList();
    }

    /**
     * 中文：绘画文档变脏时同步会话文档 dirty 状态：条目未修改且有草稿时标记条目修改，
     * 否则仅触摸修订号；文档已脏或绘画未变脏时原样返回，避免无谓修订号增长。
     *
     * English: Syncs the session-document dirty state from a paint document.
     * Marks the item modified when it is unmodified and has a draft; otherwise
     * touches the revision. Returns the source unchanged when the document is
     * already dirty or the paint document is clean, avoiding revision churn.
     */
    public static <T extends WorkbenchDraftFields>
            WorkbenchDocument<T> syncPaintDocument(
                    WorkbenchDocument<T> source,
                    WorkbenchDocument.Item<T> item,
                    TexturePaintDocument paint) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(paint, "paint");
        if (!paint.dirty() || source.dirty()) {
            return source;
        }
        if (!item.modified() && item.draft().isPresent()) {
            return source.replace(
                    item.withDraft(item.draft().orElseThrow()));
        }
        return source.touch();
    }

    public static PaintViewModel paint(PaintProjection value) {
        Objects.requireNonNull(value, "value");
        return new PaintViewModel(
                value.width(),
                value.height(),
                value.straightArgb(),
                value.selectedFace(),
                value.slots(),
                value.selectedSlot(),
                value.selectedSynthetic(),
                value.tool(),
                value.color(),
                value.brushSize(),
                value.editable(),
                value.canUndo(),
                value.canRedo(),
                value.status());
    }

    /**
     * 中文：从真实绘画文档投影不可变绘画视图，可编辑/撤销/重做状态由真实历史与槽位证据派生。
     *
     * English: Projects an immutable paint view from the real paint document;
     * editable, undo, and redo state derive from the real slot evidence and
     * history stacks.
     */
    public static PaintViewModel paint(
            TexturePaintDocument value,
            Direction selectedFace,
            Component status) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(selectedFace, "selectedFace");
        Objects.requireNonNull(status, "status");
        int[] pixels = new int[
                Math.multiplyExact(
                        value.width(),
                        value.height())];
        for (int y = 0; y < value.height(); y++) {
            for (int x = 0; x < value.width(); x++) {
                pixels[y * value.width() + x] =
                        value.colorAt(x, y);
            }
        }
        return paint(new PaintProjection(
                value.width(),
                value.height(),
                pixels,
                selectedFace,
                value.slotIndices(),
                value.selectedSlot(),
                value.selectedSynthetic(),
                value.tool(),
                value.color(),
                value.brushSize(),
                value.selectedEditable(),
                value.canUndo(),
                value.canRedo(),
                status));
    }

    public static PreviewViewModel withSurface(
            PreviewViewModel current,
            PreviewViewModel.RuntimeSurface surface) {
        Objects.requireNonNull(current, "current");
        return new PreviewViewModel(
                Optional.of(Objects.requireNonNull(surface, "surface")),
                Component.empty(),
                current.neighbors(),
                current.observedFace(),
                current.receiverVariant());
    }

    public static PreviewViewModel unavailable(
            Component reason,
            Direction observedFace) {
        return new PreviewViewModel(
                Optional.empty(),
                Objects.requireNonNull(reason, "reason"),
                Set.of(),
                Objects.requireNonNull(observedFace, "observedFace"),
                0);
    }

    public static <T extends WorkbenchDraftFields>
            WorkbenchViewModel<T> withPreview(
                    WorkbenchViewModel<T> source,
                    Set<PreviewViewModel.NeighborPosition> neighbors,
                    Direction observedFace,
                    int receiverVariant) {
        PreviewViewModel current = source.preview().orElseThrow(
                () -> new IllegalStateException("PREVIEW_UNAVAILABLE"));
        PreviewViewModel next = new PreviewViewModel(
                current.surface(),
                current.unavailableReason(),
                Objects.requireNonNull(neighbors, "neighbors"),
                Objects.requireNonNull(observedFace, "observedFace"),
                receiverVariant);
        return WorkbenchViewReducer.copy(
                source,
                source.document(),
                source.mode(),
                source.targets(),
                source.availableTargets(),
                source.selectedEntryKey(),
                Optional.of(next),
                source.paint(),
                source.properties(),
                source.operationStatus(),
                true,
                false);
    }

    public static NativePropertiesViewModel properties(
            PropertiesProjection value) {
        Objects.requireNonNull(value, "value");
        return new NativePropertiesViewModel(
                value.documentLabel(),
                "",
                value.fields(),
                value.preservedFieldsStatus(),
                value.entryId(),
                value.entryIdEditable(),
                value.matchingSelector(),
                value.connectionSelector(),
                value.faces(),
                value.facesEditable(),
                value.connectionBasis(),
                value.connectionBasisEditable(),
                value.renderLayer(),
                value.renderLayerEditable(),
                value.tintBlockId(),
                value.tintBlockEditable(),
                value.athenaConnection(),
                value.athenaConnectionEditable(),
                value.nativeDetails());
    }

    public record TargetRowProjection(
            String entryKey,
            String entryId,
            Optional<String> receiverBlockId,
            Component displayName,
            ItemStack icon,
            EngineFamily family,
            ConnectionMethod method,
            boolean compatibility,
            boolean managed,
            boolean configured,
            boolean previewEnabled,
            boolean paintEnabled,
            boolean propertiesEnabled,
            boolean editable) {
        public TargetRowProjection {
            Objects.requireNonNull(entryKey, "entryKey");
            Objects.requireNonNull(entryId, "entryId");
            receiverBlockId = Objects.requireNonNull(receiverBlockId, "receiverBlockId");
            displayName = Objects.requireNonNull(displayName, "displayName");
            icon = Objects.requireNonNull(icon, "icon").copy();
            Objects.requireNonNull(family, "family");
            Objects.requireNonNull(method, "method");
        }

        @Override
        public ItemStack icon() {
            return icon.copy();
        }
    }

    public record PaintProjection(
            int width,
            int height,
            int[] straightArgb,
            Direction selectedFace,
            List<Integer> slots,
            int selectedSlot,
            boolean selectedSynthetic,
            PaintTool tool,
            int color,
            int brushSize,
            boolean editable,
            boolean canUndo,
            boolean canRedo,
            Component status) {
        public PaintProjection {
            straightArgb = Objects.requireNonNull(straightArgb, "straightArgb").clone();
            selectedFace = Objects.requireNonNull(selectedFace, "selectedFace");
            slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
            Objects.requireNonNull(tool, "tool");
            Objects.requireNonNull(status, "status");
        }

        @Override
        public int[] straightArgb() {
            return straightArgb.clone();
        }
    }

    public record PropertiesProjection(
            Component documentLabel,
            List<NativePropertiesViewModel.Field> fields,
            Component preservedFieldsStatus,
            Optional<String> entryId,
            boolean entryIdEditable,
            NativePropertiesViewModel.Selector matchingSelector,
            NativePropertiesViewModel.Selector connectionSelector,
            Set<Direction> faces,
            boolean facesEditable,
            NativePropertiesViewModel.ConnectionBasis connectionBasis,
            boolean connectionBasisEditable,
            NativePropertiesViewModel.RenderLayer renderLayer,
            boolean renderLayerEditable,
            Optional<String> tintBlockId,
            boolean tintBlockEditable,
            NativePropertiesViewModel.AthenaConnection athenaConnection,
            boolean athenaConnectionEditable,
            Map<String, String> nativeDetails) {
        public PropertiesProjection {
            Objects.requireNonNull(documentLabel, "documentLabel");
            fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
            Objects.requireNonNull(preservedFieldsStatus, "preservedFieldsStatus");
            Objects.requireNonNull(entryId, "entryId");
            Objects.requireNonNull(matchingSelector, "matchingSelector");
            Objects.requireNonNull(connectionSelector, "connectionSelector");
            faces = Set.copyOf(Objects.requireNonNull(faces, "faces"));
            Objects.requireNonNull(connectionBasis, "connectionBasis");
            Objects.requireNonNull(renderLayer, "renderLayer");
            Objects.requireNonNull(tintBlockId, "tintBlockId");
            Objects.requireNonNull(athenaConnection, "athenaConnection");
            nativeDetails = Map.copyOf(Objects.requireNonNull(nativeDetails, "nativeDetails"));
        }
    }
}
