package com.kltyton.autoseamblend.frontend.uilib.component.property;

import com.kltyton.autoseamblend.authoring.property.NativePropertyDocument;
import com.kltyton.autoseamblend.authoring.selector.MinecraftNativeBlockSelectorResolver;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.geometry.WorldDirection;
import java.util.Objects;
import net.minecraft.core.Direction;

/**
 * 中文：把工作台属性动作归约为不可变原生文档，不依赖任何 Loader 或引擎类型。
 *
 * English: Reduces workbench property actions into immutable native documents
 * without depending on a Loader or engine type.
 */
public final class NativePropertyDocumentActions {
    private static final NativePropertyActionReducer.Operations<NativePropertyDocument>
            OPERATIONS = new NativePropertyActionReducer.Operations<>() {
                @Override
                public NativePropertyDocument setMethod(
                        NativePropertyDocument current,
                        ConnectionMethod method) {
                    return current.withAuthoringMethod(method);
                }

                @Override
                public NativePropertyDocument setCompatibility(
                        NativePropertyDocument current,
                        boolean compatibility) {
                    return current.withAuthoringCompatibility(compatibility);
                }

                @Override
                public NativePropertyDocument setEntryId(
                        NativePropertyDocument current,
                        String value) {
                    return current.withEntryId(value);
                }

                @Override
                public NativePropertyDocument toggleFace(
                        NativePropertyDocument current,
                        Direction face) {
                    return current.toggleFace(WorldDirection.valueOf(face.name()));
                }

                @Override
                public NativePropertyDocument cycleConnectionBasis(
                        NativePropertyDocument current) {
                    return current.cycleConnectionBasis();
                }

                @Override
                public NativePropertyDocument cycleRenderLayer(
                        NativePropertyDocument current) {
                    return current.cycleRenderLayer();
                }

                @Override
                public NativePropertyDocument setTintBlock(
                        NativePropertyDocument current,
                        String blockId) {
                    return current.withTintBlock(
                            blockId,
                            MinecraftNativeBlockSelectorResolver.ALL_STATES);
                }

                @Override
                public NativePropertyDocument addSelectorBlock(
                        NativePropertyDocument current,
                        WorkbenchAction.NativeSelectorKind kind,
                        String blockId) {
                    return kind == WorkbenchAction.NativeSelectorKind.MATCHING
                            ? current.addMatchingBlock(
                                    blockId,
                                    MinecraftNativeBlockSelectorResolver.ALL_STATES)
                            : current.addConnectionBlock(
                                    blockId,
                                    MinecraftNativeBlockSelectorResolver.ALL_STATES);
                }

                @Override
                public NativePropertyDocument removeSelectorEntry(
                        NativePropertyDocument current,
                        WorkbenchAction.NativeSelectorKind kind,
                        int index) {
                    return kind == WorkbenchAction.NativeSelectorKind.MATCHING
                            ? current.removeMatchingEntry(index)
                            : current.removeConnectionEntry(index);
                }

                @Override
                public NativePropertyDocument moveSelectorEntry(
                        NativePropertyDocument current,
                        WorkbenchAction.NativeSelectorKind kind,
                        int index,
                        int delta) {
                    return kind == WorkbenchAction.NativeSelectorKind.MATCHING
                            ? current.moveMatchingEntry(index, delta)
                            : current.moveConnectionEntry(index, delta);
                }

                @Override
                public NativePropertyDocument toggleSelectorProperty(
                        NativePropertyDocument current,
                        WorkbenchAction.NativeSelectorKind kind,
                        int index,
                        String propertyName,
                        String value) {
                    return kind == WorkbenchAction.NativeSelectorKind.MATCHING
                            ? current.toggleMatchingProperty(
                                    index,
                                    propertyName,
                                    value,
                                    MinecraftNativeBlockSelectorResolver.ALL_STATES)
                            : current.toggleConnectionProperty(
                                    index,
                                    propertyName,
                                    value,
                                    MinecraftNativeBlockSelectorResolver.ALL_STATES);
                }

                @Override
                public NativePropertyDocument cycleAthenaConnection(
                        NativePropertyDocument current) {
                    return current.cycleAthenaConnection(
                            MinecraftNativeBlockSelectorResolver.ALL_STATES);
                }
            };

    private NativePropertyDocumentActions() {}

    public static NativePropertyDocument reduce(
            NativePropertyDocument current,
            WorkbenchAction action) {
        NativePropertyDocument validated = Objects.requireNonNull(current, "current");
        return NativePropertyActionReducer.reduce(
                        validated,
                        Objects.requireNonNull(action, "action"),
                        OPERATIONS)
                .map(NativePropertyActionReducer.Reduction::document)
                .orElse(validated);
    }
}
