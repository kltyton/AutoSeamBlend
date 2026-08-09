package com.kltyton.autoseamblend.fabric.compat.athena.runtime;

import com.kltyton.autoseamblend.compat.athena.runtime.AthenaNativeProvider;
import com.kltyton.autoseamblend.compat.athena.runtime.AthenaNativeStateSampler;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import earth.terrarium.athena.api.client.fabric.WrappedGetter;
import earth.terrarium.athena.api.client.models.AthenaQuad;
import earth.terrarium.athena.api.client.utils.CtmState;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：按 Athena 4.0.6 原生五角色象限组合把接收 Quad 发射为四个半面 Quad。
 * English: Emits the four native-equivalent quadrant quads for a receiver quad through Athena
 * 4.0.6's five-role quadrant composition.
 */
final class FabricAthenaNativeQuadProcessor {
    /** 中文：overlay 相对 base 面的 1/2048 法向偏移，移植 NeoForge 已验收契约。 / English: 1/2048 outward normal offset for overlay quads, ported from the accepted NeoForge contract. */
    private static final float OVERLAY_OFFSET =
            1.0F / 2048.0F;
    /** 中文：本地 UV 重映射的退化精灵容差。 / English: Degenerate-sprite tolerance for the local-UV remap. */
    private static final float UV_EPSILON = 1.0e-6F;

    /**
     * 中文：CUTOUT 材质来源 seam。生产默认从当前 FRAPI renderer 的 MaterialFinder 创建
     * blendMode=CUTOUT 的材质并设置到 emitter；测试可注入 stub 材质，避免单测依赖
     * renderer 初始化。
     *
     * English: CUTOUT material source seam. Production resolves the material from the
     * current FRAPI renderer's MaterialFinder and sets it on the emitter; tests may inject
     * a stub material so the unit test never depends on renderer initialization.
     */
    static final AtomicReference<Supplier<RenderMaterial>>
            CUTOUT_MATERIAL_SOURCE = new AtomicReference<>(
                    FabricAthenaNativeQuadProcessor
                            ::resolveCutoutMaterial);

    /**
     * 中文：从当前 FRAPI renderer 实际解析 CUTOUT 材质；renderer 未初始化时明确失败，
     * 不让测试路径伪造成功。
     *
     * English: Resolves the CUTOUT material from the current FRAPI renderer; fails loudly
     * when the renderer is not initialized so tests never fake success.
     */
    static RenderMaterial resolveCutoutMaterial() {
        Renderer renderer =
                RendererAccess.INSTANCE.getRenderer();
        if (renderer == null) {
            throw new IllegalStateException(
                    "FRAPI renderer is not initialized; "
                            + "cannot resolve CUTOUT material");
        }
        return renderer.materialFinder()
                .blendMode(BlendMode.CUTOUT)
                .find();
    }

    private FabricAthenaNativeQuadProcessor() {}

