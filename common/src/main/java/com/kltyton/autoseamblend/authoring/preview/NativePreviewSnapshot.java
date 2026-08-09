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

/**
 * 中文：由所选引擎真实原生状态路径产生的不可变工作台视图模型。
 *
 * English: Immutable workbench view model produced from the selected engine's real native state path.
 *
 * @param ruleGeneration 中文：规则代次。 / English: Rule generation.
 * @param surfaceGeneration 中文：表面代次。 / English: Surface generation.
 * @param engineId 中文：所选引擎 ID。 / English: Selected engine id.
 * @param source 中文：查询来源等级。 / English: Query source tier.
 * @param receiverBlockId 中文：接收方方块 ID。 / English: Receiver block id.
 * @param donorBlockId 中文：可选供体方块 ID。 / English: Optional donor block id.
 * @param receiverPosition 中文：接收方位置。 / English: Receiver position.
 * @param donorPosition 中文：可选供体位置。 / English: Optional donor position.
 * @param connectionPlaceholder 中文：是否为连接占位。 / English: Whether this is a connection placeholder.
 * @param sourceTextureId 中文：源纹理 ID。 / English: Source texture id.
 * @param sourceSprite 中文：冻结的源精灵。 / English: Frozen source sprite.
 * @param face 中文：查询面。 / English: Query face.
 * @param requestedMethod 中文：请求的连接方法。 / English: Requested connection method.
 * @param resolvedMethod 中文：解析后的具体方法。 / English: Resolved concrete method.
 * @param connections 中文：邻接状态。 / English: Neighbor connections.
 * @param plan 中文：过程连接计划。 / English: Procedural connection plan.
 * @param faceResult 中文：精确面预览结果。 / English: Exact face preview result.
 */
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
