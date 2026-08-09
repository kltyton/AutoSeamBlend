package com.kltyton.autoseamblend.fabric.compat.athena.runtime;

import com.kltyton.autoseamblend.compat.athena.runtime.AthenaNativeProvider;
import com.kltyton.autoseamblend.compat.athena.runtime.AthenaNativeStateSampler;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolver.Donor;
import com.kltyton.autoseamblend.runtime.render.BakedQuadTextureBasis;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import earth.terrarium.athena.api.client.fabric.WrappedGetter;
import earth.terrarium.athena.api.client.models.AthenaQuad;
import earth.terrarium.athena.api.client.utils.CtmState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;

/**
 * 中文：使用 Athena 4.7.3 的 CtmState、47 切片 provider 与原生 Quad 语义发射
 * TOP/replacement/overlay。严格移植 26.1.2 NeoForge AthenaNativeQuadProcessor：
 * 非 overlay 替换保留接收 Quad 的几何与本地 UV（retexture），overlay 才使用
 * AthenaQuad 的原生整面烘焙几何。
 *
 * English: Emits TOP/replacement/overlay through Athena 4.7.3's CtmState and the
 * 47-slice provider, porting the 26.1.2 NeoForge AthenaNativeQuadProcessor
 * semantics exactly: non-overlay replacements keep the receiver quad's geometry
 * and local UVs (retexture); only overlays use the native AthenaQuad full-face
 * baked geometry.
 */
final class FabricAthenaNativeQuadProcessor {
    private static final int TILE_COUNT = 47;

    /** 中文：overlay 相对 base 面的 1/2048 法向偏移，移植 NeoForge 已验收契约。 / English: 1/2048 outward normal offset for overlay quads, ported from the accepted NeoForge contract. */
    private static final float OVERLAY_OFFSET =
            1.0F / 2048.0F;

    /** 中文：本地 UV 重映射的退化精灵容差。 / English: Degenerate-sprite tolerance for the local-UV remap. */
    private static final float UV_EPSILON = 1.0e-6F;

    private FabricAthenaNativeQuadProcessor() {}

