package com.kltyton.autoseamblend.runtime.surface;

import com.mojang.blaze3d.platform.NativeImage;
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
import com.kltyton.autoseamblend.texture.SpriteTransparency;
import com.kltyton.autoseamblend.texture.io.NativeArgb;
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
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.model.BakedQuad;
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
            Map<BlockState, BakedModel> models,
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

    /**
     * 中文：对单个 state+model 构建与已发布表面一致的候选 StateSurface：统一收集 6 个 cull
     * 方向桶 + null bucket，按 quad.getDirection()（1.21.1 几何方向）分组，fullyTransparent
     * 复用 isTransparent 全像素语义；供引擎适配器在 surfaces 尚未发布时（如 Fabric 首次
     * bake）使用。facts/overlayProfile/frameProfile 为不参与候选选择的占位；纯数据驱动，
     * 不依赖方块名或精灵白名单。
     *
     * <p>English: Builds a candidate StateSurface identical to published surfaces for a single
     * state/model: collects all six cull direction buckets plus the null bucket, grouped by
     * quad.getDirection() (the 1.21.1 geometry direction), with fullyTransparent reusing the
     * all-pixels isTransparent semantics; used by engine adapters before surfaces are
     * available (e.g. the Fabric first bake). facts/overlayProfile/frameProfile are
     * placeholders that never participate in candidate selection; purely data-driven, never
     * block-name or sprite whitelists.
     */
    public static StateSurface surfaceFacesFromModel(
            BlockState state,
            BakedModel model) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(model, "model");
        EnumMap<Direction, LinkedHashMap<TextureAtlasSprite, FaceDraft>> drafts =
                new EnumMap<>(Direction.class);
        RandomSource random = RandomSource.create(0L);
        for (Direction cullFace : Direction.values()) {
            collect(
                    model.getQuads(
                            state,
                            cullFace,
                            random),
                    drafts,
                    new ArrayList<>());
        }
        collect(
                model.getQuads(
                        state,
                        null,
                        random),
                drafts,
                new ArrayList<>());
        EnumMap<Direction, List<FaceSurface>> faces =
                new EnumMap<>(Direction.class);
        drafts.forEach((direction, bySprite) -> {
            ArrayList<FaceSurface> resolved =
                    new ArrayList<>();
            bySprite.values().forEach(draft -> {
                SpriteContents contents =
                        draft.sprite().contents();
                int frame = contents.getUniqueFrames()
                        .findFirst()
                        .orElse(0);
                resolved.add(new FaceSurface(
                        direction,
                        draft.sprite(),
                        draft.tintIndex(),
                        draft.fullFace(),
                        fullyTransparent(
                                contents,
                                frame,
                                contents.width(),
                                contents.height()),
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

    private static Optional<StateSurface> inspect(
            BlockState state,
            BakedModel model,
            IdentityHashMap<TextureAtlasSprite, TextureFacts> textureFacts,
            PreparedSurfaceMethods.Snapshot preparedMethods,
            List<String> diagnostics) {
        EnumMap<Direction, LinkedHashMap<TextureAtlasSprite, FaceDraft>> drafts =
                new EnumMap<>(Direction.class);
        ArrayList<BakedQuad> allQuads = new ArrayList<>();
        RandomSource random = RandomSource.create(0L);
        try {
            for (Direction cullFace : Direction.values()) {
                collect(
                        model.getQuads(state, cullFace, random),
                        drafts,
                        allQuads);
            }
            collect(
                    model.getQuads(state, null, random),
                    drafts,
                    allQuads);
        } catch (RuntimeException exception) {
            diagnostics.add("MODEL_QUADS_REJECTED:" + state + ':' + exception.getClass().getSimpleName());
            return Optional.empty();
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
                .map(BakedQuad::getDirection)
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
            TextureAtlasSprite sprite = quad.getSprite();
            drafts.computeIfAbsent(quad.getDirection(), ignored -> new LinkedHashMap<>())
                    .merge(
                            sprite,
                            new FaceDraft(
                                    quad.getDirection(),
                                    sprite,
                                    quad.getTintIndex(),
                                    fullFace(quad),
                                    quad),
                            FaceDraft::merge);
        }
    }

    private static TextureFacts inspectTexture(TextureAtlasSprite sprite) {
        SpriteContents contents = sprite.contents();
        SpriteTransparency transparency = SpriteTransparency.of(sprite);
        int width = contents.width();
        int height = contents.height();
        int[] uniqueFrames = contents.getUniqueFrames().toArray();
        int frame = uniqueFrames.length == 0
                ? 0
                : uniqueFrames[0];
        boolean animated = uniqueFrames.length > 1;
        OverlayCutoutProfile overlayProfile =
                overlayProfile(contents, frame);
        if (transparency.isOpaque()) {
            return new TextureFacts(
                    transparency,
                    animated,
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
                    animated,
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
                animated,
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
                        NativeArgb.toIr(
                                image.getPixelRGBA(
                                        frameX + x,
                                        frameY + y));
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
            return SurfaceFace.valueOf(quad.getDirection().name());
        }

        @Override
        public int vertexCount() {
            return 4;
        }

        @Override
        public float position(int vertex, int component) {
            return Float.intBitsToFloat(
                    quad.getVertices()[vertex * 8 + component]);
        }

        @Override
        public float u(int vertex) {
            return Float.intBitsToFloat(
                    quad.getVertices()[vertex * 8 + 4]);
        }

        @Override
        public float v(int vertex) {
            return Float.intBitsToFloat(
                    quad.getVertices()[vertex * 8 + 5]);
        }

        @Override
        public float spriteMinU() {
            return quad.getSprite().getU0();
        }

        @Override
        public float spriteMaxU() {
            return quad.getSprite().getU1();
        }

        @Override
        public float spriteMinV() {
            return quad.getSprite().getV0();
        }

        @Override
        public float spriteMaxV() {
            return quad.getSprite().getV1();
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

        /**
         * 中文：1.21.1 quad 的 lightFace 可能与烘焙面（nominalFace）不同（如草方块顶面被
         * 渲染为 lightFace=down、nominalFace=up）；lightFace 未命中时按 nominalFace 回退，
         * 与 26.1.2 使用烘焙面方向（direction()）的语义一致。仅 1.21.1 工程。
         *
         * English: In 1.21.1 a quad's lightFace can differ from its baked nominalFace (e.g.
         * grass_block_top with lightFace=down, nominalFace=up); when the lightFace lookup
         * misses, fall back to nominalFace to match 26.1.2's baked-face semantics.
         */
        public Optional<FaceSurface> face(
                BlockState state,
                Direction lightFace,
                Direction nominalFace,
                TextureAtlasSprite sprite) {
            Optional<FaceSurface> resolved =
                    face(state, lightFace, sprite);
            if (resolved.isEmpty()
                    && nominalFace != null
                    && nominalFace != lightFace) {
                resolved = face(state, nominalFace, sprite);
            }
            if (resolved.isEmpty() && states.containsKey(state)) {
                resolved = faceAcrossStates(
                        state,
                        lightFace,
                        nominalFace,
                        sprite);
            }
            return resolved;
        }

        /**
         * 中文：1.21.1 Fabric 的 multipart 模型在 OVERRIDE 阶段捕获时可能与运行时最终模型
         * 不一致（例如孤立玻璃板状态的目录缺少 pane_top 顶盖面，而运行时仍发射该 quad）；
         * 本回退在同方块、同方向、同精灵名下跨状态查找表面，纯数据驱动，不依赖方块名。
         *
         * English: On 1.21.1 Fabric the OVERRIDE-phase multipart model capture can disagree
         * with the final runtime model (e.g. the isolated pane state's catalog misses the
         * pane_top cap while the runtime still emits that quad); this fallback searches other
         * states of the same block for the same direction and sprite name, purely data-driven
         * and never name-based.
         */
        private Optional<FaceSurface> faceAcrossStates(
                BlockState state,
                Direction lightFace,
                Direction nominalFace,
                TextureAtlasSprite sprite) {
            Block block = state.getBlock();
            for (BlockState candidateState
                    : block.getStateDefinition().getPossibleStates()) {
                Optional<FaceSurface> found =
                        face(candidateState, lightFace, sprite);
                if (found.isEmpty()
                        && nominalFace != null
                        && nominalFace != lightFace) {
                    found = face(
                            candidateState,
                            nominalFace,
                            sprite);
                }
                if (found.isPresent()) {
                    return found;
                }
            }
            return Optional.empty();
        }

        public Optional<FaceSurface> preferredFace(BlockState state, Direction direction) {
            StateSurface surface = states.get(state);
            return surface == null
                    ? Optional.empty()
                    : surface.preferredFace(direction);
        }

        /**
         * 中文：overlay 供体专用面选择：优先 tinted 层（tintIndex>=0），再 opaque，再精灵名。
         * 通用 preferredFace 保持 opaque-first 不变（工作台/导出/推断仍用旧语义）。
         *
         * English: Overlay-donor-specific face selection: prefers the tinted layer
         * (tintIndex>=0), then opaque, then sprite name. The generic preferredFace stays
         * opaque-first for the workbench/export/inference consumers.
         */
        public Optional<FaceSurface> overlayDonorFace(
                BlockState state,
                Direction direction) {
            StateSurface surface = states.get(state);
            return surface == null
                    ? Optional.empty()
                    : surface.overlayDonorFace(direction);
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
            List<FaceSurface> candidates =
                    faces.getOrDefault(direction, List.of());
            Optional<FaceSurface> exact =
                    candidates.stream()
                    .filter(face -> face.sprite() == sprite)
                    .findFirst();
            if (exact.isPresent()) {
                return exact;
            }
            // 中文：1.21.1 的 Forgified/Indigo 渲染管线可能对同一名称的精灵持有不同实例
            // （重建 quad 时），按精灵名回退匹配，保证与 26.1.2 相同的表面命中；本回退
            // 只存在于 1.21.1 工程，不改 26.1.2 行为。
            // English: The 1.21.1 Forgified/Indigo pipeline can hold different instances for
            // same-named sprites (rebuilt quads); fall back to name matching so surface hits
            // match 26.1.2. This fallback exists only in the 1.21.1 project.
            return candidates.stream()
                    .filter(face -> face.sprite()
                            .contents().name()
                            .equals(sprite.contents().name()))
                    .findFirst();
        }

        public Optional<FaceSurface> preferredFace(Direction direction) {
            List<FaceSurface> values = faces.getOrDefault(direction, List.of());
            return values.isEmpty() ? Optional.empty() : Optional.of(values.getFirst());
        }

        /**
         * 中文：overlay 供体专用选择：tinted 层优先于 opaque 基底；单层与无 tint 面与
         * preferredFace 结果一致。
         *
         * English: Overlay-donor-specific selection: the tinted layer wins over the opaque
         * base; single-layer and untinted faces match preferredFace.
         */
        public Optional<FaceSurface> overlayDonorFace(
                Direction direction) {
            List<FaceSurface> values =
                    faces.getOrDefault(direction, List.of());
            if (values.isEmpty()) {
                return Optional.empty();
            }
            if (values.size() == 1) {
                return Optional.of(values.getFirst());
            }
            ArrayList<FaceSurface> sorted =
                    new ArrayList<>(values);
            sorted.sort(OVERLAY_DONOR_ORDER);
            return Optional.of(sorted.getFirst());
        }

        private static final Comparator<FaceSurface>
                OVERLAY_DONOR_ORDER =
                        (left, right) -> {
                            // 中文：tinted overlay 层必须优先于 opaque 基底（草侧
                            // grass_block_side_overlay 大部分像素透明，opaque-first 会选中
                            // 无 tint 的 dirt 基底）。
                            // English: The tinted overlay layer must outrank the opaque base
                            // (grass_block_side_overlay is mostly transparent, so opaque-first
                            // selected the untinted dirt base).
                            int tint = Boolean.compare(
                                    right.tintIndex() >= 0,
                                    left.tintIndex() >= 0);
                            if (tint != 0) {
                                return tint;
                            }
                            int opaque = Boolean.compare(
                                    right.facts()
                                            .alphaOpaque()
                                            .isTrue(),
                                    left.facts()
                                            .alphaOpaque()
                                            .isTrue());
                            if (opaque != 0) {
                                return opaque;
                            }
                            return left.sprite()
                                    .contents()
                                    .name()
                                    .toString()
                                    .compareTo(
                                            right.sprite()
                                                    .contents()
                                                    .name()
                                                    .toString());
                        };
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
            SpriteTransparency transparency,
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
