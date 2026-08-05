package com.kltyton.autoseamblend.runtime.surface;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Transparency;
import com.kltyton.autoseamblend.discovery.SurfaceRepresentativeFacts;
import com.kltyton.autoseamblend.foundation.Constants;
import com.kltyton.autoseamblend.engine.query.SurfaceFace;
import com.kltyton.autoseamblend.inference.ConnectionAxis;
import com.kltyton.autoseamblend.inference.InferenceFacts;
import com.kltyton.autoseamblend.inference.InferenceFacts.FactState;
import com.kltyton.autoseamblend.inference.SurfaceMethodDecisionPolicy;
import com.kltyton.autoseamblend.inference.TransparentSelfConnectionInference;
import com.kltyton.autoseamblend.mixin.minecraft.SpriteContentsImageAccessor;
import com.kltyton.autoseamblend.runtime.render.ProceduralConnectionPlan;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import com.kltyton.autoseamblend.texture.mask.TextureFrameProfile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：每个 NeoForge 引擎适配器使用的完整模型烘焙事实；发布此快照后不再发生图像编码或资源查找。
 *
 * English:
 * Complete model-bake facts used by every NeoForge engine adapter.
 *
 * <p>No image encoding or resource lookup occurs after this snapshot is published.
 */
public final class MinecraftSurfaceCatalog {
    private static final float EPSILON = 1.0e-4F;
    private MinecraftSurfaceCatalog() {}

    /** 中文：在模型所属线程构造烘焙表面候选，不立即发布。 / English: Builds a baked-surface candidate on the model-owning thread without immediate publication. */
    public static Snapshot prepare(
            Map<BlockState, BlockStateModel> models,
            PreparedSurfaceMethods.Snapshot preparedMethods,
            long generation) {
        Objects.requireNonNull(models, "models");
        Objects.requireNonNull(
                preparedMethods,
                "preparedMethods");
        if (generation <= 0) {
            throw new IllegalArgumentException(
                    "generation must be positive");
        }
        IdentityHashMap<TextureAtlasSprite, TextureFacts> textureFacts = new IdentityHashMap<>();
        LinkedHashMap<BlockState, StateSurface> states = new LinkedHashMap<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        models.forEach((state, model) -> inspect(
                        state,
                        model,
                        textureFacts,
                        preparedMethods,
                        diagnostics)
                .ifPresent(surface -> states.put(state, surface)));
        Snapshot next = new Snapshot(
                generation,
                states,
                diagnostics);
        return next;
    }

    /** 中文：根代次提交后清理依赖旧表面的覆盖缓存并记录结果。 / English: Clears overlay caches that depended on old surfaces and records the result after root commit. */
    public static void onPublished(
            Snapshot next) {
        Objects.requireNonNull(next, "next");
        ProceduralConnectionPlan.clearCachedOverlays();
        Constants.LOG.info(
                "Published AutoSeamBlend Minecraft surface generation {}: states={}, diagnostics={}",
                next.generation(),
                next.states().size(),
                next.diagnostics().size());
    }

