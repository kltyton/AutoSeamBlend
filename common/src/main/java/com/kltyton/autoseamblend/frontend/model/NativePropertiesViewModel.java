package com.kltyton.autoseamblend.frontend.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 中文：由已验证原生 codec 提供的可视字段，不在控件中解释或改写原生格式。
 *
 * English:
 * Visual fields supplied by a verified native codec. Widgets never interpret
 * or rewrite a native format themselves.
 */
public record NativePropertiesViewModel(
        Component documentLabel,
        String sourceDocumentPath,
        List<Field> fields,
        Component preservedFieldsStatus,
        Optional<String> entryId,
        boolean entryIdEditable,
        Selector matchingSelector,
        Selector connectionSelector,
        Set<Direction> faces,
        boolean facesEditable,
        ConnectionBasis connectionBasis,
        boolean connectionBasisEditable,
        RenderLayer renderLayer,
        boolean renderLayerEditable,
        Optional<String> tintBlockId,
        boolean tintBlockEditable,
        AthenaConnection athenaConnection,
        boolean athenaConnectionEditable,
        Map<String, String> nativeDetails) {
    public NativePropertiesViewModel {
        documentLabel = Objects.requireNonNull(
                documentLabel,
                "documentLabel");
        sourceDocumentPath = Objects.requireNonNull(
                sourceDocumentPath,
                "sourceDocumentPath");
        fields = List.copyOf(
                Objects.requireNonNull(fields, "fields"));
        preservedFieldsStatus = Objects.requireNonNull(
                preservedFieldsStatus,
                "preservedFieldsStatus");
        entryId = Objects.requireNonNull(entryId, "entryId");
        matchingSelector = Objects.requireNonNull(matchingSelector, "matchingSelector");
        connectionSelector = Objects.requireNonNull(connectionSelector, "connectionSelector");
        faces = Set.copyOf(Objects.requireNonNull(faces, "faces"));
        connectionBasis = Objects.requireNonNull(connectionBasis, "connectionBasis");
        renderLayer = Objects.requireNonNull(renderLayer, "renderLayer");
        tintBlockId = Objects.requireNonNull(tintBlockId, "tintBlockId");
        athenaConnection = Objects.requireNonNull(athenaConnection, "athenaConnection");
        LinkedHashMap<String, String> details = new LinkedHashMap<>();
        Objects.requireNonNull(nativeDetails, "nativeDetails").forEach((key, value) ->
                details.put(requireText(key, "native detail key"),
                        Objects.requireNonNull(value, "native detail value")));
        nativeDetails = Collections.unmodifiableMap(details);
    }

    /** 中文：保持原生条目顺序的选择器投影。 / English: Selector projection preserving native entry order. */
    public record Selector(List<Entry> entries, boolean editable) {
        public Selector {
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        }
    }

    /** 中文：一个原生选择器条目及其按需解析的方块状态信息。 / English: One native selector entry and its lazily resolved block-state information. */
    public record Entry(
            String serialized,
            Optional<String> blockId,
            boolean opaque,
            boolean editable,
            List<Constraint> constraints,
            List<PropertyValues> availableProperties) {
        public Entry {
            serialized = requireText(serialized, "serialized selector entry");
            blockId = Objects.requireNonNull(blockId, "blockId");
            constraints = List.copyOf(Objects.requireNonNull(constraints, "constraints"));
            availableProperties = List.copyOf(
                    Objects.requireNonNull(availableProperties, "availableProperties"));
            if (opaque == editable) {
                throw new IllegalArgumentException(
                        "selector entry must be exactly one of opaque or editable");
            }
        }
    }

    /** 中文：保持原生约束顺序，并附带该属性全部合法值。 / English: Native-ordered constraint with every legal value for that property. */
    public record Constraint(
            String propertyName,
            List<String> availableValues,
            List<String> selectedValues) {
        public Constraint {
            propertyName = requireText(propertyName, "constraint property name");
            availableValues = List.copyOf(
                    Objects.requireNonNull(availableValues, "availableValues"));
            selectedValues = List.copyOf(
                    Objects.requireNonNull(selectedValues, "selectedValues"));
        }
    }

    /** 中文：注册表属性顺序下的全部合法值与当前选择值。 / English: All legal and currently selected values in registry property order. */
    public record PropertyValues(
            String propertyName,
            List<String> availableValues,
            List<String> selectedValues) {
        public PropertyValues {
            propertyName = requireText(propertyName, "property name");
            availableValues = List.copyOf(
                    Objects.requireNonNull(availableValues, "availableValues"));
            selectedValues = List.copyOf(
                    Objects.requireNonNull(selectedValues, "selectedValues"));
        }
    }

    public enum ConnectionBasis { BLOCK, TILE, STATE }
    public enum RenderLayer { CUTOUT, TRANSLUCENT }
    public enum AthenaConnection { SAME_BLOCK, SAME_STATE, STATE, CUSTOM }

    /** 中文：一个已验证字段及其原生值令牌。 / English: One verified field and its native value tokens. */
    public record Field(
            String id,
            Component label,
            Component valueLabel,
            String valueToken,
            List<Option> options,
            boolean editable) {
        public Field {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException(
                        "native property id must be nonblank");
            }
            label = Objects.requireNonNull(label, "label");
            valueLabel = Objects.requireNonNull(
                    valueLabel,
                    "valueLabel");
            valueToken = Objects.requireNonNull(
                    valueToken,
                    "valueToken");
            options = List.copyOf(
                    Objects.requireNonNull(options, "options"));
        }
    }

    /** 中文：原生 codec 明确允许写回的一个候选值。 / English: One value the native codec explicitly permits writing back. */
    public record Option(String token, Component label) {
        public Option {
            token = Objects.requireNonNull(token, "token");
            label = Objects.requireNonNull(label, "label");
        }
    }

    /** 中文：属性编辑器独享的完整注册表候选，不与已发现目标列表混用。 / English: Full registry candidate used only by native-property editing. */
    public record SelectorCandidate(
            String blockId,
            Component displayName,
            ItemStack icon) {
        public SelectorCandidate {
            if (blockId == null || blockId.isBlank()) {
                throw new IllegalArgumentException("selector block id must be nonblank");
            }
            displayName = Objects.requireNonNull(displayName, "displayName");
            icon = Objects.requireNonNull(icon, "icon").copy();
        }

        @Override
        public ItemStack icon() {
            return icon.copy();
        }
    }

    private static String requireText(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " must not be blank");
        return normalized;
    }
}
