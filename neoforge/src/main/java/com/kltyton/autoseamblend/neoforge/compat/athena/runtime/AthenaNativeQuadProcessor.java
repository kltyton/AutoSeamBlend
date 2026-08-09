package com.kltyton.autoseamblend.neoforge.compat.athena.runtime;

import com.kltyton.autoseamblend.compat.athena.runtime.AthenaNativeProvider;
import com.kltyton.autoseamblend.neoforge.compat.athena.runtime.overlay.AthenaNativeOverlayStateSampler;
import com.kltyton.autoseamblend.neoforge.runtime.render.NeoForgeQuadRetexturing;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolver.Donor;
import com.kltyton.autoseamblend.runtime.render.BakedQuadTextureBasis;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.texture.geometry.TextureBasis;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import earth.terrarium.athena.api.client.models.AthenaQuad;
import earth.terrarium.athena.api.client.models.TintProvider;
import earth.terrarium.athena.api.client.neoforge.ForgeAthenaUtils;
import earth.terrarium.athena.api.client.utils.CtmState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * 中文：使用 Athena CTM 状态、五角色原生提供器和原生 Quad 烘焙器的精确版本桥接。
 *
 * English:
 * Exact-version bridge using Athena's CTM state, five-role native provider, and native
 * quad baker.
 */
final class AthenaNativeQuadProcessor {
    private static final float OVERLAY_OFFSET = 1.0F / 2048.0F;
    private static final VertexFormat BLOCK_FORMAT =
            DefaultVertexFormat.BLOCK;
    private static final int STRIDE_INTS =
            BLOCK_FORMAT.getVertexSize() / 4;
    private static final int COLOR_OFFSET_INTS =
            BLOCK_FORMAT.getOffset(
                    VertexFormatElement.COLOR) / 4;

    private AthenaNativeQuadProcessor() {}

    static List<BakedQuad> process(
            BakedQuad source,
            TextureAtlasSprite[] stateSprites,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState receiverState,
            ConnectionRuleSet<Block> rules,
            boolean fullFace,
            Optional<OverlayRequest> overlayRequest) {
        Objects.requireNonNull(source, "source");
        stateSprites =
                Objects.requireNonNull(
                                stateSprites,
                                "stateSprites")
                        .clone();
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(receiverState, "receiverState");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(overlayRequest, "overlayRequest");
        if (stateSprites.length != AthenaNativeProvider.ROLE_COUNT) {
            return List.of();
        }
        boolean overlay = overlayRequest.isPresent();
        if (overlay && !fullFace) {
            return List.of();
        }
        CtmState nativeState;
        if (overlay) {
            nativeState = AthenaNativeOverlayStateSampler.state(
                        level,
                        pos,
                        receiverState,
                        overlayRequest.orElseThrow().donor(),
                        source.getDirection(),
                        rules,
                        overlayRequest.orElseThrow().surfaces());
        } else {
            TextureBasis textureBasis = BakedQuadTextureBasis.resolve(source);
            nativeState = AthenaNativeConnectionSampler.stateInTextureSpace(
                        level,
                        pos,
                        receiverState,
                        source.getDirection(),
                        textureBasis,
                        rules);
        }
        if (overlay && nativeState.allTrue()) {
            return List.of();
        }
        List<AthenaQuad> nativeQuads =
                AthenaNativeProvider.quads(
                        nativeState,
                        source.getDirection(),
                        stateSprites);
        if (nativeQuads.isEmpty()) {
            return List.of();
        }
        ArrayList<BakedQuad> output =
                new ArrayList<>(nativeQuads.size());
        for (AthenaQuad nativeQuad : nativeQuads) {
            int slot = nativeQuad.sprite();
            if (slot < 0
                    || slot >= stateSprites.length
                    || stateSprites[slot] == null) {
                return List.of();
            }
            TextureAtlasSprite sprite = stateSprites[slot];
            if (overlay) {
                for (BakedQuad baked
                        : bakeNative(
                                source,
                                nativeQuad,
                                sprite)) {
                    output.add(overlay(
                            baked,
                            overlayRequest.orElseThrow().tintColor()));
                }
            } else if (fullFace) {
                // 中文：fullFace 非 overlay 与 overlay 共用 Athena 4.0.6 原生象限烘焙。
                // ForgeAthenaUtils.bakeQuad 按 nativeQuad.left/right/top/bottom 生成
                // 子矩形几何，AthenaBlockElementFace.getUVs 按同一 bounds 采样精灵子区域；
                // 禁止整面重贴，否则每个 nativeQuad 都变成一张完整源面，partial 状态会
                // 叠出总面积 4 的重叠面（叠色/接缝）。allTrue 时 common provider 按 4.0.6
                // ConnectedBlockModel 合同返回单张 withSprite(1) 整面，自然烘焙为 1 张。
                // 26.1.2 用 47 槽 provider 单整面 quad，可见语义一致，此处保留 4.0.6 的
                // 四象限几何边界；!fullFace（pane 次级面等）维持旧分支。
                // English: fullFace non-overlay shares Athena 4.0.6's native quadrant
                // baker with overlay. bakeQuad builds sub-rect geometry from the native
                // quad's left/right/top/bottom, and AthenaBlockElementFace.getUVs samples
                // the sprite sub-region from the same bounds; full-face retexturing would
                // turn every nativeQuad into a full-source face and stack partial states
                // into total area 4 (overdraw/seams). For allTrue the common provider
                // follows 4.0.6's ConnectedBlockModel contract with a single
                // withSprite(1) full face, so the bake naturally emits one quad. The
                // accepted 26.1.2 visible semantics (single full-face state tile) match,
                // while 4.0.6's four-quadrant geometry boundary is preserved; !fullFace
                // keeps the legacy branch.
                output.addAll(bakeNative(
                        source,
                        nativeQuad,
                        sprite));
            } else {
                output.add(retexture(source, sprite));
            }
        }
        return List.copyOf(output);
    }

