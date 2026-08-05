package com.kltyton.autoseamblend.selection.minecraft;

import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** 中文：解析方块 ID 和当前游戏注册表标签，且不改变选择器顺序。 / English: Resolves block ids and current play-registry tags without changing selector order. */
public final class MinecraftSelectorResolver implements ConnectionRuleSet.Resolver<Block> {
    private final RegistryAccess registryAccess;
    private int resolvedTagSelectorCount;
    private int resolvedTagMemberCount;

    public MinecraftSelectorResolver(RegistryAccess registryAccess) {
        this.registryAccess = registryAccess;
    }

    @Override
    public boolean isValidId(String id) {
        return id.indexOf(':') > 0 && Identifier.tryParse(id) != null;
    }

    @Override
    public Optional<Block> block(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null || !BuiltInRegistries.BLOCK.containsKey(identifier)) {
            return Optional.empty();
        }
        Block block = BuiltInRegistries.BLOCK.getValue(identifier);
        return block == null || block == Blocks.AIR
                ? Optional.empty()
                : Optional.of(block);
    }

    @Override
    public Set<Block> tag(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null || registryAccess == null) {
            return Set.of();
        }
        TagKey<Block> tag = TagKey.create(Registries.BLOCK, identifier);
        Registry<Block> blockRegistry = registryAccess.lookupOrThrow(Registries.BLOCK);
        HashSet<Block> blocks = new HashSet<>();
        for (Holder<Block> holder : blockRegistry.getTagOrEmpty(tag)) {
            Block block = holder.value();
            if (block != Blocks.AIR) {
                blocks.add(block);
            }
        }
        Set<Block> resolved = Set.copyOf(blocks);
        resolvedTagSelectorCount++;
        resolvedTagMemberCount += resolved.size();
        return resolved;
    }

    @Override
    public boolean tagsReady() {
        return registryAccess != null;
    }

    @Override
    public String id(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    public int resolvedTagSelectorCount() {
        return resolvedTagSelectorCount;
    }

    public int resolvedTagMemberCount() {
        return resolvedTagMemberCount;
    }
}
