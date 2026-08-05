package com.kltyton.autoseamblend.authoring.storage;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * 中文：Minecraft 客户端资源包仓库到 Managed 选择算法的共享适配器。
 *
 * English: Shared adapter from Minecraft's client pack repository to the Managed selection
 * algorithm.
 */
public final class MinecraftManagedPackRepositoryOrder {
    private MinecraftManagedPackRepositoryOrder() {}

    public static ManagedPackRepositoryOrder.Result ensureSelected(Minecraft minecraft)
            throws IOException {
        Minecraft client = Objects.requireNonNull(minecraft, "minecraft");
        return ManagedPackRepositoryOrder.ensureSelected(
                new ManagedPackRepositoryOrder.Context(
                        new RepositoryPort(client),
                        new OptionsPort(client)),
                ManagedPackIdentity.REPOSITORY_ID);
    }

    private record RepositoryPort(Minecraft minecraft)
            implements ManagedPackRepositoryOrder.Repository {
        private RepositoryPort {
            Objects.requireNonNull(minecraft, "minecraft");
        }

        @Override
        public void reload() throws IOException {
            repository().reload();
        }

        @Override
        public Optional<ManagedPackRepositoryOrder.PackEntry> find(String id) {
            return Optional.ofNullable(repository().getPack(id))
                    .map(MinecraftManagedPackRepositoryOrder::entry);
        }

        @Override
        public List<ManagedPackRepositoryOrder.PackEntry> selected() {
            return repository().getSelectedPacks().stream()
                    .map(MinecraftManagedPackRepositoryOrder::entry)
                    .toList();
        }

        @Override
        public void setSelected(List<String> ids) {
            repository().setSelected(ids);
        }

        private PackRepository repository() {
            return minecraft.getResourcePackRepository();
        }
    }

    private record OptionsPort(Minecraft minecraft)
            implements ManagedPackRepositoryOrder.Options {
        private OptionsPort {
            Objects.requireNonNull(minecraft, "minecraft");
        }

        @Override
        public List<String> resourcePackIds() {
            return List.copyOf(minecraft.options.resourcePacks);
        }

        @Override
        public List<String> incompatiblePackIds() {
            return List.copyOf(minecraft.options.incompatibleResourcePacks);
        }

        @Override
        public void setResourcePackIds(List<String> ids) {
            minecraft.options.resourcePacks.clear();
            minecraft.options.resourcePacks.addAll(ids);
        }

        @Override
        public void setIncompatiblePackIds(List<String> ids) {
            minecraft.options.incompatibleResourcePacks.clear();
            minecraft.options.incompatibleResourcePacks.addAll(ids);
        }

        @Override
        public void save() {
            minecraft.options.save();
        }
    }

    private static ManagedPackRepositoryOrder.PackEntry entry(Pack pack) {
        return new ManagedPackRepositoryOrder.PackEntry(
                pack.getId(),
                pack.isRequired(),
                pack.isFixedPosition());
    }
}
