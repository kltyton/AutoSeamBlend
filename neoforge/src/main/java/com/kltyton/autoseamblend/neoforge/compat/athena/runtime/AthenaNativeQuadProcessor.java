package com.kltyton.autoseamblend.neoforge.compat.athena.runtime;

import com.kltyton.autoseamblend.compat.athena.runtime.AthenaNativeProvider;
import com.kltyton.autoseamblend.neoforge.compat.athena.runtime.overlay.AthenaNativeOverlayStateSampler;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolver.Donor;
import com.kltyton.autoseamblend.neoforge.runtime.render.NeoForgeQuadRetexturing;
import com.kltyton.autoseamblend.runtime.render.BakedQuadTextureBasis;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.texture.geometry.TextureBasis;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import earth.terrarium.athena.api.client.models.AthenaQuad;
import earth.terrarium.athena.api.client.models.TintProvider;
import earth.terrarium.athena.api.client.neoforge.ForgeAthenaUtils;
import earth.terrarium.athena.api.client.utils.CtmState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.quad.MutableQuad;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * 中文：使用 Athena CTM 状态、47 切片提供器和原生 Quad 烘焙器的精确版本桥接。
 *
 * English:
 * Exact-version bridge using Athena's CTM state, 47-slice provider, and native
 * quad baker.
 */
final class AthenaNativeQuadProcessor {
    private static final float OVERLAY_OFFSET = 1.0F / 2048.0F;
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
        if (stateSprites.length != 47) {
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
                        source.direction(),
                        rules,
                        overlayRequest.orElseThrow().surfaces());
        } else {
            TextureBasis textureBasis = BakedQuadTextureBasis.resolve(source);
            nativeState = AthenaNativeConnectionSampler.stateInTextureSpace(
                        level,
                        pos,
                        receiverState,
                        source.direction(),
                        textureBasis,
                        rules);
        }
        if (overlay && nativeState.allTrue()) {
            return List.of();
        }
        List<AthenaQuad> nativeQuads =
                AthenaNativeProvider.quads(nativeState);
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
            BakedQuad baked = overlay
                    ? bakeNative(
                            source,
                            nativeQuad,
                            sprite)
                    : retexture(source, sprite);
            if (overlayRequest.isPresent()) {
                baked = overlay(
                        baked,
                        overlayRequest.orElseThrow().tintColor());
            }
            output.add(baked);
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
        if (stateSprites.length != 47) {
            return Optional.empty();
        }
        TextureBasis textureBasis = BakedQuadTextureBasis.resolve(source);
        CtmState nativeState = AthenaNativeConnectionSampler.stateInTextureSpace(
                        level,
                        pos,
                        state,
                        source.direction(),
                        textureBasis,
                        rules);
        List<AthenaQuad> selected = AthenaNativeProvider.quads(nativeState);
        if (selected.size() != 1) {
            return Optional.empty();
        }
        int slot = selected.getFirst().sprite();
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

    private static BakedQuad bakeNative(
            BakedQuad source,
            AthenaQuad nativeQuad,
            TextureAtlasSprite sprite) {
        int tintIndex =
                source.materialInfo().tintIndex();
        TintProvider tint = tintIndex >= 0
                ? new TintProvider.Index(tintIndex)
                : null;
        return ForgeAthenaUtils.bakeQuad(
                nativeQuad,
                source.direction(),
                new Material.Baked(sprite, false),
                tint);
    }

    private static BakedQuad overlay(
            BakedQuad source,
            int tintColor) {
        MutableQuad output =
                new MutableQuad().setFrom(source);
        output.setTintIndex(-1);
        output.setColor(tintColor);
        // 中文：overlay 保留 Athena 原生整面烘焙几何，并规范到 cutout 材质层，避免继承接收面的透明层。
        // English: Keep Athena's native full-face overlay geometry and normalize it to the cutout layer instead of inheriting the receiver layer.
        output.setSprite(
                source.materialInfo().sprite(),
                ChunkSectionLayer.CUTOUT,
                Sheets.cutoutBlockItemSheet());
        Vector3fc normal =
                source.direction().getUnitVec3f();
        for (int vertex = 0; vertex < 4; vertex++) {
            output.setPosition(
                    vertex,
                    new Vector3f(
                                    output.copyPosition(
                                            vertex))
                            .fma(
                                    OVERLAY_OFFSET,
                                    normal));
        }
        return output.toBakedQuad();
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
