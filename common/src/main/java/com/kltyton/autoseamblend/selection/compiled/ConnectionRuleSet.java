package com.kltyton.autoseamblend.selection.compiled;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 中文：不可变的有序选择器结果；配置文本只在重载期间解析。 / English: Immutable ordered selector result. Configuration text is parsed only during reload. */
public final class ConnectionRuleSet<T> {
    private final Map<T, Set<String>> memberships;
    private final Map<T, CompiledSelector<T>> selected;
    private final List<CompiledSelector<T>> selectors;
    private final Map<T, List<CompiledSelector<T>>> exclusions;
    private final List<CompiledSelector<T>> exclusionSelectors;

    ConnectionRuleSet(
            Map<T, Set<String>> memberships,
            Map<T, CompiledSelector<T>> selected,
            List<CompiledSelector<T>> selectors,
            Map<T, List<CompiledSelector<T>>> exclusions,
            List<CompiledSelector<T>> exclusionSelectors) {
        HashMap<T, Set<String>> stableMemberships = new HashMap<>();
        memberships.forEach((target, values) -> stableMemberships.put(target, Set.copyOf(values)));
        this.memberships = Map.copyOf(stableMemberships);
        this.selected = Map.copyOf(selected);
        this.selectors = List.copyOf(selectors);
        HashMap<T, List<CompiledSelector<T>>> stableExclusions = new HashMap<>();
        exclusions.forEach((target, values) -> stableExclusions.put(target, List.copyOf(values)));
        this.exclusions = Map.copyOf(stableExclusions);
        this.exclusionSelectors = List.copyOf(exclusionSelectors);
    }

    public boolean isTarget(T value) {
        CompiledSelector<T> selector = selected.get(value);
        return selector != null && !isExcluded(value, selector.method(), selector.mode());
    }

    public boolean connects(T current, T neighbor) {
        CompiledSelector<T> currentSelector = selected.get(current);
        CompiledSelector<T> neighborSelector = selected.get(neighbor);
        if (currentSelector == null || neighborSelector == null
                || isExcluded(current, currentSelector.method(), currentSelector.mode())
                || isExcluded(neighbor, neighborSelector.method(), neighborSelector.mode())) {
            return false;
        }
        Set<String> left = memberships.get(current);
        Set<String> right = memberships.get(neighbor);
        return left != null && right != null && left.stream().anyMatch(right::contains);
    }

    public Optional<CompiledSelector<T>> selector(T value) {
        CompiledSelector<T> selector = selected.get(value);
        return selector == null || isExcluded(value, selector.method(), selector.mode())
                ? Optional.empty()
                : Optional.of(selector);
    }

    /** 中文：应用查询局部排除前的精确配置胜出项。 / English: Exact configured winner before query-local exclusions are applied. */
    public Optional<CompiledSelector<T>> configuredSelector(T value) {
        return Optional.ofNullable(selected.get(value));
    }

    public List<CompiledSelector<T>> selectors() {
        return selectors;
    }

    public List<CompiledSelector<T>> exclusionSelectors() {
        return exclusionSelectors;
    }

    public ConnectionMethod method(T value) {
        CompiledSelector<T> selector = selected.get(value);
        return selector == null ? ConnectionMethod.AUTO : selector.method();
    }

    public ResourcePackMode resourcePackMode(T value) {
        CompiledSelector<T> selector = selected.get(value);
        return selector == null ? ResourcePackMode.NON_COMPATIBILITY : selector.mode();
    }

    public int priority(T value) {
        CompiledSelector<T> selector = selected.get(value);
        return selector == null ? Integer.MAX_VALUE : selector.order();
    }

    public List<Target<T>> targets() {
        return selected.entrySet().stream()
                .filter(entry -> !isExcluded(
                        entry.getKey(), entry.getValue().method(), entry.getValue().mode()))
                .map(entry -> new Target<>(
                        entry.getKey(),
                        entry.getValue().method(),
                        entry.getValue().mode(),
                        entry.getValue().order()))
                .sorted((left, right) -> Integer.compare(left.priority(), right.priority()))
                .toList();
    }

