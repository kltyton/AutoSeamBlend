package com.kltyton.autoseamblend.frontend.controller;

import com.kltyton.autoseamblend.authoring.workbench.PaintTool;
import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDraftFields;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.frontend.model.NativePropertiesViewModel;
import com.kltyton.autoseamblend.frontend.model.PaintViewModel;
import com.kltyton.autoseamblend.frontend.model.PreviewViewModel;
import com.kltyton.autoseamblend.frontend.model.TargetRowView;
import com.kltyton.autoseamblend.frontend.model.WorkbenchViewModel;
import com.kltyton.autoseamblend.frontend.port.WorkbenchActionPort;
import java.util.ArrayList;
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
