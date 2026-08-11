package com.kltyton.autoseamblend.compat.continuity.authoring.export;

import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringRule;
import com.kltyton.autoseamblend.compat.continuity.authoring.materialize.ContinuitySlotRecipeDomain;
import com.kltyton.autoseamblend.export.managed.ManagedExportIr;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.generation.GeneratedTileRecipe;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 中文：跨 Loader 的 Continuity 导出方法、槽位、配方和资源命名计划。
 * English: Loader-neutral Continuity export plan for methods, slots, recipes, and resource names.
 *
 * <p>Only the locked native slot map is consulted by {@link ContinuitySlotRecipeDomain}; pixel
 * reads, PNG encoding, and native document I/O remain outside this plan.</p>
 */
public final class ContinuityManagedExportPlan {
    private ContinuityManagedExportPlan() {}

    /**
     * 中文：验证源精灵仍对应创作规则，防止导出时把另一张材质写入当前规则。
     * English: Verifies that the frozen source sprite still belongs to the authoring rule before
     * any generated artifact is assembled.
     */
    public static void requireSource(String expected, String actual) {
        if (!Objects.requireNonNull(expected, "expectedSource").equals(
                Objects.requireNonNull(actual, "actualSource"))) {
            throw new IllegalArgumentException("EXPORT_SOURCE_TEXTURE_CHANGED");
        }
    }

