package com.kltyton.autoseamblend.selection.compiled;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 中文：所有 Loader 边界共享的唯一有序选择器编译器。 / English: Sole ordered selector compiler shared by every loader boundary. */
final class ConnectionRuleCompiler {
    private ConnectionRuleCompiler() {}

    static <T> ConnectionRuleSet.Compilation<T> compile(
            Map<String, Map<String, List<String>>> targets,
            Map<String, Map<String, List<String>>> excludedTargets,
            ConnectionRuleSet.Resolver<T> resolver) {
        CompilationState<T> state = new CompilationState<>(resolver);
        compileBuckets("targets", targets, false, state);
        compileBuckets("excludedTargets", excludedTargets, true, state);
        return state.finish();
    }

    private static <T> void compileBuckets(
            String root,
            Map<String, Map<String, List<String>>> methods,
            boolean exclusion,
            CompilationState<T> state) {
        for (Map.Entry<String, Map<String, List<String>>> methodEntry : methods.entrySet()) {
            if (ConnectionMethod.parse(methodEntry.getKey()).isEmpty()) {
                state.reject(root + " has unknown method: " + methodEntry.getKey());
            }
            for (String modeKey : methodEntry.getValue().keySet()) {
                if (ConnectionRuleSet.ResourcePackMode.parse(modeKey).isEmpty()) {
                    state.reject(root + '.' + methodEntry.getKey() + " has unknown mode: " + modeKey);
                }
            }
        }
        for (ConnectionRuleSet.ResourcePackMode priorityMode : List.of(
                ConnectionRuleSet.ResourcePackMode.COMPATIBILITY,
                ConnectionRuleSet.ResourcePackMode.NON_COMPATIBILITY)) {
            compileMode(root, methods, priorityMode, exclusion, state);
        }
    }

    private static <T> void compileMode(
            String root,
            Map<String, Map<String, List<String>>> methods,
            ConnectionRuleSet.ResourcePackMode priorityMode,
            boolean exclusion,
            CompilationState<T> state) {
        for (Map.Entry<String, Map<String, List<String>>> methodEntry : methods.entrySet()) {
            Optional<ConnectionMethod> parsedMethod = ConnectionMethod.parse(methodEntry.getKey());
            if (parsedMethod.isEmpty()) continue;
            ConnectionMethod method = parsedMethod.orElseThrow();
            for (Map.Entry<String, List<String>> modeEntry : methodEntry.getValue().entrySet()) {
                Optional<ConnectionRuleSet.ResourcePackMode> parsedMode =
                        ConnectionRuleSet.ResourcePackMode.parse(modeEntry.getKey());
                if (parsedMode.isEmpty() || parsedMode.orElseThrow() != priorityMode) continue;
                ConnectionRuleSet.ResourcePackMode mode = parsedMode.orElseThrow();
                List<String> selectors = modeEntry.getValue();
                for (int listIndex = 0; listIndex < selectors.size(); listIndex++) {
                    String path = root + '.' + methodEntry.getKey() + '.' + modeEntry.getKey()
                            + '[' + listIndex + ']';
                    state.add(
                            path,
                            selectors.get(listIndex),
                            methodEntry.getKey(),
                            method,
                            modeEntry.getKey(),
                            mode,
                            listIndex,
                            exclusion);
                }
            }
        }
    }

    private static final class CompilationState<T> {
        private final ConnectionRuleSet.Resolver<T> resolver;
        private final ArrayList<String> diagnostics = new ArrayList<>();
        private final ArrayList<String> deferred = new ArrayList<>();
        private final HashMap<String, String> targetSpellings = new HashMap<>();
        private final HashMap<T, Set<String>> memberships = new HashMap<>();
        private final LinkedHashMap<T, ConnectionRuleSet.CompiledSelector<T>> selected = new LinkedHashMap<>();
        private final ArrayList<ConnectionRuleSet.CompiledSelector<T>> selectors = new ArrayList<>();
        private final HashMap<T, List<ConnectionRuleSet.CompiledSelector<T>>> exclusions = new HashMap<>();
        private final ArrayList<ConnectionRuleSet.CompiledSelector<T>> exclusionSelectors = new ArrayList<>();
        private int validSelectorCount;
        private int order;
        private boolean valid = true;

