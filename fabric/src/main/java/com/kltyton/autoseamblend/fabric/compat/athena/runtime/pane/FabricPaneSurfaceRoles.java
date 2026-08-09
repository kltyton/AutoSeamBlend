package com.kltyton.autoseamblend.fabric.compat.athena.runtime.pane;

import com.kltyton.autoseamblend.inference.InferenceFacts;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.StateSurface;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import com.kltyton.autoseamblend.texture.mask.TextureFrameProfile;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：26.1.2 Fabric pane 的 loader 专属首烤表面收集组件。角色选择语义（body/edge、
 * sibling cap 借用）属于 common，由 {@link
 * com.kltyton.autoseamblend.compat.athena.runtime.pane.AthenaPaneSurfaceRoles} 承担，本
 * 组件只负责已发布表面快照为空（首次烘焙）时从同代基础模型按 quad 几何方向收集
 * StateSurface；facts/fullFace/frameProfile 为不参与候选选择的占位，与 1.21.1 ce33d6c
 * surfaceFacesFromModel 合同一致。
 *
 * <p>English: The loader-specific first-bake surface collection for the 26.1.2 Fabric pane.
 * Role selection (body/edge and sibling cap borrowing) is common and owned by {@link
 * com.kltyton.autoseamblend.compat.athena.runtime.pane.AthenaPaneSurfaceRoles}; this
 * component only collects a StateSurface from the same-generation base model by quad
 * geometry direction when the published surface snapshot is empty (first bake);
 * facts/fullFace/frameProfile are placeholders that never participate in candidate
 * selection, matching the 1.21.1 ce33d6c surfaceFacesFromModel contract.
 */
public final class FabricPaneSurfaceRoles {
    private FabricPaneSurfaceRoles() {}

    /** 中文：首烤无已发布表面时，从同代基础模型收集与已发布表面同语义的 StateSurface；按 quad 几何方向分组并按精灵去重。 / English: First-bake collection of a StateSurface from the same-generation base model with the same semantics as published surfaces, grouped by quad geometry direction and deduplicated by sprite. */
    public static StateSurface surfaceFacesFromModel(
            BlockState state,
            BlockStateModel model) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(model, "model");
        EnumMap<Direction, LinkedHashMap<Identifier, FaceDraft>> drafts =
                new EnumMap<>(Direction.class);
        ArrayList<BlockStateModelPart> parts =
                new ArrayList<>();
        model.collectParts(
                RandomSource.create(0L),
                parts);
        for (BlockStateModelPart part : parts) {
            for (Direction cullFace
                    : Direction.values()) {
                collect(
                        part.getQuads(cullFace),
                        drafts);
            }
            collect(
                    part.getQuads(null),
                    drafts);
        }
        EnumMap<Direction, List<FaceSurface>> faces =
                new EnumMap<>(Direction.class);
        drafts.forEach((direction, bySprite) -> {
            ArrayList<FaceSurface> resolved =
                    new ArrayList<>(bySprite.size());
            bySprite.values().forEach(draft -> {
                resolved.add(new FaceSurface(
                        direction,
                        draft.sprite(),
                        draft.tintIndex(),
                        draft.fullFace(),
                        fullyTransparent(
                                draft.sprite()
                                        .contents()),
                        draft.representativeQuad(),
                        InferenceFacts.unknown(),
                        ConnectionMethod.NONE,
                        OverlayCutoutProfile
                                .thinUniform(),
                        new TextureFrameProfile(
                                0.0F,
                                0.0F,
                                0.0F,
                                0.0F)));
            });
            faces.put(
                    direction,
                    List.copyOf(resolved));
        });
        return new StateSurface(
                state,
                faces);
    }

    private static void collect(
            List<BakedQuad> source,
            EnumMap<Direction, LinkedHashMap<Identifier, FaceDraft>>
                    drafts) {
        if (source.isEmpty()) {
            return;
        }
        for (BakedQuad quad : source) {
            Direction direction = quad.direction();
            if (direction == null) {
                continue;
            }
            TextureAtlasSprite sprite =
                    quad.materialInfo().sprite();
            drafts.computeIfAbsent(
                            direction,
                            ignored ->
                                    new LinkedHashMap<>())
                    .putIfAbsent(
                            sprite.contents().name(),
                            new FaceDraft(
                                    sprite,
                                    quad.materialInfo()
                                            .tintIndex(),
                                    false,
                                    quad));
        }
    }

    private static boolean fullyTransparent(
            SpriteContents contents) {
        // 中文：与 26.1.2 common MinecraftSurfaceCatalog 同一帧选择语义（fastutil IntList
        // 无 findFirst）。
        // English: Same frame selection as the 26.1.2 common MinecraftSurfaceCatalog
        // (fastutil IntList has no findFirst).
        int frame = contents.getUniqueFrames()
                .isEmpty()
                ? 0
                : contents.getUniqueFrames()
                        .getInt(0);
        for (int y = 0;
                y < contents.height();
                y++) {
            for (int x = 0;
                    x < contents.width();
                    x++) {
                if (!contents.isTransparent(
                        frame,
                        x,
                        y)) {
                    return false;
                }
            }
        }
        return true;
    }

    private record FaceDraft(
            TextureAtlasSprite sprite,
            int tintIndex,
            boolean fullFace,
            BakedQuad representativeQuad) {
        private FaceDraft {
            Objects.requireNonNull(
                    sprite,
                    "sprite");
            Objects.requireNonNull(
                    representativeQuad,
                    "representativeQuad");
        }
    }
}