    /**
     * 中文：按 resolved method 选择源；TOP 必须显式提供另一张冻结源，其余方法使用当前源。
     * English: Selects the source for a resolved method; TOP requires an explicit frozen top
     * source, while all other methods use the current source.
     */
    public static <T> T sourceFor(
            ConnectionMethod method,
            T source,
            Optional<T> topSource) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(topSource, "topSource");
        return method == ConnectionMethod.TOP
                ? topSource.orElseThrow(() -> new IllegalStateException(
                        "TOP_SOURCE_SURFACE_UNRESOLVED"))
                : source;
    }

    /**
     * 中文：判断单图方法是否隐含 slot 0 的已有源精灵，而不是生成连接槽 PNG。
     * English: Tests whether a single-image method implicitly uses the existing source sprite in
     * slot 0 instead of generating a connected-texture PNG.
     */
    public static boolean isSingleSourceMethod(ConnectionMethod method) {
        return Objects.requireNonNull(method, "method") == ConnectionMethod.TOP
                || method == ConnectionMethod.FIXED;
    }

    /**
     * 中文：合并原生受保护槽、实体化槽和单图 slot 0 的 properties 表达式；后写 generated
     * 槽保持 Fabric 既有覆盖顺序。
     * English: Merges protected-native, materialized, and single-image slot-0 properties
     * expressions; later generated entries preserve Fabric's existing overwrite order.
     */
    public static Map<Integer, String> tileExpressions(
            ConnectionMethod method,
            Map<Integer, String> protectedExpressions,
            Map<Integer, String> generatedExpressions,
            String sourceSpriteId) {
        Objects.requireNonNull(method, "method");
        LinkedHashMap<Integer, String> expressions = new LinkedHashMap<>();
        putExpressions(expressions, protectedExpressions, "protected");
        putExpressions(expressions, generatedExpressions, "generated");
        if (isSingleSourceMethod(method)) {
            String source = Objects.requireNonNull(sourceSpriteId, "sourceSpriteId");
            if (source.isBlank()) {
                throw new IllegalArgumentException("sourceSpriteId must not be blank");
            }
            expressions.putIfAbsent(0, source);
        }
        return Collections.unmodifiableMap(expressions);
    }

    private static void putExpressions(
            Map<Integer, String> target,
            Map<Integer, String> values,
            String label) {
        Objects.requireNonNull(values, label + "Expressions").forEach((slot, expression) -> {
            if (slot == null || slot < 0
                    || expression == null || expression.isBlank()) {
                throw new IllegalArgumentException(
                        label + " Continuity tile expression is invalid");
            }
            target.put(slot, expression);
        });
    }

    /**
     * 中文：依据同一 Continuity 原生槽位表构建生成槽计划；槽顺序就是方法域的稳定顺序。
     * English: Builds generated slots from the same Continuity native slot map; slot order is the
     * stable order of the method domain.
     */
    public static SlotPlan forRule(ManagedAuthoringRule rule) {
        Objects.requireNonNull(rule, "rule");
        ConnectionMethod method = rule.resolvedMethod();
        List<Slot> slots = ContinuitySlotRecipeDomain.slots(method).stream()
                .map(index -> new Slot(
                        index,
                        ContinuitySlotRecipeDomain.recipe(method, index),
                        texturePath(rule, index),
                        resourceId(rule, index)))
                .toList();
        return new SlotPlan(method, slots);
    }

    /**
     * 中文：由 Managed source ID 派生生成 PNG 的资源包路径。
     * English: Derives the resource-pack path for a generated PNG from the Managed source ID.
     */
    public static String texturePath(
            ManagedAuthoringRule rule,
            int slot) {
        Objects.requireNonNull(rule, "rule");
        requireSlot(rule.resolvedMethod(), slot);
        return "assets/autoseamblend/textures/generated/continuity/"
                + rule.resolvedMethod().serializedName()
                + '/'
                + rule.textureNamespace()
                + '/'
                + rule.texturePath()
                + '/'
                + slot
                + ".png";
    }

    /**
     * 中文：由同一规则派生 MCPatcher tiles 属性中的 generated texture ID。
     * English: Derives the generated texture ID used by the MCPatcher tiles property for the
     * same rule.
     */
    public static String resourceId(
            ManagedAuthoringRule rule,
            int slot) {
        Objects.requireNonNull(rule, "rule");
        requireSlot(rule.resolvedMethod(), slot);
        return "autoseamblend:generated/continuity/"
                + rule.resolvedMethod().serializedName()
                + '/'
                + rule.textureNamespace()
                + '/'
                + rule.texturePath()
                + '/'
                + slot;
    }

    /**
     * 中文：按 authoring/baked 两个文档图谱去重；完全相同的重复节点合并，冲突节点拒绝。
     * English: Deduplicates the authoring and baked document graphs independently; identical
     * duplicate nodes merge while conflicting nodes are rejected.
     */
    public static List<ManagedExportIr.Document> deduplicateDocuments(
            List<ManagedExportIr.Document> documents) {
        Objects.requireNonNull(documents, "documents");
        LinkedHashMap<String, ManagedExportIr.Document> authoring = new LinkedHashMap<>();
        LinkedHashMap<String, ManagedExportIr.Document> baked = new LinkedHashMap<>();
        ArrayList<ManagedExportIr.Document> unique = new ArrayList<>();
        for (ManagedExportIr.Document candidate : documents) {
            Objects.requireNonNull(candidate, "document");
            ManagedExportIr.Document authoringConflict = candidate.authoring()
                    .map(value -> authoring.putIfAbsent(value.path(), candidate))
                    .orElse(null);
            ManagedExportIr.Document bakedConflict = candidate.baked()
                    .map(value -> baked.putIfAbsent(value.path(), candidate))
                    .orElse(null);
            ManagedExportIr.Document conflict = authoringConflict != null
                    ? authoringConflict
                    : bakedConflict;
            if (conflict != null) {
                if (!sameDocument(conflict, candidate)) {
                    throw new IllegalArgumentException(
                            "CONTINUITY_DOCUMENT_PATH_CONFLICT");
                }
                continue;
            }
            unique.add(candidate);
        }
        return List.copyOf(unique);
    }

    private static boolean sameDocument(
            ManagedExportIr.Document left,
            ManagedExportIr.Document right) {
        return sameArtifact(left.authoring(), right.authoring())
                && sameArtifact(left.baked(), right.baked());
    }

    private static boolean sameArtifact(
            Optional<ManagedExportIr.Artifact> left,
            Optional<ManagedExportIr.Artifact> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return left.isEmpty() && right.isEmpty();
        }
        return left.orElseThrow().path().equals(right.orElseThrow().path())
                && Arrays.equals(
                        left.orElseThrow().bytes(),
                        right.orElseThrow().bytes());
    }

    private static void requireSlot(ConnectionMethod method, int slot) {
        if (slot < 0 || !ContinuitySlotRecipeDomain.slots(method).contains(slot)) {
            throw new IllegalArgumentException(
                    "slot " + slot + " is outside Continuity method " + method);
        }
    }

    /**
     * 中文：一个方法的不可变生成槽计划；构造时验证索引、路径、表达式均无重复。
     * English: Immutable generated-slot plan for one method; construction validates unique
     * indexes, paths, and expressions.
     */
    public record SlotPlan(ConnectionMethod method, List<Slot> slots) {
        public SlotPlan {
            method = Objects.requireNonNull(method, "method");
            slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
            LinkedHashSet<Integer> indexes = new LinkedHashSet<>();
            LinkedHashSet<String> paths = new LinkedHashSet<>();
            LinkedHashSet<String> resources = new LinkedHashSet<>();
            for (Slot slot : slots) {
                if (!indexes.add(slot.index())
                        || !paths.add(slot.texturePath())
                        || !resources.add(slot.resourceId())) {
                    throw new IllegalArgumentException(
                            "Continuity export slot plan contains duplicate identity");
                }
            }
            if (!indexes.equals(new LinkedHashSet<>(ContinuitySlotRecipeDomain.slots(method)))) {
                throw new IllegalArgumentException(
                        "Continuity export slot plan does not cover the method domain");
            }
        }

        public List<Integer> indexes() {
            return slots.stream().map(Slot::index).toList();
        }

        /** 中文：按稳定槽顺序生成 properties tiles 值。 / English: Builds the properties tiles values in stable slot order. */
        public Map<Integer, String> tileExpressions() {
            LinkedHashMap<Integer, String> values = new LinkedHashMap<>();
            slots.forEach(slot -> values.put(slot.index(), slot.resourceId()));
            return Collections.unmodifiableMap(values);
        }

        /** 中文：当前批次会生成的 PNG 与 metadata 路径。 / English: Paths generated by this batch, including metadata companions. */
        public Set<String> generatedDocumentPaths() {
            LinkedHashSet<String> paths = new LinkedHashSet<>();
            slots.forEach(slot -> {
                paths.add(slot.texturePath());
                paths.add(slot.texturePath() + ".mcmeta");
            });
            return Collections.unmodifiableSet(paths);
        }
    }

    /** 中文：一个连续纹理生成槽的纯数据描述。 / English: Pure-data description of one generated connected-texture slot. */
    public record Slot(
            int index,
            GeneratedTileRecipe recipe,
            String texturePath,
            String resourceId) {
        public Slot {
            if (index < 0) {
                throw new IllegalArgumentException("Continuity slot index must be non-negative");
            }
            recipe = Objects.requireNonNull(recipe, "recipe");
            if (texturePath == null || texturePath.isBlank()
                    || resourceId == null || resourceId.isBlank()) {
                throw new IllegalArgumentException("Continuity slot paths must not be blank");
            }
        }
    }
}
