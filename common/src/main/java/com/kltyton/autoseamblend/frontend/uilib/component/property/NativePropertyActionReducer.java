package com.kltyton.autoseamblend.frontend.uilib.component.property;

import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.AddNativeSelectorBlock;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.CycleAthenaConnection;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.CycleNativeConnectionBasis;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.CycleNativeRenderLayer;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.MoveNativeSelectorEntry;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.RemoveNativeSelectorEntry;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.SetNativeEntryId;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.SetNativeProperty;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.SetNativeTintBlock;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.ToggleNativeFace;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.ToggleNativeSelectorProperty;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Direction;

/**
 * 中文：把 UILib 原生属性动作路由到 Loader 原生操作，不在控件中解释文档格式。
 *
 * English: Routes UILib native-property actions to Loader native operations;
 * controls never interpret a document format.
 */
public final class NativePropertyActionReducer {
    private NativePropertyActionReducer() {}

    public static <D> Optional<Reduction<D>> reduce(
            D current,
            WorkbenchAction action,
            Operations<D> operations) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(operations, "operations");
        D next;
        Optional<ConnectionMethod> method = Optional.empty();
        Optional<Boolean> compatibility = Optional.empty();
        if (action instanceof SetNativeProperty set) {
            if (set.fieldId().equals("method")) {
                ConnectionMethod selected = ConnectionMethod.parse(set.valueToken())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "unknown native method token: " + set.valueToken()));
                next = operations.setMethod(current, selected);
                method = Optional.of(selected);
            } else if (set.fieldId().equals("compatibility")) {
                boolean selected = Boolean.parseBoolean(set.valueToken());
                next = operations.setCompatibility(current, selected);
                compatibility = Optional.of(selected);
            } else {
                return Optional.empty();
            }
        } else if (action instanceof SetNativeEntryId set) {
            next = operations.setEntryId(current, set.value());
        } else if (action instanceof ToggleNativeFace toggle) {
            next = operations.toggleFace(current, toggle.face());
        } else if (action instanceof CycleNativeConnectionBasis) {
            next = operations.cycleConnectionBasis(current);
        } else if (action instanceof CycleNativeRenderLayer) {
            next = operations.cycleRenderLayer(current);
        } else if (action instanceof SetNativeTintBlock set) {
            next = operations.setTintBlock(current, set.blockId());
        } else if (action instanceof AddNativeSelectorBlock add) {
            next = operations.addSelectorBlock(current, add.kind(), add.blockId());
        } else if (action instanceof RemoveNativeSelectorEntry remove) {
            next = operations.removeSelectorEntry(current, remove.kind(), remove.index());
        } else if (action instanceof MoveNativeSelectorEntry move) {
            next = operations.moveSelectorEntry(
                    current,
                    move.kind(),
                    move.index(),
                    move.delta());
        } else if (action instanceof ToggleNativeSelectorProperty toggle) {
            next = operations.toggleSelectorProperty(
                    current,
                    toggle.kind(),
                    toggle.index(),
                    toggle.propertyName(),
                    toggle.value());
        } else if (action instanceof CycleAthenaConnection) {
            next = operations.cycleAthenaConnection(current);
        } else {
            return Optional.empty();
        }
        return Optional.of(new Reduction<>(
                Objects.requireNonNull(next, "next"),
                method,
                compatibility));
    }

    public record Reduction<D>(
            D document,
            Optional<ConnectionMethod> method,
            Optional<Boolean> compatibility) {
        public Reduction {
            document = Objects.requireNonNull(document, "document");
            method = Objects.requireNonNull(method, "method");
            compatibility = Objects.requireNonNull(compatibility, "compatibility");
        }
    }

    public interface Operations<D> {
        D setMethod(D current, ConnectionMethod method);

        D setCompatibility(D current, boolean compatibility);

        D setEntryId(D current, String value);

        D toggleFace(D current, Direction face);

        D cycleConnectionBasis(D current);

        D cycleRenderLayer(D current);

        D setTintBlock(D current, String blockId);

        D addSelectorBlock(
                D current,
                WorkbenchAction.NativeSelectorKind kind,
                String blockId);

        D removeSelectorEntry(
                D current,
                WorkbenchAction.NativeSelectorKind kind,
                int index);

        D moveSelectorEntry(
                D current,
                WorkbenchAction.NativeSelectorKind kind,
                int index,
                int delta);

        D toggleSelectorProperty(
                D current,
                WorkbenchAction.NativeSelectorKind kind,
                int index,
                String propertyName,
                String value);

        D cycleAthenaConnection(D current);
    }
}
