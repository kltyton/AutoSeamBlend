package com.kltyton.autoseamblend.frontend.uilib.component.property;

import com.kltyton.autoseamblend.authoring.property.NativePropertyDocument;
import com.kltyton.autoseamblend.authoring.selector.MinecraftNativeBlockSelectorResolver;
import com.kltyton.autoseamblend.authoring.selector.NativeBlockSelectorEntry;
import com.kltyton.autoseamblend.authoring.selector.NativeBlockSelectorField;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.frontend.model.NativePropertiesViewModel;
import com.kltyton.autoseamblend.frontend.model.NativePropertiesViewModel.Selector;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * 中文：把 Common 原生文档完整投影为属性视图模型，集中选择器与 Minecraft 方向转换。
 *
 * English: Projects a Common native document into the complete property view
 * model while centralizing selector and Minecraft-direction conversion.
 */
public final class NativePropertyDocumentViewProjection {
    private NativePropertyDocumentViewProjection() {}

    public static NativePropertiesViewModel project(NativePropertyDocument document) {
        NativePropertyDocument validated = Objects.requireNonNull(document, "document");
        NativePropertiesViewModel projected = NativePropertyViewProjection.project(
                source(validated));
        return new NativePropertiesViewModel(
                projected.documentLabel(),
                validated.documentPath(),
                projected.fields(),
                Component.translatable("gui.autoseamblend.property.preserved_fields"),
                projected.entryId(),
                projected.entryIdEditable(),
                projected.matchingSelector(),
                projected.connectionSelector(),
                projected.faces(),
                projected.facesEditable(),
                projected.connectionBasis(),
                projected.connectionBasisEditable(),
                projected.renderLayer(),
                projected.renderLayerEditable(),
                projected.tintBlockId(),
                projected.tintBlockEditable(),
                projected.athenaConnection(),
                projected.athenaConnectionEditable(),
                projected.nativeDetails());
    }

    private static NativePropertyViewProjection.Source source(
            NativePropertyDocument document) {
        return new NativePropertyViewProjection.Source() {
            @Override
            public EngineFamily family() {
                return document.family();
            }

            @Override
            public ConnectionMethod authoringMethod() {
                return document.authoringMethod();
            }

            @Override
            public boolean authoringCompatibility() {
                return document.authoringCompatibility();
            }

            @Override
            public Optional<String> entryId() {
                return document.explicitEntryId();
            }

            @Override
            public boolean entryIdEditable() {
                return true;
            }

            @Override
            public Selector matchingSelector() {
                return selector(
                        document.matchingSelector(),
                        document.matchingEditingSupported());
            }

            @Override
            public Selector connectionSelector() {
                return selector(
                        document.connectionSelector(),
                        document.connectionEditingSupported());
            }

            @Override
            public Set<Direction> faces() {
                EnumSet<Direction> faces = EnumSet.noneOf(Direction.class);
                document.faces().stream()
                        .map(face -> Direction.valueOf(face.name()))
                        .forEach(faces::add);
                return Collections.unmodifiableSet(faces);
            }

            @Override
            public boolean facesEditable() {
                return document.connectionEditingSupported();
            }

            @Override
            public NativePropertiesViewModel.ConnectionBasis connectionBasis() {
                return NativePropertiesViewModel.ConnectionBasis.valueOf(
                        document.connectionBasis().name());
            }

            @Override
            public boolean connectionBasisEditable() {
                return document.connectionEditingSupported();
            }

            @Override
            public NativePropertiesViewModel.RenderLayer renderLayer() {
                return NativePropertiesViewModel.RenderLayer.valueOf(
                        document.renderLayer().name());
            }

            @Override
            public boolean renderLayerEditable() {
                return document.connectionEditingSupported();
            }

            @Override
            public Optional<String> tintBlockId() {
                return Optional.of(document.tintBlockId())
                        .filter(value -> !value.isBlank());
            }

            @Override
            public boolean tintBlockEditable() {
                return document.connectionEditingSupported();
            }

            @Override
            public NativePropertiesViewModel.AthenaConnection athenaConnection() {
                return NativePropertiesViewModel.AthenaConnection.valueOf(
                        document.athenaConnection().name());
            }

            @Override
            public boolean athenaConnectionEditable() {
                return document.athenaConnectionEditingSupported();
            }

            @Override
            public Map<String, String> nativeDetails() {
                return document.nativeDetails();
            }
        };
    }

    private static Selector selector(
            NativeBlockSelectorField field,
            boolean editable) {
        return NativePropertySelectorProjection.project(
                field.entries(),
                editable,
                NativeBlockSelectorEntry::serialized,
                NativeBlockSelectorEntry::blockId,
                NativeBlockSelectorEntry::editable,
                MinecraftNativeBlockSelectorResolver::availableProperties,
                entry -> entry.constraints().stream()
                        .map(constraint ->
                                new NativePropertySelectorProjection.ConstraintValues(
                                        constraint.propertyName(),
                                        constraint.values()))
                        .toList(),
                NativeBlockSelectorEntry::selects);
    }
}
