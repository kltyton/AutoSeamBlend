package com.kltyton.autoseamblend.authoring.storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：Loader 中立的 Managed 资源包选择、位置校验和可回滚结果算法。
 *
 * English: Loader-neutral algorithm for selecting Managed, validating its position, and
 * returning a rollback-capable result.
 *
 * <p>Only the small {@link Repository} and {@link Options} ports cross the Loader boundary;
 * Fabric/NeoForge adapt their native Minecraft objects at the edge.
 */
public final class ManagedPackRepositoryOrder {
    private ManagedPackRepositoryOrder() {}

    /**
     * 中文：确保 Managed 位于所有非必需外部资源包之下。
     *
     * English: Ensures Managed remains below every non-required external resource pack.
     */
    public static Result ensureSelected(
            Context context,
            String managedId) throws IOException {
        Context current = Objects.requireNonNull(context, "context");
        String managed = requireId(managedId);
        Repository repository = current.repository();
        Options options = current.options();
        repository.reload();
        if (repository.find(managed).isEmpty()) {
            throw new IOException("Managed pack is not visible as " + managed);
        }

        List<String> before = repository.selected().stream()
                .map(PackEntry::id)
                .toList();
        List<String> previousOptions = copyIds(
                options.resourcePackIds(),
                "resource pack options");
        List<String> previousIncompatible = copyIds(
                options.incompatiblePackIds(),
                "incompatible resource pack options");
        try {
            ArrayList<String> requested = new ArrayList<>(before.size() + 1);
            requested.add(managed);
            for (String id : before) {
                if (!managed.equals(id)) {
                    requested.add(id);
                }
            }
            repository.setSelected(requested);

            List<PackEntry> selected = List.copyOf(repository.selected());
            int managedIndex = indexOf(selected, managed);
            if (managedIndex < 0) {
                throw new IOException("PackRepository did not select Managed");
            }
            for (int index = 0; index < managedIndex; index++) {
                PackEntry pack = selected.get(index);
                if (!pack.required() && !managed.equals(pack.id())) {
                    throw new IOException(
                            "Managed cannot be placed below external pack " + pack.id());
                }
            }

            List<String> optionIds = selected.stream()
                    .filter(pack -> !pack.fixedPosition())
                    .map(PackEntry::id)
                    .toList();
            boolean changed = !previousOptions.equals(optionIds);
            if (changed) {
                options.setResourcePackIds(optionIds);
                ArrayList<String> incompatible = new ArrayList<>(previousIncompatible);
                incompatible.removeIf(managed::equals);
                options.setIncompatiblePackIds(incompatible);
                options.save();
            }
            return new Result(
                    current,
                    changed,
                    optionIds,
                    before,
                    previousOptions,
                    previousIncompatible);
        } catch (IOException | RuntimeException failure) {
            try {
                restore(
                        current,
                        before,
                        previousOptions,
                        previousIncompatible);
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    private static void restore(
            Context context,
            List<String> repositoryIds,
            List<String> optionIds,
            List<String> incompatibleIds) {
        context.repository().setSelected(repositoryIds);
        context.options().setResourcePackIds(optionIds);
        context.options().setIncompatiblePackIds(incompatibleIds);
        context.options().save();
    }

    private static int indexOf(List<PackEntry> packs, String id) {
        for (int index = 0; index < packs.size(); index++) {
            if (packs.get(index).id().equals(id)) {
                return index;
            }
        }
        return -1;
    }

    private static List<String> copyIds(List<String> ids, String label) {
        Objects.requireNonNull(ids, label);
        return List.copyOf(ids);
    }

    private static String requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("pack id must not be blank");
        }
        return id;
    }

    /** 中文：Loader 原生资源包仓库的最小端口。 / English: Minimal port for a Loader-native pack repository. */
    public interface Repository {
        void reload() throws IOException;

        Optional<PackEntry> find(String id);

        List<PackEntry> selected();

        void setSelected(List<String> ids);
    }

    /** 中文：Minecraft 客户端资源包选项的最小端口。 / English: Minimal port for Minecraft client pack options. */
    public interface Options {
        List<String> resourcePackIds();

        List<String> incompatiblePackIds();

        void setResourcePackIds(List<String> ids);

        void setIncompatiblePackIds(List<String> ids);

        void save();
    }

    /** 中文：一次算法调用的 Loader 边界组合。 / English: Loader-bound pair used by one algorithm call. */
    public record Context(Repository repository, Options options) {
        public Context {
            Objects.requireNonNull(repository, "repository");
            Objects.requireNonNull(options, "options");
        }
    }

    /** 中文：仓库中的不可变资源包事实。 / English: Immutable pack facts exposed by a repository adapter. */
    public record PackEntry(String id, boolean required, boolean fixedPosition) {
        public PackEntry {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("pack id must not be blank");
            }
        }
    }

    /**
     * 中文：保存成功前的仓库和客户端选项快照，并提供原子回滚。
     *
     * English: Captures repository and client-option state before saving and exposes rollback.
     */
    public static final class Result {
        private final Context context;
        private final boolean selectionChanged;
        private final List<String> selectedPackIds;
        private final List<String> previousRepositoryIds;
        private final List<String> previousOptionIds;
        private final List<String> previousIncompatibleIds;

        private Result(
                Context context,
                boolean selectionChanged,
                List<String> selectedPackIds,
                List<String> previousRepositoryIds,
                List<String> previousOptionIds,
                List<String> previousIncompatibleIds) {
            this.context = Objects.requireNonNull(context, "context");
            this.selectionChanged = selectionChanged;
            this.selectedPackIds = List.copyOf(selectedPackIds);
            this.previousRepositoryIds = List.copyOf(previousRepositoryIds);
            this.previousOptionIds = List.copyOf(previousOptionIds);
            this.previousIncompatibleIds = List.copyOf(previousIncompatibleIds);
        }

        public boolean selectionChanged() {
            return selectionChanged;
        }

        public List<String> selectedPackIds() {
            return selectedPackIds;
        }

        public List<String> previousRepositoryIds() {
            return previousRepositoryIds;
        }

        public List<String> previousOptionIds() {
            return previousOptionIds;
        }

        public List<String> previousIncompatibleIds() {
            return previousIncompatibleIds;
        }

        /** 中文：恢复调用前的全部原生状态。 / English: Restores every native state captured before the call. */
        public void rollback() {
            restore(
                    context,
                    previousRepositoryIds,
                    previousOptionIds,
                    previousIncompatibleIds);
        }
    }
}
