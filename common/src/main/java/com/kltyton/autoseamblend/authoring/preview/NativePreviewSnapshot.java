package com.kltyton.autoseamblend.authoring.preview;

import com.kltyton.autoseamblend.engine.routing.query.EngineRouteSource;
import com.kltyton.autoseamblend.runtime.render.ProceduralConnectionPlan;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** 中文：由所选引擎真实原生状态路径产生的不可变工作台视图模型。 / English: Immutable workbench view model produced from the selected engine's real native state path. */
public record NativePreviewSnapshot(
        long ruleGeneration,
        long surfaceGeneration,
        String engineId,
        EngineRouteSource source,
        String receiverBlockId,
        Optional<String> donorBlockId,
        BlockPos receiverPosition,
        Optional<BlockPos> donorPosition,
        boolean connectionPlaceholder,
        String sourceTextureId,
        TextureAtlasSprite sourceSprite,
        Direction face,
        ConnectionMethod requestedMethod,
        ConnectionMethod resolvedMethod,
        NeighborConnections connections,
        ProceduralConnectionPlan plan,
        PreviewFaceResult faceResult) {
    public NativePreviewSnapshot {
        if (ruleGeneration < 0 || surfaceGeneration < 0) {
            throw new IllegalArgumentException(
                    "preview generations must be non-negative");
        }
        requireText(engineId, "engineId");
        Objects.requireNonNull(source, "source");
        requireText(receiverBlockId, "receiverBlockId");
        donorBlockId = Objects.requireNonNull(
                donorBlockId,
                "donorBlockId");
        donorBlockId.ifPresent(value ->
                requireText(value, "donorBlockId"));
        receiverPosition = Objects.requireNonNull(
                        receiverPosition,
                        "receiverPosition")
                .immutable();
        donorPosition = Objects.requireNonNull(
                donorPosition,
                "donorPosition")
                .map(BlockPos::immutable);
        requireText(sourceTextureId, "sourceTextureId");
        Objects.requireNonNull(
                sourceSprite,
                "sourceSprite");
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(requestedMethod, "requestedMethod");
        Objects.requireNonNull(resolvedMethod, "resolvedMethod");
        Objects.requireNonNull(connections, "connections");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(
                faceResult,
                "faceResult");
    }

    private static void requireText(
            String value,
            String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    name + " must not be blank");
        }
    }
}