    /** 中文：供尚未提供查询边界的调用方使用的兼容重载。 / English: Compatibility overload for callers that have not yet supplied a query boundary. */
    public boolean isExcluded(T value) {
        return isExcluded(value, method(value), resourcePackMode(value));
    }

    public boolean isExcluded(T value, ConnectionMethod method, ResourcePackMode mode) {
        return exclusions.getOrDefault(value, List.of()).stream()
                .anyMatch(selector -> selector.method() == method && selector.mode() == mode);
    }

    public Set<ResourcePackMode> excludedModes(T value, ConnectionMethod method) {
        EnumSet<ResourcePackMode> modes = EnumSet.noneOf(ResourcePackMode.class);
        exclusions.getOrDefault(value, List.of()).stream()
                .filter(selector -> selector.method() == method)
                .forEach(selector -> modes.add(selector.mode()));
        return modes.isEmpty() ? Set.of() : Set.copyOf(modes);
    }

    public int targetCount() {
        return (int) selected.entrySet().stream()
                .filter(entry -> !isExcluded(
                        entry.getKey(), entry.getValue().method(), entry.getValue().mode()))
                .count();
    }

    public static <T> Compilation<T> compile(
            Map<String, Map<String, List<String>>> targets,
            Resolver<T> resolver) {
        return compile(targets, Map.of(), resolver);
    }

    public static <T> Compilation<T> compile(
            Map<String, Map<String, List<String>>> targets,
            Map<String, Map<String, List<String>>> excludedTargets,
            Resolver<T> resolver) {
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(excludedTargets, "excludedTargets");
        Objects.requireNonNull(resolver, "resolver");
        return ConnectionRuleCompiler.compile(targets, excludedTargets, resolver);
    }

    public enum ResourcePackMode {
        NON_COMPATIBILITY("non-compatibility"),
        COMPATIBILITY("compatibility");

        private final String serializedName;

        ResourcePackMode(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }

        static Optional<ResourcePackMode> parse(String raw) {
            if (raw == null) return Optional.empty();
            for (ResourcePackMode value : values()) {
                if (value.serializedName.equals(raw.trim())) return Optional.of(value);
            }
            return Optional.empty();
        }
    }

    public interface Resolver<T> {
        boolean isValidId(String id);
        Optional<T> block(String id);
        Set<T> tag(String id);
        String id(T value);

        default boolean tagsReady() {
            return true;
        }
    }

    public record Target<T>(
            T value,
            ConnectionMethod method,
            ResourcePackMode resourcePackMode,
            int priority) {}

    public record CompiledSelector<T>(
            String identity,
            String spelling,
            String groupId,
            String methodBucket,
            ConnectionMethod method,
            String modeBucket,
            ResourcePackMode mode,
            int listIndex,
            int order,
            int specificity,
            Set<T> targets) {
        public CompiledSelector {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(spelling, "spelling");
            Objects.requireNonNull(groupId, "groupId");
            Objects.requireNonNull(methodBucket, "methodBucket");
            Objects.requireNonNull(method, "method");
            Objects.requireNonNull(modeBucket, "modeBucket");
            Objects.requireNonNull(mode, "mode");
            if (listIndex < 0 || order < 0 || specificity < 0) {
                throw new IllegalArgumentException("selector indices must be non-negative");
            }
            targets = Set.copyOf(Objects.requireNonNull(targets, "targets"));
        }
    }

    public record Compilation<T>(
            ConnectionRuleSet<T> rules,
            int validSelectorCount,
            List<String> diagnostics,
            List<String> deferredSelectors,
            boolean valid) {
        public Compilation {
            Objects.requireNonNull(rules, "rules");
            diagnostics = List.copyOf(diagnostics);
            deferredSelectors = List.copyOf(deferredSelectors);
        }
    }
}