    /**
     * 中文：非 overlay replacement 路径。采样 Athena 原生状态（投影到实际 Quad 纹理空间），
     * 对 provider 选出的每个原生 Quad 用其槽位精灵重贴图接收 Quad；保留接收 Quad 的几何、
     * 本地 UV、透明玻璃层与正反两面。与 NeoForge process(source, sprites, ..., fullFace,
     * empty) 语义一致。
     *
     * English: Non-overlay replacement path. Samples Athena's native state (projected into the
     * actual quad texture space) and retextures the receiver quad with each selected native
     * slot sprite; receiver geometry, local UVs, transparent layers, and both pane sides are
     * preserved. Matches NeoForge process(source, sprites, ..., fullFace, empty).
     */
    static List<BakedQuad> process(
            BakedQuad source,
            TextureAtlasSprite[] stateSprites,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            ConnectionRuleSet<Block> rules) {
        Objects.requireNonNull(source, "source");
        stateSprites = Objects.requireNonNull(
                        stateSprites,
                        "stateSprites")
                .clone();
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(rules, "rules");
        if (stateSprites.length != TILE_COUNT) {
            return List.of();
        }
        CtmState nativeState =
                AthenaNativeStateSampler.sampleInTextureSpace(
                        new WrappedGetter(level),
                        state,
                        pos,
                        source.direction(),
                        BakedQuadTextureBasis.resolve(source),
                        rules,
                        Set.of());
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
            output.add(
                    retexture(
                            source,
                            stateSprites[slot]));
        }
        return List.copyOf(output);
    }

    /**
     * 中文：只补全缺失精灵：provider 恰好选中一个槽位时用该精灵重贴图源 Quad，否则透传。
     * 移植 NeoForge completeMissing。
     *
     * English: Complements only the missing sprite: when the provider selects exactly one slot
     * the source quad is retextured with that sprite, otherwise nothing is produced. Ports
     * NeoForge completeMissing.
     */
    static Optional<BakedQuad> completeMissing(
            BakedQuad source,
            TextureAtlasSprite[] stateSprites,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            ConnectionRuleSet<Block> rules) {
        Objects.requireNonNull(source, "source");
        stateSprites = Objects.requireNonNull(
                        stateSprites,
                        "stateSprites")
                .clone();
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(rules, "rules");
        if (stateSprites.length != TILE_COUNT) {
            return Optional.empty();
        }
        CtmState nativeState =
                AthenaNativeStateSampler.sampleInTextureSpace(
                        new WrappedGetter(level),
                        state,
                        pos,
                        source.direction(),
                        BakedQuadTextureBasis.resolve(source),
                        rules,
                        Set.of());
        List<AthenaQuad> selected =
                AthenaNativeProvider.quads(nativeState);
        if (selected.size() != 1) {
            return Optional.empty();
        }
        int slot = selected.getFirst().sprite();
        if (slot < 0
                || slot >= stateSprites.length
                || stateSprites[slot] == null) {
            return Optional.empty();
        }
        return Optional.of(
                retexture(
                        source,
                        stateSprites[slot]));
    }

    /**
     * 中文：保留接收 Quad 几何与本地 UV，只把源精灵局部 UV 重映射到目标精灵区域；与
     * NeoForge NeoForgeQuadRetexturing.replace 语义一致（保持源 quad 身份、透明玻璃层与
     * 物品渲染类型）。Fabric 的 BakedQuad 只携带位置/UV/MaterialInfo，直接在记录上构造新
     * Quad，热路径无渲染器 emitter 分配。
     *
     * English: Preserves the receiver quad's geometry and local UVs while remapping the source
     * sprite's local UVs into the target sprite region; matches NeoForge
     * NeoForgeQuadRetexturing.replace (source quad identity, transparent layers, and item
     * render type are kept). The 26.1 BakedQuad record carries only positions/UVs/MaterialInfo,
     * so a new quad is constructed directly without per-quad renderer emitter allocation.
     */
    static BakedQuad retexture(
            BakedQuad source,
            TextureAtlasSprite target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        BakedQuad.MaterialInfo sourceInfo =
                source.materialInfo();
        TextureAtlasSprite original =
                sourceInfo.sprite();
        float sourceWidth =
                original.getU1() - original.getU0();
        float sourceHeight =
                original.getV1() - original.getV0();
        long uv0 = source.packedUV(0);
        long uv1 = source.packedUV(1);
        long uv2 = source.packedUV(2);
        long uv3 = source.packedUV(3);
        if (Math.abs(sourceWidth) > UV_EPSILON
                && Math.abs(sourceHeight) > UV_EPSILON) {
            uv0 = remapUv(source, original, target, 0);
            uv1 = remapUv(source, original, target, 1);
            uv2 = remapUv(source, original, target, 2);
            uv3 = remapUv(source, original, target, 3);
        }
        BakedQuad.MaterialInfo targetInfo =
                new BakedQuad.MaterialInfo(
                        target,
                        sourceInfo.layer(),
                        sourceInfo.itemRenderType(),
                        sourceInfo.tintIndex(),
                        sourceInfo.shade(),
                        sourceInfo.lightEmission());
        return new BakedQuad(
                source.position0(),
                source.position1(),
                source.position2(),
                source.position3(),
                uv0,
                uv1,
                uv2,
                uv3,
                source.direction(),
                targetInfo);
    }

    /**
     * 中文：overlay 专用路径：用 donor 语义采样 Athena 原生八方向状态，非 allTrue 时对每个
     * 原生 Quad 烘焙整面几何。几何/精灵语义与 NeoForge process(..., overlayRequest) 一致；
     * 固定 ARGB 色与 CUTOUT 层在 emitOverlayTinted 发射阶段写入（Fabric BakedQuad 无顶点色）。
     *
     * English: Overlay-only path sampling Athena's native eight-way state through donor
     * semantics and baking native full-face geometry per quad when the state is not allTrue.
     * Geometry/sprite semantics match NeoForge process(..., overlayRequest); the fixed ARGB
     * tint and CUTOUT layer are written at emission in emitOverlayTinted because Fabric's
     * BakedQuad carries no vertex colors.
     */
    static List<BakedQuad> processOverlay(
            QuadEmitter scratch,
            BakedQuad source,
            TextureAtlasSprite[] stateSprites,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState receiver,
            ConnectionRuleSet<Block> rules,
            boolean fullFace,
            OverlayRequest request) {
        Objects.requireNonNull(scratch, "scratch");
        Objects.requireNonNull(source, "source");
        stateSprites = Objects.requireNonNull(
                        stateSprites,
                        "stateSprites")
                .clone();
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(request, "request");
        if (!fullFace) {
            return List.of();
        }
        if (stateSprites.length != TILE_COUNT) {
            return List.of();
        }
        CtmState nativeState =
                FabricAthenaNativeOverlayStateSampler
                        .state(
                level,
                pos,
                receiver,
                request.donor(),
                source.direction(),
                rules,
                request.surfaces());
        if (nativeState.allTrue()) {
            return List.of();
        }
        return bakeOverlaySelected(
                scratch,
                source,
                nativeState,
                stateSprites);
    }

    /**
     * 中文：把已确定的 overlay CtmState 经 Athena 原生 provider 转换为 BakedQuad 列表，
     * 用 bakeNative 重建原生整面几何。
     *
     * English: Converts a decided overlay CtmState into a BakedQuad list through Athena's
     * native provider, rebuilding native full-face geometry through bakeNative.
     */
    private static List<BakedQuad> bakeOverlaySelected(
            QuadEmitter scratch,
            BakedQuad source,
            CtmState nativeState,
            TextureAtlasSprite[] stateSprites) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(nativeState, "nativeState");
        Objects.requireNonNull(stateSprites, "stateSprites");
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
            TextureAtlasSprite sprite =
                    stateSprites[slot];
            output.add(
                    bakeNative(
                            scratch,
                            source,
                            nativeQuad,
                            sprite));
        }
        return List.copyOf(output);
    }

    /**
     * 中文：使用 FRAPI square+materialBake 的原生路径重建 AthenaQuad 的整面几何（与
     * AthenaBakedModel.emitQuads 相同数学），再转为 BakedQuad 供模型发射。scratch 由模型
     * 每次发射调用复用，避免热路径逐 Quad 分配。
     *
     * English: Rebuilds the AthenaQuad full-face geometry through the same FRAPI
     * square+materialBake math AthenaBakedModel.emitQuads uses, then converts it to a
     * BakedQuad for the model to emit. The scratch emitter is reused per emission call to
     * avoid per-quad hot-path allocation.
     */
    private static BakedQuad bakeNative(
            QuadEmitter scratch,
            BakedQuad source,
            AthenaQuad nativeQuad,
            TextureAtlasSprite sprite) {
        Direction face = source.direction();
        scratch.clear();
        scratch.square(
                face,
                nativeQuad.left(),
                nativeQuad.bottom(),
                nativeQuad.right(),
                nativeQuad.top(),
                nativeQuad.depth());
        scratch.materialBake(
                new Material.Baked(
                        sprite,
                        false),
                bakeFlags(nativeQuad.rotation()));
        scratch.tintIndex(
                source.materialInfo().tintIndex());
        return scratch.toBakedQuad(sprite);
    }

    /**
     * 中文：发射已烘焙 overlay Quad：沿面法向偏移 1/2048，固定 ARGB 直通四个顶点，规范到
     * CUTOUT 层并禁用 tint index。FRAPI QuadEmitter.color 消费 ARGB（0xAARRGGBB），indigo
     * 只在写入 vanilla 顶点缓冲时转换为 ABGR。
     *
     * English: Emits a baked overlay quad: shifts vertices outward by 1/2048 along the face
     * normal, passes the fixed ARGB through to all four vertices, normalizes the quad to the
     * CUTOUT layer, and disables the tint index. FRAPI QuadEmitter.color consumes ARGB
     * (0xAARRGGBB) and indigo converts to ABGR only at the vanilla vertex boundary.
     */
    static void emitOverlayTinted(
            QuadEmitter output,
            BakedQuad baked,
            int tint) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(baked, "baked");
        Direction face = baked.direction();
        Vector3fc normal = face.getUnitVec3f();
        float nx = normal.x() * OVERLAY_OFFSET;
        float ny = normal.y() * OVERLAY_OFFSET;
        float nz = normal.z() * OVERLAY_OFFSET;
        QuadEmitter out = output.fromBakedQuad(baked);
        out.chunkLayer(ChunkSectionLayer.CUTOUT);
        for (int vertex = 0; vertex < 4; vertex++) {
            out.pos(
                    vertex,
                    out.x(vertex) + nx,
                    out.y(vertex) + ny,
                    out.z(vertex) + nz);
            out.color(vertex, tint);
        }
        out.tintIndex(-1);
        out.emit();
    }

    private static long remapUv(
            BakedQuad source,
            TextureAtlasSprite original,
            TextureAtlasSprite target,
            int vertex) {
        float width = original.getU1()
                - original.getU0();
        float height = original.getV1()
                - original.getV0();
        float u = (UVPair.unpackU(
                                source.packedUV(vertex))
                        - original.getU0())
                / width;
        float v = (UVPair.unpackV(
                                source.packedUV(vertex))
                        - original.getV0())
                / height;
        return UVPair.pack(
                target.getU(u),
                target.getV(v));
    }

    private static int bakeFlags(
            Rotation rotation) {
        return MutableQuadView.BAKE_LOCK_UV
                | (Objects.requireNonNull(
                                        rotation,
                                        "rotation")
                                .ordinal()
                        & 0x3);
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