    private static Optional<StateSurface> inspect(
            BlockState state,
            BlockStateModel model,
            IdentityHashMap<TextureAtlasSprite, TextureFacts> textureFacts,
            PreparedSurfaceMethods.Snapshot preparedMethods,
            List<String> diagnostics) {
        ArrayList<BlockStateModelPart> parts = new ArrayList<>();
        try {
            model.collectParts(RandomSource.create(0L), parts);
        } catch (RuntimeException exception) {
            diagnostics.add("MODEL_PARTS_REJECTED:" + state + ':' + exception.getClass().getSimpleName());
            return Optional.empty();
        }
        if (parts.isEmpty()) {
            diagnostics.add("MODEL_PARTS_EMPTY:" + state);
            return Optional.empty();
        }

        EnumMap<Direction, LinkedHashMap<TextureAtlasSprite, FaceDraft>> drafts =
                new EnumMap<>(Direction.class);
        ArrayList<BakedQuad> allQuads = new ArrayList<>();
        for (BlockStateModelPart part : parts) {
            for (Direction cullFace : Direction.values()) {
                collect(part.getQuads(cullFace), drafts, allQuads);
            }
            collect(part.getQuads(null), drafts, allQuads);
        }
        if (allQuads.isEmpty()) {
            diagnostics.add("MODEL_QUADS_EMPTY:" + state);
            return Optional.empty();
        }

        boolean axisAligned = allQuads.stream().allMatch(MinecraftSurfaceCatalog::axisAligned);
        boolean validUv = allQuads.stream().allMatch(MinecraftSurfaceCatalog::validUv);
        EnumSet<Direction> completeFaces = EnumSet.noneOf(Direction.class);
        allQuads.stream()
                .filter(MinecraftSurfaceCatalog::fullFace)
                .map(BakedQuad::direction)
                .forEach(completeFaces::add);
        boolean fullBlock = completeFaces.size() == Direction.values().length;
        boolean topOnly = drafts.keySet().equals(SetView.UP_ONLY);
        long distinctSprites = drafts.values().stream()
                .flatMap(faces -> faces.keySet().stream())
                .distinct()
                .count();

        EnumMap<Direction, List<FaceSurface>> faces = new EnumMap<>(Direction.class);
        drafts.forEach((direction, bySprite) -> {
            ArrayList<FaceSurface> resolved = new ArrayList<>();
            bySprite.values().forEach(draft -> {
                TextureFacts texture = textureFacts.computeIfAbsent(
                        draft.sprite(),
                        MinecraftSurfaceCatalog::inspectTexture);
                InferenceFacts facts = new InferenceFacts(
                        FactState.of(axisAligned),
                        FactState.of(axisAligned),
                        FactState.of(validUv),
                        FactState.of(distinctSprites == 1),
                        FactState.TRUE,
                        FactState.of(texture.transparency().isOpaque()),
                        FactState.of(texture.framedAlpha()),
                        FactState.of(texture.animated()),
                        FactState.of(draft.tintIndex() >= 0),
                        FactState.of(fullBlock),
                        FactState.of(!fullBlock || !draft.fullFace()),
                        FactState.of(topOnly),
                        FactState.FALSE,
                        FactState.TRUE,
                        EnumSet.of(ConnectionAxis.HORIZONTAL, ConnectionAxis.VERTICAL));
                ConnectionMethod method =
                        preparedMethods
                                .method(
                                        state,
                                        direction,
                                        draft.sprite()
                                                .contents()
                                                .name())
                                .orElseGet(() ->
                                        SurfaceMethodDecisionPolicy.resolve(
                                                ConnectionMethod.AUTO,
                                                facts,
                                                TransparentSelfConnectionInference
                                                        .observesEqualStateBoundary(
                                                                state)));
                resolved.add(new FaceSurface(
                        direction,
                        draft.sprite(),
                        draft.tintIndex(),
                        draft.fullFace(),
                        texture.fullyTransparent(),
                        draft.representativeQuad(),
                        facts,
                        method,
                        draft.tintIndex() >= 0
                                ? texture.overlayProfile()
                                        .forTintedSurface()
                                : texture.overlayProfile(),
                        texture.frameProfile()));
            });
            resolved.sort((left, right) -> {
                int opaque = Boolean.compare(
                        right.facts().alphaOpaque().isTrue(),
                        left.facts().alphaOpaque().isTrue());
                if (opaque != 0) {
                    return opaque;
                }
                int tint = Boolean.compare(right.tintIndex() >= 0, left.tintIndex() >= 0);
                if (tint != 0) {
                    return tint;
                }
                return left.sprite()
                        .contents()
                        .name()
                        .toString()
                        .compareTo(right.sprite().contents().name().toString());
            });
            faces.put(direction, List.copyOf(resolved));
        });
        return Optional.of(new StateSurface(state, faces));
    }

    private static void collect(
            List<BakedQuad> quads,
            EnumMap<Direction, LinkedHashMap<TextureAtlasSprite, FaceDraft>> drafts,
            List<BakedQuad> allQuads) {
        for (BakedQuad quad : quads) {
            allQuads.add(quad);
            TextureAtlasSprite sprite = quad.materialInfo().sprite();
            drafts.computeIfAbsent(quad.direction(), ignored -> new LinkedHashMap<>())
                    .merge(
                            sprite,
                            new FaceDraft(
                                    quad.direction(),
                                    sprite,
                                    quad.materialInfo().tintIndex(),
                                    fullFace(quad),
                                    quad),
                            FaceDraft::merge);
        }
    }

