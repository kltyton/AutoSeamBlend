package com.kltyton.autoseamblend.frontend.uilib.component.property;

import com.kltyton.autoseamblend.frontend.model.NativePropertiesViewModel.Constraint;
import com.kltyton.autoseamblend.frontend.model.NativePropertiesViewModel.Entry;
import com.kltyton.autoseamblend.frontend.model.NativePropertiesViewModel.PropertyValues;
import com.kltyton.autoseamblend.frontend.model.NativePropertiesViewModel.Selector;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * 中文：在不依赖 Loader 选择器类型的前提下保持原生条目、约束和注册表属性顺序。
 *
 * English: Preserves native entry, constraint, and registry-property order
 * without depending on a Loader selector type.
 */
public final class NativePropertySelectorProjection {
    private NativePropertySelectorProjection() {}

    public static <E> Selector project(
            List<E> entries,
            boolean selectorEditable,
            Function<E, String> serialized,
            Function<E, Optional<String>> blockId,
            Function<E, Boolean> entryEditable,
            Function<E, Map<String, List<String>>> availableProperties,
            Function<E, List<ConstraintValues>> constraints,
            TriPredicate<E, String, String> selects) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(serialized, "serialized");
        Objects.requireNonNull(blockId, "blockId");
        Objects.requireNonNull(entryEditable, "entryEditable");
        Objects.requireNonNull(availableProperties, "availableProperties");
        Objects.requireNonNull(constraints, "constraints");
        Objects.requireNonNull(selects, "selects");
        return new Selector(
                entries.stream()
                        .map(entry -> projectEntry(
                                entry,
                                selectorEditable,
                                serialized,
                                blockId,
                                entryEditable,
                                availableProperties,
                                constraints,
                                selects))
                        .toList(),
                selectorEditable);
    }

    private static <E> Entry projectEntry(
            E entry,
            boolean selectorEditable,
            Function<E, String> serialized,
            Function<E, Optional<String>> blockId,
            Function<E, Boolean> entryEditable,
            Function<E, Map<String, List<String>>> availableProperties,
            Function<E, List<ConstraintValues>> constraints,
            TriPredicate<E, String, String> selects) {
        List<PropertyValues> properties = availableProperties.apply(entry)
                .entrySet()
                .stream()
                .map(property -> new PropertyValues(
                        property.getKey(),
                        property.getValue(),
                        property.getValue().stream()
                                .filter(value -> selects.test(
                                        entry,
                                        property.getKey(),
                                        value))
                                .toList()))
                .toList();
        List<Constraint> projectedConstraints = constraints.apply(entry)
                .stream()
                .map(constraint -> new Constraint(
                        constraint.propertyName(),
                        properties.stream()
                                .filter(property -> property.propertyName()
                                        .equals(constraint.propertyName()))
                                .map(PropertyValues::availableValues)
                                .findFirst()
                                .orElse(constraint.values()),
                        constraint.values()))
                .toList();
        boolean editable = selectorEditable && Boolean.TRUE.equals(entryEditable.apply(entry));
        return new Entry(
                serialized.apply(entry),
                blockId.apply(entry),
                !editable,
                editable,
                projectedConstraints,
                properties);
    }

    /** 中文：Loader 选择器约束的无损中间值。 / English: Lossless intermediate for a Loader selector constraint. */
    public record ConstraintValues(String propertyName, List<String> values) {
        public ConstraintValues {
            propertyName = Objects.requireNonNull(propertyName, "propertyName");
            values = List.copyOf(Objects.requireNonNull(values, "values"));
        }
    }

    @FunctionalInterface
    public interface TriPredicate<A, B, C> {
        boolean test(A first, B second, C third);
    }
}