    static NeighborConnections connections(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            Direction face,
            ConnectionRuleSet<Block> rules) {
        return AthenaNativeConnectionSampler.sample(
                level,
                pos,
                state,
                face,
                rules);
    }

    static Optional<BakedQuad> completeMissing(
            BakedQuad source,
            TextureAtlasSprite[] stateSprites,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            ConnectionRuleSet<Block> rules) {
        Objects.requireNonNull(source, "source");
        stateSprites =
                Objects.requireNonNull(
                                stateSprites,
                                "stateSprites")
                        .clone();
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(rules, "rules");
        if (stateSprites.length != AthenaNativeProvider.ROLE_COUNT) {
            return Optional.empty();
        }
        TextureBasis textureBasis = BakedQuadTextureBasis.resolve(source);
        CtmState nativeState = AthenaNativeConnectionSampler.stateInTextureSpace(
                        level,
                        pos,
                        state,
                        source.getDirection(),
                        textureBasis,
                        rules);
        List<AthenaQuad> selected = AthenaNativeProvider.quads(
                nativeState,
                source.getDirection(),
                stateSprites);
        // 中文：common provider 已按 4.0.6 ConnectedBlockModel 合同对齐：allTrue 返回
        // 单张 AthenaQuad.withSprite(1) 整面，partial 才返回四个象限。completeMissing
        // 必须接受任意非空 selected 且所有槽一致（allTrue 单 quad 可补全），不能再假设
        // 恒为四个象限。
        // English: The common provider now follows 4.0.6's ConnectedBlockModel contract:
        // allTrue returns a single AthenaQuad.withSprite(1) full face while partial states
        // return four quadrants. completeMissing must accept any non-empty selection whose
        // sprite slots are identical (the allTrue single quad is completable) and must not
        // assume four quadrants anymore.
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        int slot = selected.getFirst().sprite();
        for (AthenaQuad quad : selected) {
            if (quad.sprite() != slot) {
                return Optional.empty();
            }
        }
        if (slot < 0
                || slot >= stateSprites.length
                || stateSprites[slot] == null) {
            return Optional.empty();
        }
        TextureAtlasSprite targetSprite = stateSprites[slot];
        return Optional.of(retexture(source, targetSprite));
    }

    static BakedQuad retexture(
            BakedQuad source,
            TextureAtlasSprite target) {
        // 中文：替换分支保留接收 Quad 的几何、局部 UV、透明玻璃材质层以及玻璃板正反两侧。
        // English: The replacement path preserves receiver geometry, local UVs, transparent-glass layers, and both pane sides.
        return NeoForgeQuadRetexturing.replace(
                source,
                target);
    }

    private static List<BakedQuad> bakeNative(
            BakedQuad source,
            AthenaQuad nativeQuad,
            TextureAtlasSprite sprite) {
        int tintIndex =
                source.getTintIndex();
        TintProvider tint = tintIndex >= 0
                ? new TintProvider.Index(tintIndex)
                : null;
        return ForgeAthenaUtils.bakeQuad(
                nativeQuad,
                source.getDirection(),
                sprite,
                tint);
    }

    private static BakedQuad overlay(
            BakedQuad source,
            int tintColor) {
        // 中文：overlay 保留 Athena 原生整面烘焙几何，并规范到 cutout 材质层，避免继承接收面的透明层。
        // English: Keep Athena's native full-face overlay geometry and normalize it to the cutout layer instead of inheriting the receiver layer.
        int[] vertices = source.getVertices()
                .clone();
        int packed = packedColor(tintColor);
        Vector3f normal =
                source.getDirection().step();
        for (int vertex = 0;
                vertex < 4;
                vertex++) {
            int base = vertex * STRIDE_INTS;
            vertices[base + COLOR_OFFSET_INTS] =
                    packed;
            vertices[base] =
                    Float.floatToRawIntBits(
                            Float.intBitsToFloat(
                                    vertices[base])
                                    + normal.x()
                                            * OVERLAY_OFFSET);
            vertices[base + 1] =
                    Float.floatToRawIntBits(
                            Float.intBitsToFloat(
                                    vertices[base + 1])
                                    + normal.y()
                                            * OVERLAY_OFFSET);
            vertices[base + 2] =
                    Float.floatToRawIntBits(
                            Float.intBitsToFloat(
                                    vertices[base + 2])
                                    + normal.z()
                                            * OVERLAY_OFFSET);
        }
        return new BakedQuad(
                vertices,
                -1,
                source.getDirection(),
                source.getSprite(),
                source.isShade());
    }

    private static int packedColor(int argb) {
        int alpha = (argb >> 24) & 0xFF;
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        return (alpha << 24)
                | (blue << 16)
                | (green << 8)
                | red;
    }

    /** 中文：overlay 同时保留真实接收状态、完整供体语义与同代的表面快照。 / English: Keeps the real receiver, complete donor semantics, and the same-generation surface snapshot for overlay sampling. */
    record OverlayRequest(
            Donor donor,
            MinecraftSurfaceCatalog.Snapshot surfaces,
            int tintColor) {
        OverlayRequest {
            Objects.requireNonNull(donor, "donor");
            Objects.requireNonNull(surfaces, "surfaces");
        }
    }
}