    private static TextureFacts inspectTexture(TextureAtlasSprite sprite) {
        SpriteContents contents = sprite.contents();
        Transparency transparency = contents.transparency();
        int width = contents.width();
        int height = contents.height();
        int frame = contents.getUniqueFrames().isEmpty()
                ? 0
                : contents.getUniqueFrames().getInt(0);
        OverlayCutoutProfile overlayProfile =
                overlayProfile(contents, frame);
        if (transparency.isOpaque()) {
            return new TextureFacts(
                    transparency,
                    contents.isAnimated(),
                    false,
                    false,
                    overlayProfile,
                    TextureFrameProfile.fromAlpha(
                            width,
                            height,
                            false,
                            (x, y) -> true));
        }
        if (width < 3 || height < 3) {
            return new TextureFacts(
                    transparency,
                    contents.isAnimated(),
                    false,
                    fullyTransparent(
                            contents,
                            frame,
                            width,
                            height),
                    overlayProfile,
                    TextureFrameProfile.fromAlpha(
                            width,
                            height,
                            false,
                            (x, y) -> !contents.isTransparent(
                                    frame,
                                    x,
                                    y)));
        }
        int opaqueBorder = 0;
        int border = 0;
        int transparentInterior = 0;
        int interior = 0;
        boolean visible = false;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean edge = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                boolean transparent = contents.isTransparent(frame, x, y);
                visible |= !transparent;
                if (edge) {
                    border++;
                    if (!transparent) {
                        opaqueBorder++;
                    }
                } else {
                    interior++;
                    if (transparent) {
                        transparentInterior++;
                    }
                }
            }
        }
        boolean framed = opaqueBorder * 2 >= border
                && transparentInterior * 4 >= interior;
        return new TextureFacts(
                transparency,
                contents.isAnimated(),
                framed,
                !visible,
                overlayProfile,
                TextureFrameProfile.fromAlpha(
                        width,
                        height,
                        framed,
                        (x, y) -> !contents.isTransparent(
                                frame,
                                x,
                                y)));
    }

    private static OverlayCutoutProfile overlayProfile(
            SpriteContents contents,
            int frame) {
        NativeImage image =
                ((SpriteContentsImageAccessor) contents)
                        .autoseamblend$originalImage();
        int frameWidth = contents.width();
        int frameHeight = contents.height();
        int columns = Math.max(
                1,
                image.getWidth() / frameWidth);
        int frameX = Math.floorMod(frame, columns)
                * frameWidth;
        int frameY = Math.floorDiv(frame, columns)
                * frameHeight;
        if (frameX + frameWidth > image.getWidth()
                || frameY + frameHeight > image.getHeight()) {
            frameX = 0;
            frameY = 0;
        }
        int[] pixels = new int[Math.multiplyExact(
                frameWidth,
                frameHeight)];
        for (int y = 0; y < frameHeight; y++) {
            for (int x = 0; x < frameWidth; x++) {
                pixels[y * frameWidth + x] =
                        image.getPixel(
                                frameX + x,
                                frameY + y);
            }
        }
        return OverlayCutoutProfile.fromArgb(
                frameWidth,
                frameHeight,
                pixels);
    }

    private static boolean fullyTransparent(
            SpriteContents contents,
            int frame,
            int width,
            int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
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

    private static boolean axisAligned(BakedQuad quad) {
        return SurfaceQuadGeometry.axisAligned(
                new BakedQuadSurfaceView(quad),
                EPSILON);
    }

    private static boolean fullFace(BakedQuad quad) {
        return SurfaceQuadGeometry.fullFace(
                new BakedQuadSurfaceView(quad),
                EPSILON);
    }

    private static boolean validUv(BakedQuad quad) {
        return SurfaceQuadGeometry.validUv(
                new BakedQuadSurfaceView(quad),
                EPSILON);
    }

    private record BakedQuadSurfaceView(BakedQuad quad)
            implements SurfaceQuadView {
        private BakedQuadSurfaceView {
            Objects.requireNonNull(quad, "quad");
        }

        @Override
        public SurfaceFace face() {
            return SurfaceFace.valueOf(quad.direction().name());
        }

        @Override
        public int vertexCount() {
            return BakedQuad.VERTEX_COUNT;
        }

        @Override
        public float position(int vertex, int component) {
            return quad.position(vertex).get(component);
        }

        @Override
        public float u(int vertex) {
            return UVPair.unpackU(quad.packedUV(vertex));
        }

        @Override
        public float v(int vertex) {
            return UVPair.unpackV(quad.packedUV(vertex));
        }

        @Override
        public float spriteMinU() {
            return quad.materialInfo().sprite().getU0();
        }

        @Override
        public float spriteMaxU() {
            return quad.materialInfo().sprite().getU1();
        }

        @Override
        public float spriteMinV() {
            return quad.materialInfo().sprite().getV0();
        }

        @Override
        public float spriteMaxV() {
            return quad.materialInfo().sprite().getV1();
        }
    }

    public record Snapshot(
            long generation,
            Map<BlockState, StateSurface> states,
            Map<Block, BlockRepresentative>
                    blockRepresentatives,
            List<String> diagnostics) {
        public Snapshot(
                long generation,
                Map<BlockState, StateSurface> states,
                List<String> diagnostics) {
            this(
                    generation,
                    states,
                    representativesByBlock(states),
                    diagnostics);
        }

        public Snapshot {
            if (generation < 0) {
                throw new IllegalArgumentException("generation must be non-negative");
            }
            states = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(states, "states")));
            java.util.IdentityHashMap<
                            Block,
                            BlockRepresentative>
                    representatives =
                            new java.util.IdentityHashMap<>();
            Objects.requireNonNull(
                            blockRepresentatives,
                            "blockRepresentatives")
                    .forEach(representatives::put);
            blockRepresentatives =
                    Collections.unmodifiableMap(
                            representatives);
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }

        public static Snapshot empty() {
            return new Snapshot(0, Map.of(), List.of());
        }

        public static Snapshot empty(
                long generation) {
            return new Snapshot(
                    generation,
                    Map.of(),
                    List.of());
        }

        /**
         * 中文：返回资源重载时已选定的稳定代表面，GUI 打开不再遍历或排序表面。
         *
         * English:
         * Returns the stable representative selected during resource reload so
         * opening the GUI never traverses or sorts surfaces.
         */
        public Optional<BlockRepresentative>
                representative(Block block) {
            return Optional.ofNullable(
                    blockRepresentatives.get(
                            Objects.requireNonNull(
                                    block,
                                    "block")));
        }

        /**
         * 中文：确认方块在本次烘焙的标准模型表中至少发布了一个可观察表面。
         *
         * English:
         * Confirms that this bake generation published at least one observable surface for the block
         * from the standard model table.
         */
        public boolean discovered(Block block) {
            return blockRepresentatives.containsKey(
                    Objects.requireNonNull(block, "block"));
        }

        public Optional<FaceSurface> face(
                BlockState state,
                Direction direction,
                TextureAtlasSprite sprite) {
            StateSurface surface = states.get(state);
            return surface == null
                    ? Optional.empty()
                    : surface.face(direction, sprite);
        }

        public Optional<FaceSurface> preferredFace(BlockState state, Direction direction) {
            StateSurface surface = states.get(state);
            return surface == null
                    ? Optional.empty()
                    : surface.preferredFace(direction);
        }

        private static Map<Block, BlockRepresentative>
                representativesByBlock(
                        Map<BlockState, StateSurface>
                                states) {
            java.util.IdentityHashMap<
                            Block,
                            BlockRepresentative>
                    representatives =
                            new java.util.IdentityHashMap<>();
            Objects.requireNonNull(states, "states")
                    .forEach((state, stateSurface) ->
                            stateSurface.faces()
                                    .forEach((direction, faces) ->
                                            faces.forEach(surface -> {
                                                BlockRepresentative candidate =
                                                        new BlockRepresentative(
                                                                state,
                                                                direction,
                                                                surface);
                                                representatives.merge(
                                                        state.getBlock(),
                                                        candidate,
                                                        (left, right) ->
                                                                REPRESENTATIVE_ORDER
                                                                                .compare(
                                                                                        left,
                                                                                        right)
                                                                        <= 0
                                                                        ? left
                                                                        : right);
                                            })));
            return representatives;
        }

        private static final Comparator<BlockRepresentative>
                REPRESENTATIVE_ORDER =
                        Comparator
                                .comparing((BlockRepresentative candidate) ->
                                        !candidate.state()
                                                .equals(candidate.state()
                                                        .getBlock()
                                                        .defaultBlockState()))
                                .thenComparing((BlockRepresentative candidate) ->
                                        candidate.surface()
                                                        .inferredMethod()
                                                == ConnectionMethod.NONE)
                                .thenComparing(candidate ->
                                        candidate.state()
                                                .toString())
                                .thenComparingInt(candidate ->
                                        candidate.direction()
                                                .ordinal())
                                .thenComparing(candidate ->
                                        candidate.surface()
                                                .sprite()
                                                .contents()
                                                .name()
                                                .toString());
    }

    /**
     * 中文：工作台与导出共享的每方块稳定代表面。
     *
     * English:
     * Stable per-block representative shared by the workbench and export flow.
     */
    public record BlockRepresentative(
            BlockState state,
            Direction direction,
            FaceSurface surface) {
        public BlockRepresentative {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(surface, "surface");
        }
    }

    public record StateSurface(
            BlockState state,
            Map<Direction, List<FaceSurface>> faces) {
        public StateSurface {
            Objects.requireNonNull(state, "state");
            EnumMap<Direction, List<FaceSurface>> stable = new EnumMap<>(Direction.class);
            faces.forEach((direction, values) -> stable.put(direction, List.copyOf(values)));
            faces = Collections.unmodifiableMap(stable);
        }

        public Optional<FaceSurface> face(
                Direction direction,
                TextureAtlasSprite sprite) {
            return faces.getOrDefault(direction, List.of()).stream()
                    .filter(face -> face.sprite() == sprite)
                    .findFirst();
        }

        public Optional<FaceSurface> preferredFace(Direction direction) {
            List<FaceSurface> values = faces.getOrDefault(direction, List.of());
            return values.isEmpty() ? Optional.empty() : Optional.of(values.getFirst());
        }
    }

    public record FaceSurface(
            Direction direction,
            TextureAtlasSprite sprite,
            int tintIndex,
            boolean fullFace,
            boolean fullyTransparent,
            BakedQuad representativeQuad,
            InferenceFacts facts,
            ConnectionMethod inferredMethod,
            OverlayCutoutProfile overlayProfile,
            TextureFrameProfile frameProfile) {
        public FaceSurface {
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(sprite, "sprite");
            Objects.requireNonNull(
                    representativeQuad,
                    "representativeQuad");
            Objects.requireNonNull(facts, "facts");
            Objects.requireNonNull(inferredMethod, "inferredMethod");
            Objects.requireNonNull(
                    overlayProfile,
                    "overlayProfile");
            Objects.requireNonNull(
                    frameProfile,
                    "frameProfile");
        }
    }

    private record FaceDraft(
            Direction direction,
            TextureAtlasSprite sprite,
            int tintIndex,
            boolean fullFace,
            BakedQuad representativeQuad) {
        private FaceDraft merge(FaceDraft other) {
            SurfaceRepresentativeFacts current = new SurfaceRepresentativeFacts(
                    fullFace,
                    tintIndex);
            SurfaceRepresentativeFacts candidate = new SurfaceRepresentativeFacts(
                    other.fullFace,
                    other.tintIndex);
            BakedQuad representative = current.shouldReplaceWith(candidate)
                    ? other.representativeQuad
                    : representativeQuad;
            SurfaceRepresentativeFacts merged = current.merge(candidate);
            return new FaceDraft(
                    direction,
                    sprite,
                    merged.tintIndex(),
                    merged.fullFace(),
                    representative);
        }
    }

    private record TextureFacts(
            Transparency transparency,
            boolean animated,
            boolean framedAlpha,
            boolean fullyTransparent,
            OverlayCutoutProfile overlayProfile,
            TextureFrameProfile frameProfile) {}

    private static final class SetView {
        private static final java.util.Set<Direction> UP_ONLY = java.util.Set.of(Direction.UP);

        private SetView() {}
    }
}
