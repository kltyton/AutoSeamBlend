package com.kltyton.autoseamblend.fabric.frontend.uilib.render.preview;

import com.kltyton.autoseamblend.authoring.preview.BlockPreviewTint;
import com.kltyton.autoseamblend.authoring.preview.PreviewNeighborPosition;
import com.kltyton.autoseamblend.authoring.preview.VirtualPreviewLevel;
import com.kltyton.autoseamblend.frontend.model.preview.PreviewSceneState;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.Node;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.PublicationPort;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.PublishedCapture;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.RenderRequest;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.SceneGeometry;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.SceneGeometryCache;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.TintPort;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneRenderState;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import java.util.Objects;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinder;
import net.fabricmc.fabric.impl.client.renderer.SpriteFinderImpl;
import com.kltyton.autoseamblend.fabric.mixin.TextureAtlasAccessor;

/**
 * 中文：把 common 上下文模型捕获、发布读锁、动态 tint 与 PIP 提交接到 Fabric 渲染路径。
 * English: Adapts common contextual model capture, publication locking,
 * dynamic tint, and PIP submission to the Fabric rendering path.
 */
public final class FabricBlockScenePorts {
    private static final PublicationPort PUBLICATION = new PublicationPort() {
        @Override
        public SceneGeometry capture(
                PublishedCapture capture) {
            return ReloadPublication.read(runtime ->
                    capture.capture(
                            runtime.surfaces()
                                    .generation()));
        }

        @Override
        public long currentGeneration() {
            return ReloadPublication.current()
                    .surfaces()
                    .generation();
        }
    };
    private static final TintPort TINT =
            BlockPreviewTint::values;

    private FabricBlockScenePorts() {}

    public static SceneGeometryCache geometryCache() {
        return new SceneGeometryCache(
                PUBLICATION,
                FabricBlockScenePorts::capture);
    }

    public static void submit(
            GuiGraphicsExtractor graphics,
            RenderRequest request) {
        Objects.requireNonNull(graphics, "graphics")
                .guiRenderState
                .addPicturesInPictureState(
                        new BlockSceneRenderState(
                                request,
                                new net.minecraft.client.gui.navigation
                                        .ScreenRectangle(
                                                request.x0(),
                                                request.y0(),
                                                request.x1()
                                                        - request.x0(),
                                                request.y1()
                                                        - request.y0())));
    }

    private static SceneGeometry capture(
            PreviewSceneState scene) {
        Objects.requireNonNull(scene, "scene");
        return PUBLICATION.capture(
                surfaceGeneration ->
                        capturePublished(
                                scene,
                                surfaceGeneration));
    }