    static boolean emitReplacement(
            Direction face,
            TextureAtlasSprite sourceSprite,
            TextureAtlasSprite[] stateSprites,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            ConnectionRuleSet<Block> rules,
            BakedQuad source,
            int colorIndex,
            QuadEmitter output,
            OptionalInt overlayTint,
            ConnectionMethod method,
            boolean fullFace) {
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(sourceSprite, "sourceSprite");
        Objects.requireNonNull(stateSprites, "stateSprites");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(overlayTint, "overlayTint");
        if (stateSprites.length != AthenaNativeProvider.ROLE_COUNT) {
            return false;
        }
        // 中文：非 overlay replacement 保持现有普通采样；overlay 必须走
        // emitOverlayReplacement 直接消费预计算状态，禁止在本方法内重采样。
        // English: Non-overlay replacement keeps the existing plain sampling; overlays must
        // go through emitOverlayReplacement with the precomputed state and are never
        // re-sampled inside this method.
        CtmState nativeState = AthenaNativeStateSampler.sample(
                new WrappedGetter(level),
                state,
                pos,
                face,
                rules,
                Set.of());
        if (!fullFace) {
            // 中文：移植已验收 NeoForge AthenaNativeQuadProcessor 的 !fullFace
            // retexture(source, sprite) 语义：保留源 quad 的几何与本地 UV，只把源精灵的
            // 局部 UV 重映射到目标精灵区域；pane 薄条/次级面绝不被 square() 成整面。
            // 草方块与完整玻璃均为 fullFace，继续走下方象限路径，不受影响。
            // English: Ports the accepted NeoForge AthenaNativeQuadProcessor !fullFace
            // retexture(source, sprite) semantics: the source quad's geometry and local
            // UVs are preserved and only the source sprite's local UVs are remapped into
            // the target sprite region; pane strips and secondary faces are never squared
            // into full faces. Grass and full glass are fullFace and keep the quadrant
            // path below, so they are unaffected.
            List<AthenaQuad> quads = AthenaNativeProvider.quads(
                    nativeState,
                    face,
                    stateSprites);
            if (quads.isEmpty()) {
                return false;
            }
            for (AthenaQuad quad : quads) {
                if (quad.sprite() < 0
                        || quad.sprite()
                                >= stateSprites.length
                        || stateSprites[quad.sprite()]
                                == null) {
                    return false;
                }
            }
            boolean emitted = false;
            for (AthenaQuad quad : quads) {
                emitted |= emitRetextured(
                        output,
                        source,
                        sourceSprite,
                        stateSprites[quad.sprite()]);
            }
            return emitted;
        }
        return emitWithState(
                face,
                nativeState,
                stateSprites,
                colorIndex,
                overlayTint,
                output);
    }

