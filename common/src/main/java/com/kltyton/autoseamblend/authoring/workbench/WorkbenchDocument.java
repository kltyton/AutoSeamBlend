package com.kltyton.autoseamblend.authoring.workbench;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：目标、属性和纹理模式共享的带修订号不可变会话文档；草稿载荷保持为 Loader 无关类型参数。
 *
 * English:
 * Revisioned immutable session document shared by target, property, and texture
 * modes. The draft payload is constrained only by loader-neutral authoring
 * fields.
 */
public record WorkbenchDocument<T extends WorkbenchDraftFields>(
        long revision,
        long persistedRevision,
        Map<String, Item<T>> items) {
    public WorkbenchDocument {
        if (revision < 0
                || persistedRevision < 0
                || persistedRevision > revision) {
            throw new IllegalArgumentException(
                    "invalid workbench revision");
        }
        items = Collections.unmodifiableMap(
                new LinkedHashMap<>(
                        Objects.requireNonNull(
                                items,
                                "items")));
    }

    public static <T extends WorkbenchDraftFields> WorkbenchDocument<T> open(
            List<Item<T>> initial) {
        LinkedHashMap<String, Item<T>> items =
                new LinkedHashMap<>();
        for (Item<T> item : initial) {
            if (items.putIfAbsent(
                            item.entryKey(),
                            item)
                    != null) {
                throw new IllegalArgumentException(
                        "duplicate workbench target");
            }
        }
        return new WorkbenchDocument<>(
                0,
                0,
                items);
    }

    public Optional<Item<T>> item(String entryKey) {
        return Optional.ofNullable(
                items.get(entryKey));
    }

    public WorkbenchDocument<T> add(Item<T> item) {
        Objects.requireNonNull(item, "item");
        String entryKey = item.entryKey();
        if (items.containsKey(entryKey)) {
            return this;
        }
        LinkedHashMap<String, Item<T>> next =
                new LinkedHashMap<>(items);
        next.put(entryKey, item);
        return changed(next);
    }

    public WorkbenchDocument<T> replace(Item<T> item) {
        Objects.requireNonNull(item, "item");
        String entryKey = item.entryKey();
        Item<T> previous = items.get(entryKey);
        if (previous == null) {
            throw new IllegalArgumentException(
                    "unknown workbench target");
        }
        if (previous.equals(item)) {
            return this;
        }
        LinkedHashMap<String, Item<T>> next =
                new LinkedHashMap<>(items);
        next.put(entryKey, item);
        return changed(next);
    }

    public WorkbenchDocument<T> markPersisted(
            long submittedRevision) {
        if (submittedRevision < persistedRevision
                || submittedRevision > revision) {
            throw new IllegalArgumentException(
                    "invalid submitted revision");
        }
        return new WorkbenchDocument<>(
                revision,
                submittedRevision,
                items);
    }

    public boolean dirty() {
        return revision != persistedRevision;
    }

    public WorkbenchDocument<T> touch() {
        return new WorkbenchDocument<>(
                Math.addExact(revision, 1),
                persistedRevision,
                items);
    }

    private WorkbenchDocument<T> changed(
            Map<String, Item<T>> next) {
        return new WorkbenchDocument<>(
                Math.addExact(revision, 1),
                persistedRevision,
                next);
    }

    public record Item<T extends WorkbenchDraftFields>(
            String entryKey,
            String entryId,
            String documentPath,
            EngineFamily family,
            Optional<T> draft,
            ConnectionMethod method,
            boolean compatibility,
            boolean managedAtOpen,
            boolean configuredAtOpen,
            boolean newlyAdded,
            boolean modified) {
        public Item {
            if (entryKey == null
                    || entryKey.isBlank()
                    || entryId == null
                    || entryId.isEmpty()) {
                throw new IllegalArgumentException(
                        "workbench entry key must be nonblank and display id nonempty");
            }
            documentPath = Objects.requireNonNull(
                    documentPath,
                    "documentPath");
            Objects.requireNonNull(family, "family");
            draft = Objects.requireNonNull(draft, "draft");
            Objects.requireNonNull(method, "method");
            draft.ifPresent(value -> {
                if (value.requestedMethod() != method
                        || value.compatibility()
                                != compatibility) {
                    throw new IllegalArgumentException(
                            "draft and authoring fields differ");
                }
            });
        }

        public Item<T> withDraft(T replacement) {
            return new Item<>(
                    entryKey,
                    entryId,
                    documentPath,
                    family,
                    Optional.of(replacement),
                    replacement.requestedMethod(),
                    replacement.compatibility(),
                    managedAtOpen,
                    configuredAtOpen,
                    newlyAdded,
                    true);
        }

        public Item<T> withTargetlessMethod(
                ConnectionMethod replacement) {
            if (draft.isPresent()) {
                throw new IllegalStateException(
                        "resolved items require a replacement draft");
            }
            return new Item<>(
                    entryKey,
                    entryId,
                    documentPath,
                    family,
                    draft,
                    Objects.requireNonNull(
                            replacement,
                            "replacement"),
                    compatibility,
                    managedAtOpen,
                    configuredAtOpen,
                    newlyAdded,
                    true);
        }

        public Item<T> withTargetlessCompatibility(
                boolean replacement) {
            if (draft.isPresent()) {
                throw new IllegalStateException(
                        "resolved items require a replacement draft");
            }
            return new Item<>(
                    entryKey,
                    entryId,
                    documentPath,
                    family,
                    draft,
                    method,
                    replacement,
                    managedAtOpen,
                    configuredAtOpen,
                    newlyAdded,
                    true);
        }

        public Item<T> withEntryId(
                String replacement) {
            if (replacement == null
                    || replacement.isEmpty()) {
                throw new IllegalArgumentException(
                        "entry id must not be empty");
            }
            return new Item<>(
                    entryKey,
                    replacement,
                    documentPath,
                    family,
                    draft,
                    method,
                    compatibility,
                    managedAtOpen,
                    configuredAtOpen,
                    newlyAdded,
                    true);
        }
    }
}
