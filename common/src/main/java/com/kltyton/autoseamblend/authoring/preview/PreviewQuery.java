package com.kltyton.autoseamblend.authoring.preview;

import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** 中文：运行时派生预览提供器共享的精确世界与表面查询。 / English: Exact world/surface query shared by runtime-derived preview providers. */
public record PreviewQuery(
        BlockAndTintGetter level,
        BlockPos pos,
        BlockState state,
        Direction face,
        FaceSurface surface,
        RuleRuntime.Snapshot rules,
        MinecraftSurfaceCatalog.Snapshot surfaces,
        ConnectionMethod requestedMethod,
        ConnectionMethod resolvedMethod,
        Set<Block> authoringConnectionBlocks,
        boolean connectionPlaceholder) {
    public PreviewQuery {
        Objects.requireNonNull(level, "level");
        pos = Objects.requireNonNull(pos, "pos").immutable();
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(surfaces, "surfaces");
        Objects.requireNonNull(requestedMethod, "requestedMethod");
        Objects.requireNonNull(resolvedMethod, "resolvedMethod");
        authoringConnectionBlocks = Set.copyOf(
                Objects.requireNonNull(
                        authoringConnectionBlocks,
                        "authoringConnectionBlocks"));
        if (resolvedMethod == ConnectionMethod.AUTO) {
            throw new IllegalArgumentException(
                    "preview query must contain one resolved method");
        }
    }

    /**
     * 中文：工作台优先使用当前原生文档的 connectBlocks；非创作查询再退回配置连接规则。
     *
     * English:
     * The workbench prefers connectBlocks from the current native document.
     * Non-authoring queries fall back to configured connection rules.
     */
    public boolean connects(
            BlockState origin,
            BlockState other) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(other, "other");
        if (connectionPlaceholder) {
            /*
             * 中文：白色羊毛只是在三维场景中表达“这里有一个同目标连接邻居”；
             * 精确查询必须把它当作目标方块，而不能让占位外观清空连接掩码。
             *
             * English: White wool only visualizes an abstract same-target
             * neighbor in the 3D scene. Exact queries must treat it as the
             * target block instead of letting its appearance clear the mask.
             */
            return other.getBlock() == Blocks.WHITE_WOOL
                    || other.getBlock() == origin.getBlock();
        }
        if (!authoringConnectionBlocks.isEmpty()) {
            return authoringConnectionBlocks.contains(
                    other.getBlock());
        }
        return rules.rules()
                        .isTarget(
                                origin.getBlock())
                ? rules.rules()
                        .connects(
                                origin.getBlock(),
                                other.getBlock())
                : origin.getBlock()
                        == other.getBlock();
    }

    public boolean usesDocumentConnectionBlocks() {
        return !authoringConnectionBlocks.isEmpty();
    }
}