    private static SceneGeometry capturePublished(
            PreviewSceneState scene,
            long surfaceGeneration) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos origin = minecraft.player == null
                ? BlockPos.ZERO
                : minecraft.player.blockPosition();
        BlockAndTintGetter delegate = minecraft.level == null
                ? BlockAndTintGetter.EMPTY
                : minecraft.level;
        BlockAndTintGetter virtual = new VirtualPreviewLevel(
                delegate,
                origin,
                scene.centerState(),
                scene.neighbors());
        ArrayList<Node> nodes = new ArrayList<>();
        nodes.add(node(
                minecraft,
                virtual,
                scene.centerState(),
                origin,
                BlockPos.ZERO));
        for (Map.Entry<PreviewNeighborPosition, BlockState> neighbor
                : scene.neighbors().entrySet()) {
            BlockPos offset = new BlockPos(
                    neighbor.getKey().x(),
                    neighbor.getKey().y(),
                    neighbor.getKey().z());
            nodes.add(node(
                    minecraft,
                    virtual,
                    neighbor.getValue(),
                    origin.offset(offset),
                    offset));
        }
        return new SceneGeometry(
                nodes,
                origin,
                scene.sceneRevision(),
                surfaceGeneration);
    }

    private static Node node(
            Minecraft minecraft,
            BlockAndTintGetter level,
            BlockState state,
            BlockPos worldPosition,
            BlockPos sceneOffset) {
        BlockStateModel model = minecraft
                .getModelManager()
                .getBlockStateModelSet()
                .get(state);
        if (model instanceof FabricBlockStateModel fabricModel) {
            return runtimeNode(
                    minecraft,
                    fabricModel,
                    level,
                    state,
                    worldPosition,
                    sceneOffset);
        }
        ArrayList<BlockStateModelPart> parts =
                new ArrayList<>();
        model.collectParts(
                RandomSource.create(
                        worldPosition.asLong()),
                parts);
        return new Node(
                parts,
                TINT.values(
                        level,
                        state,
                        worldPosition),
                (model.materialFlags() & 1) != 0,
                sceneOffset.getX(),
                sceneOffset.getY(),
                sceneOffset.getZ());
    }

    /**
     * 中文：通过 Fabric 运行时发射路径捕获与 Runtime 同源的连接纹理几何。
     *
     * English: Captures connection-texture geometry identical to Runtime via
     * the Fabric runtime emission path.
     */
    private static Node runtimeNode(
            Minecraft minecraft,
            FabricBlockStateModel model,
            BlockAndTintGetter level,
            BlockState state,
            BlockPos worldPosition,
            BlockPos sceneOffset) {
        SpriteFinder sprites = spriteFinder(minecraft);
        ArrayList<BakedQuad> quads = new ArrayList<>();
        ArrayList<Direction> cullFaces = new ArrayList<>();
        boolean[] translucent = {false};
        Consumer<MutableQuadView> capture = quad -> {
            Direction cullFace = quad.cullFace();
            quads.add(quad.toBakedQuad(sprites.find(quad)));
            cullFaces.add(cullFace);
            if (quad.chunkLayer()
                    == ChunkSectionLayer.TRANSLUCENT) {
                translucent[0] = true;
            }
        };
        QuadEmitter emitter =
                Renderer.get().quadEmitter(capture);
        model.emitQuads(
                emitter,
                level,
                worldPosition,
                state,
                RandomSource.create(
                        worldPosition.asLong()),
                face -> false);
        BlockStateModelPart part =
                new CollectedPart(
                        quads,
                        cullFaces);
        return new Node(
                List.of(part),
                TINT.values(
                        level,
                        state,
                        worldPosition),
                translucent[0],
                sceneOffset.getX(),
                sceneOffset.getY(),
                sceneOffset.getZ());
    }

    private static SpriteFinder spriteFinder(
            Minecraft minecraft) {
        TextureAtlas atlas = minecraft
                .getAtlasManager()
                .getAtlasOrThrow(
                        AtlasIds.BLOCKS);
        Map<net.minecraft.resources.Identifier, TextureAtlasSprite>
                textures =
                        ((TextureAtlasAccessor) (Object) atlas)
                                .autoseamblend$texturesByName();
        return new SpriteFinderImpl(
                new LinkedHashMap<>(textures),
                atlas.missingSprite());
    }

    private static final class CollectedPart
            implements BlockStateModelPart {
        private final EnumMap<Direction, List<BakedQuad>>
                byDirection =
                        new EnumMap<>(Direction.class);
        private final List<BakedQuad> unculled =
                new ArrayList<>();
        private final Material.Baked particle;

        private CollectedPart(
                List<BakedQuad> quads,
                List<Direction> cullFaces) {
            for (int index = 0;
                    index < quads.size();
                    index++) {
                Direction cullFace =
                        cullFaces.get(index);
                if (cullFace == null) {
                    unculled.add(quads.get(index));
                } else {
                    byDirection
                            .computeIfAbsent(
                                    cullFace,
                                    ignored ->
                                            new ArrayList<>())
                            .add(quads.get(index));
                }
            }
            Material.Baked resolved = null;
            for (BakedQuad quad : quads) {
                if (quad.materialInfo() != null) {
                    resolved = new Material.Baked(
                            quad.materialInfo().sprite(),
                            quad.materialInfo().layer()
                                    == ChunkSectionLayer.TRANSLUCENT);
                    break;
                }
            }
            particle = resolved;
        }

        @Override
        public List<BakedQuad> getQuads(
                Direction direction) {
            return direction == null
                    ? unculled
                    : byDirection.getOrDefault(
                            direction,
                            List.of());
        }

        @Override
        public boolean useAmbientOcclusion() {
            return true;
        }

        @Override
        public Material.Baked particleMaterial() {
            return particle;
        }

        @Override
        public int materialFlags() {
            return 0;
        }
    }
}