    /**
     * 中文：!fullFace 专用发射：从源 BakedQuad 复制几何/本地 UV/光照/法线/tint 到 emitter，
     * 再把源精灵局部 UV 按目标精灵区域重映射；与 NeoForge NeoForgeQuadRetexturing.replace
     * 语义一致（保留源 quad 身份，只换精灵）。
     *
     * <p>English: !fullFace-only emission: copies geometry, local UVs, lightmap, normals,
     * and tint from the source BakedQuad into the emitter, then remaps the source sprite's
     * local UVs into the target sprite region; matches NeoForge
     * NeoForgeQuadRetexturing.replace semantics (the source quad identity is preserved and
     * only the sprite changes).
     */
    private static boolean emitRetextured(
            QuadEmitter output,
            BakedQuad source,
            TextureAtlasSprite sourceSprite,
            TextureAtlasSprite target) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sourceSprite, "sourceSprite");
        Objects.requireNonNull(target, "target");
        // 中文：直接复制源 BakedQuad 顶点数据（几何/颜色/UV/光照/法线），不经过
        // FabricQuadEmitting 的 renderer 材质查找，测试环境无需 FRAPI renderer 初始化。
        // English: Copies the source BakedQuad vertex data directly (geometry, colors, UVs,
        // lightmap, normals) without FabricQuadEmitting's renderer material lookup, so unit
        // tests need no FRAPI renderer initialization.
        output.fromVanilla(
                source.getVertices(),
                0);
        output.colorIndex(
                source.getTintIndex());
        Direction face = source.getDirection();
        if (face != null) {
            output.cullFace(face);
            output.nominalFace(face);
        }
        float sourceWidth = sourceSprite.getU1()
                - sourceSprite.getU0();
        float sourceHeight = sourceSprite.getV1()
                - sourceSprite.getV0();
        if (Math.abs(sourceWidth) > UV_EPSILON
                && Math.abs(sourceHeight) > UV_EPSILON) {
            for (int vertex = 0;
                    vertex < 4;
                    vertex++) {
                float localU = (output.u(vertex)
                        - sourceSprite.getU0())
                        / sourceWidth;
                float localV = (output.v(vertex)
                        - sourceSprite.getV0())
                        / sourceHeight;
                output.uv(
                        vertex,
                        target.getU(localU),
                        target.getV(localV));
            }
        }
        output.emit();
        return true;
    }

    /**
     * 中文：overlay 专用入口：直接消费 donor 循环已算出的 overlay CtmState，禁止再次对
     * 接收方块普通采样；其余发射语义（CUTOUT、1/2048、ARGB 直通、BAKE_LOCK_UV）与
     * emitReplacement 共用 emitWithState。
     *
     * English: Overlay-only entry that consumes the overlay CtmState already computed by the
     * donor loop and never re-samples the receiver; all other emission semantics (CUTOUT,
     * 1/2048, ARGB passthrough, BAKE_LOCK_UV) are shared with emitReplacement through
     * emitWithState.
     */
    static boolean emitOverlayReplacement(
            Direction face,
            CtmState overlayState,
            TextureAtlasSprite[] stateSprites,
            int argbTint,
            QuadEmitter output) {
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(overlayState, "overlayState");
        Objects.requireNonNull(stateSprites, "stateSprites");
        Objects.requireNonNull(output, "output");
        return emitWithState(
                face,
                overlayState,
                stateSprites,
                -1,
                OptionalInt.of(argbTint),
                output);
    }

    /**
     * 中文：把已确定的 CtmState 经 Athena 原生五角色象限组合发射到 emitter。
     *
     * English: Emits the Athena-native five-role quadrant composition of an already-decided
     * CtmState to the emitter.
     */
    private static boolean emitWithState(
            Direction face,
            CtmState nativeState,
            TextureAtlasSprite[] stateSprites,
            int colorIndex,
            OptionalInt overlayTint,
            QuadEmitter output) {
        List<AthenaQuad> quads = AthenaNativeProvider.quads(
                nativeState,
                face,
                stateSprites);
        if (quads.isEmpty()) {
            return false;
        }
        for (AthenaQuad quad : quads) {
            if (quad.sprite() < 0
                    || quad.sprite() >= stateSprites.length
                    || stateSprites[quad.sprite()] == null) {
                return false;
            }
        }
        for (AthenaQuad quad : quads) {
            emitQuad(
                    output,
                    face,
                    quad,
                    stateSprites[quad.sprite()],
                    colorIndex,
                    overlayTint);
        }
        return true;
    }

    static boolean emitDirectReplacement(
            Direction face,
            TextureAtlasSprite sourceSprite,
            TextureAtlasSprite target,
            int colorIndex,
            QuadEmitter output) {
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(sourceSprite, "sourceSprite");
        Objects.requireNonNull(output, "output");
        if (target == null) {
            return false;
        }
        output.square(
                face,
                0.0F,
                0.0F,
                1.0F,
                1.0F,
                0.0F);
        // 中文：square 不写 UV；BAKE_LOCK_UV 让 bakeSprite 按顶点位置计算整面 UV，
        // 否则 UV 全 0 落到精灵角点（退化 UV/错误 Atlas 区域）。
        // English: square() writes no UVs; BAKE_LOCK_UV makes bakeSprite derive full-face
        // UVs from vertex positions, otherwise all UVs stay 0 (degenerate corner UVs).
        output.spriteBake(
                target,
                MutableQuadView.BAKE_LOCK_UV);
        tint(output, colorIndex, OptionalInt.empty());
        output.emit();
        return true;
    }

    private static void emitQuad(
            QuadEmitter output,
            Direction face,
            AthenaQuad quad,
            TextureAtlasSprite sprite,
            int colorIndex,
            OptionalInt overlayTint) {
        if (overlayTint.isPresent()) {
            emitOverlayQuad(
                    output,
                    face,
                    quad,
                    sprite,
                    overlayTint.getAsInt());
            return;
        }
        output.square(
                face,
                quad.left(),
                quad.bottom(),
                quad.right(),
                quad.top(),
                quad.depth());
        output.spriteBake(
                sprite,
                bakeFlags(quad.rotation()));
        tint(output, colorIndex, overlayTint);
        output.emit();
    }

    /**
     * 中文：overlay 专用发射 seam：CUTOUT 材质 + 1/2048 法向偏移 + ARGB 四顶点同色直通。
     * FRAPI QuadEmitter.color 采用 ARGB（0xAARRGGBB），indigo 只在写入 vanilla 顶点缓冲时
     * 转换为 ABGR；已验收 NeoForge 语义相同（入口 ARGB），但 NeoForge 写入 BakedQuad 顶点
     * 时才显式换 ABGR。测试直接驱动本方法观察 QuadEmitter 调用。
     *
     * English: Overlay-only emission seam: CUTOUT material, 1/2048 outward normal offset,
     * and the ARGB tint passed through unchanged to all four vertices. FRAPI
     * QuadEmitter.color consumes ARGB (0xAARRGGBB) and indigo converts to ABGR only when
     * writing the vanilla vertex buffer; the accepted NeoForge semantics share the same ARGB
     * entry, with NeoForge converting to ABGR explicitly for BakedQuad vertices. Tests drive
     * this method directly to observe QuadEmitter calls.
     */
    static void emitOverlayQuad(
            QuadEmitter output,
            Direction face,
            AthenaQuad quad,
            TextureAtlasSprite sprite,
            int argbTint) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(quad, "quad");
        Objects.requireNonNull(sprite, "sprite");
        output.square(
                face,
                quad.left(),
                quad.bottom(),
                quad.right(),
                quad.top(),
                quad.depth());
        output.spriteBake(
                sprite,
                bakeFlags(quad.rotation()));
        output.material(
                CUTOUT_MATERIAL_SOURCE.get().get());
        offsetOutward(output, face);
        // 中文：DonorTintResolver 已返回 ARGB，FRAPI 需要 ARGB；在此转 ABGR 会与 indigo 的
        // vanilla 边界转换叠加成双换序，导致 R/B 互换（青绿色）。必须原样直通。
        // English: DonorTintResolver already returns ARGB and FRAPI consumes ARGB; converting
        // to ABGR here would double-swap with indigo's vanilla-boundary conversion and flip
        // R/B (cyan-green). The tint must pass through unchanged.
        output.color(
                argbTint,
                argbTint,
                argbTint,
                argbTint);
        output.colorIndex(-1);
        output.emit();
    }

    private static void offsetOutward(
            QuadEmitter output,
            Direction face) {
        float nx = face.step().x() * OVERLAY_OFFSET;
        float ny = face.step().y() * OVERLAY_OFFSET;
        float nz = face.step().z() * OVERLAY_OFFSET;
        for (int vertex = 0;
                vertex < 4;
                vertex++) {
            output.pos(
                    vertex,
                    output.x(vertex) + nx,
                    output.y(vertex) + ny,
                    output.z(vertex) + nz);
        }
    }

    private static void tint(
            QuadEmitter output,
            int colorIndex,
            OptionalInt overlayTint) {
        if (overlayTint.isPresent()) {
            int argb = overlayTint.getAsInt();
            // 中文：FRAPI 4 参数 color(c0,c1,c2,c3) 是四个顶点的 packed 颜色，不是 rgba；
            // 必须把同一 packed ARGB 传给全部四个顶点。
            // English: FRAPI's 4-arg color(c0,c1,c2,c3) assigns per-vertex packed colors,
            // not rgba; the same packed ARGB must be passed to all four vertices.
            output.color(
                    argb,
                    argb,
                    argb,
                    argb);
            output.colorIndex(-1);
            return;
        }
        if (colorIndex >= 0) {
            output.colorIndex(colorIndex);
            return;
        }
        output.color(-1, -1, -1, -1);
        output.colorIndex(-1);
    }

    private static int bakeFlags(Rotation rotation) {
        // 中文：旋转位占低 2 位，BAKE_LOCK_UV 必须恒置位，否则 UV 退化。
        // English: rotation occupies the low two bits; BAKE_LOCK_UV must always be set,
        // otherwise UVs degenerate.
        return MutableQuadView.BAKE_LOCK_UV
                | (Objects.requireNonNull(rotation, "rotation").ordinal() & 0x3);
    }
}
