package com.kltyton.autoseamblend.authoring.selector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：无副作用、无损的原生方块选择器条目；平台负责把注册表事实提供为 DTO。
 *
 * English: Side-effect-free lossless native selector entry. The platform
 * supplies registry facts as project DTOs.
 */
public final class NativeBlockSelectorEntry {
    private final String original;
    private final String blockId;
    private final List<StateConstraint> constraints;
    private final boolean editable;
    private final boolean modified;

    private NativeBlockSelectorEntry(String original, String blockId, List<StateConstraint> constraints, boolean editable, boolean modified) {
        this.original = nonBlank(original, "original");
        this.blockId = blockId;
        this.constraints = List.copyOf(Objects.requireNonNull(constraints, "constraints"));
        this.editable = editable;
        this.modified = modified;
    }

    public static NativeBlockSelectorEntry parse(String raw, NativeBlockSelectorResolver resolver) {
        String normalized = nonBlank(raw, "raw");
        String[] parts = normalized.split(":", -1);
        if (parts.length == 0 || parts[0].isBlank()) return opaque(normalized);
        boolean explicitNamespace = parts.length >= 2 && !parts[1].contains("=");
        String encodedId = explicitNamespace ? parts[0] + ':' + parts[1] : "minecraft:" + parts[0];
        int propertyStart = explicitNamespace ? 2 : 1;
        Optional<NativeBlockSelectorFacts> resolved = Objects.requireNonNull(resolver, "resolver").resolve(encodedId);
        if (resolved.isEmpty()) return opaque(normalized);
        NativeBlockSelectorFacts facts = resolved.orElseThrow();
        ArrayList<StateConstraint> parsed = new ArrayList<>();
        LinkedHashSet<String> propertyNames = new LinkedHashSet<>();
        for (int index = propertyStart; index < parts.length; index++) {
            String segment = parts[index];
            int separator = segment.indexOf('=');
            if (separator <= 0 || separator == segment.length() - 1 || separator != segment.lastIndexOf('=')) return opaque(normalized);
            String propertyName = segment.substring(0, separator);
            List<String> allowed = facts.availableProperties().get(propertyName);
            if (allowed == null || !propertyNames.add(propertyName)) return opaque(normalized);
            ArrayList<String> values = new ArrayList<>();
            for (String value : segment.substring(separator + 1).split(",", -1)) {
                if (value.isBlank() || !allowed.contains(value) || values.contains(value)) return opaque(normalized);
                values.add(value);
            }
            parsed.add(new StateConstraint(propertyName, values));
        }
        return new NativeBlockSelectorEntry(normalized, facts.blockId(), parsed, true, false);
    }

    public static NativeBlockSelectorEntry forBlock(String blockId, NativeBlockSelectorResolver resolver) {
        NativeBlockSelectorEntry parsed = parse(blockId, resolver);
        if (!parsed.editable()) throw new IllegalArgumentException("blockId must resolve to a selectable block");
        return new NativeBlockSelectorEntry(parsed.original, parsed.blockId, parsed.constraints, true, true);
    }

    private static NativeBlockSelectorEntry opaque(String raw) { return new NativeBlockSelectorEntry(raw, null, List.of(), false, false); }
    public String serialized() {
        if (!modified || !editable) return original;
        StringBuilder output = new StringBuilder(Objects.requireNonNull(blockId, "blockId"));
        for (StateConstraint constraint : constraints) output.append(':').append(constraint.propertyName()).append('=').append(String.join(",", constraint.values()));
        return output.toString();
    }
    public boolean editable() { return editable; }
    public Optional<String> blockId() { return Optional.ofNullable(blockId); }
    public List<StateConstraint> constraints() { return constraints; }
    public Map<String, List<String>> availableProperties(NativeBlockSelectorFacts facts) { return requireFacts(facts).availableProperties(); }
    public boolean selects(String propertyName, String value) { return constraints.stream().filter(constraint -> constraint.propertyName().equals(propertyName)).findFirst().map(constraint -> constraint.values().contains(value)).orElse(false); }
    public NativeBlockSelectorEntry toggle(NativeBlockSelectorFacts facts, String propertyName, String value) {
        List<String> available = requireFacts(facts).availableProperties().get(propertyName);
        if (available == null || !available.contains(value)) throw new IllegalArgumentException("unknown block-state property value");
        ArrayList<StateConstraint> next = new ArrayList<>(constraints);
        int constraintIndex = -1;
        for (int index = 0; index < next.size(); index++) if (next.get(index).propertyName().equals(propertyName)) { constraintIndex = index; break; }
        if (constraintIndex < 0) next.add(new StateConstraint(propertyName, List.of(value)));
        else {
            ArrayList<String> values = new ArrayList<>(next.get(constraintIndex).values());
            if (!values.remove(value)) { values.add(value); values.sort(java.util.Comparator.comparingInt(available::indexOf)); }
            if (values.isEmpty()) next.remove(constraintIndex); else next.set(constraintIndex, new StateConstraint(propertyName, values));
        }
        return new NativeBlockSelectorEntry(original, blockId, next, true, true);
    }
    public boolean matches(NativeBlockSelectorState state) {
        Objects.requireNonNull(state, "state");
        if (!editable || blockId == null || !blockId.equals(state.blockId())) return false;
        return constraints.stream().allMatch(constraint -> constraint.values().contains(state.values().get(constraint.propertyName())));
    }
    public Optional<NativeBlockSelectorState> representativeState(NativeBlockSelectorFacts facts) {
        NativeBlockSelectorFacts validated = requireFacts(facts);
        if (matches(validated.defaultState())) return Optional.of(validated.defaultState());
        return validated.possibleStates().stream().filter(this::matches).findFirst();
    }
    private NativeBlockSelectorFacts requireFacts(NativeBlockSelectorFacts facts) {
        if (!editable || blockId == null || !blockId.equals(Objects.requireNonNull(facts, "facts").blockId())) throw new UnsupportedOperationException("opaque selector entries are read-only");
        return facts;
    }
    private static String nonBlank(String value, String label) { String normalized = Objects.requireNonNull(value, label).trim(); if (normalized.isEmpty()) throw new IllegalArgumentException(label + " must not be blank"); return normalized; }

    /** 中文：一个属性上的有序允许值集合。 English: Ordered allowed values for one state property. */
    public record StateConstraint(String propertyName, List<String> values) {
        public StateConstraint { propertyName = nonBlank(propertyName, "propertyName"); values = List.copyOf(Objects.requireNonNull(values, "values")); if (values.isEmpty() || values.stream().anyMatch(String::isBlank) || values.stream().distinct().count() != values.size()) throw new IllegalArgumentException("constraint values must be nonblank and unique"); }
    }
}
