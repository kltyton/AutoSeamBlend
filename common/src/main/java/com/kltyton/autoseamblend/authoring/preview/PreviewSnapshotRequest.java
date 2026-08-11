package com.kltyton.autoseamblend.authoring.preview;

import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** 中文：Loader 查询完成后交给公共预览解析器的不可变输入。 / English: Immutable input handed to the common preview resolver after Loader routing. */
public record PreviewSnapshotRequest(
        BlockAndTintGetter level,
        BlockPos pos,
        BlockState state,
        Direction face,
        FaceSurface surface,
        RuleRuntime.Snapshot rules,
        MinecraftSurfaceCatalog.Snapshot surfaces,
        EngineQuerySelection selection,
        Optional<ConnectionMethod> requestedOverride,
        Set<Block> connectionBlocks,
        Optional<BlockState> donorState,
        Optional<BlockPos> donorPosition,
        boolean connectionPlaceholder) {
    public PreviewSnapshotRequest {
        Objects.requireNonNull(level, "level");
        pos = Objects.requireNonNull(pos, "pos").immutable();
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(surfaces, "surfaces");
        Objects.requireNonNull(selection, "selection");
        requestedOverride = Objects.requireNonNull(
                requestedOverride,
                "requestedOverride");
        connectionBlocks = Set.copyOf(
                Objects.requireNonNull(connectionBlocks, "connectionBlocks"));
        donorState = Objects.requireNonNull(donorState, "donorState");
        donorPosition = Objects.requireNonNull(donorPosition, "donorPosition")
                .map(BlockPos::immutable);
    }
}