        private CompilationState(ConnectionRuleSet.Resolver<T> resolver) {
            this.resolver = resolver;
        }

        private void add(
                String path,
                String raw,
                String methodBucket,
                ConnectionMethod method,
                String modeBucket,
                ConnectionRuleSet.ResourcePackMode mode,
                int listIndex,
                boolean exclusion) {
            ResolvedSelector<T> resolved = resolve(path, raw);
            int selectorOrder = order++;
            if (resolved == null) return;
            if (!exclusion) {
                String previous = targetSpellings.putIfAbsent(resolved.spelling(), path);
                if (previous != null) {
                    diagnostics.add(path + " conflicts with earlier selector " + previous
                            + "; five-tier/bucket order keeps the earlier match: " + resolved.spelling());
                }
            }
            ConnectionRuleSet.CompiledSelector<T> compiled = new ConnectionRuleSet.CompiledSelector<>(
                    path,
                    resolved.spelling(),
                    resolved.groupId(),
                    methodBucket,
                    method,
                    modeBucket,
                    mode,
                    listIndex,
                    selectorOrder,
                    resolved.specificity(),
                    resolved.targets());
            validSelectorCount++;
            if (exclusion) {
                exclusionSelectors.add(compiled);
                resolved.targets().forEach(target -> exclusions
                        .computeIfAbsent(target, ignored -> new ArrayList<>())
                        .add(compiled));
                return;
            }
            selectors.add(compiled);
            for (T target : resolved.targets()) {
                ConnectionRuleSet.CompiledSelector<T> previous = selected.putIfAbsent(target, compiled);
                if (previous == null) {
                    memberships.put(target, Set.of(resolved.groupId()));
                } else if (previous.method() != compiled.method() || previous.mode() != compiled.mode()) {
                    diagnostics.add("Block " + resolver.id(target) + " keeps first selector "
                            + previous.identity() + " before " + compiled.identity());
                }
            }
        }

        private ResolvedSelector<T> resolve(String path, String raw) {
            String spelling = raw == null ? "" : raw.trim();
            if (spelling.isEmpty()) {
                skipSelector(path + " is empty");
                return null;
            }
            boolean tag = spelling.charAt(0) == '#';
            String id = tag ? spelling.substring(1) : spelling;
            if (!resolver.isValidId(id)) {
                skipSelector(path + " has invalid id: " + spelling);
                return null;
            }
            if (tag && !resolver.tagsReady()) {
                deferred.add(path + " deferred until block tags are available: " + spelling);
                return null;
            }
            Set<T> values = tag
                    ? resolver.tag(id)
                    : resolver.block(id).map(Set::of).orElse(Set.of());
            if (values.isEmpty()) {
                skipSelector(path
                        + (tag ? " tag is missing or empty: " : " block does not exist: ")
                        + spelling);
                return null;
            }
            return new ResolvedSelector<>(
                    spelling,
                    (tag ? "tag:" : "block:") + id,
                    tag ? 1 : 2,
                    values);
        }

        private void skipSelector(String diagnostic) {
            diagnostics.add(diagnostic);
        }

        private void reject(String diagnostic) {
            diagnostics.add(diagnostic);
            valid = false;
        }

        private ConnectionRuleSet.Compilation<T> finish() {
            return new ConnectionRuleSet.Compilation<>(
                    new ConnectionRuleSet<>(
                            memberships,
                            selected,
                            selectors,
                            exclusions,
                            exclusionSelectors),
                    validSelectorCount,
                    diagnostics,
                    deferred,
                    valid);
        }
    }

    private record ResolvedSelector<T>(
            String spelling,
            String groupId,
            int specificity,
            Set<T> targets) {}
}
