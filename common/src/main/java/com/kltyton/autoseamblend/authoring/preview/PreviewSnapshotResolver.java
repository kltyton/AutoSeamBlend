package com.kltyton.autoseamblend.authoring.preview;

import com.kltyton.autoseamblend.engine.routing.query.EngineRouteSource;
import com.kltyton.autoseamblend.runtime.render.ProceduralConnectionPlan;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.runtime.surface.MinecraftTopSurfaceResolver;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/** 中文：组合 provider 查询、样本与最终面计划的公共纯决策。 / English: Common decision flow composing provider queries, samples, and final face plans. */
public final class PreviewSnapshotResolver {
    private PreviewSnapshotResolver() {}

    public static Optional<NativePreviewSnapshot> resolve(
            PreviewSnapshotRequest request,
            TintResolver tintResolver) {
        PreviewSnapshotRequest checked = Objects.requireNonNull(request, "request");
        TintResolver checkedTint = Objects.requireNonNull(tintResolver, "tintResolver");
        PreviewProvider provider = PreviewProviderRegistry.find(
                        checked.selection().engineId())
                .orElse(null);
        if (provider == null) {
            return Optional.empty();
        }

        EngineRouteSource routeSource = checked.selection()
                .route()
                .provenance()
                .source();
        Optional<ConnectionMethod> effectiveOverride =
                routeSource == EngineRouteSource.NATIVE_AUTHOR
                        ? Optional.empty()
                        : checked.requestedOverride();
        ConnectionMethod requested = effectiveOverride.orElseGet(() ->
                checked.selection()
                        .resolution()
                        .map(value -> value.method().requestedMethod())
                        .orElseGet(checked.selection()::method));
        ConnectionMethod resolved = requested == ConnectionMethod.AUTO
                ? checked.selection().method()
                : requested;
        PreviewQuery query = new PreviewQuery(
                checked.level(),
                checked.pos(),
                checked.state(),
                checked.face(),
                checked.surface(),
                checked.rules(),
                checked.surfaces(),
                requested,
                resolved,
                checked.connectionBlocks(),
                checked.connectionPlaceholder());
        List<PreviewSample> sampled = provider.sample(query);
        if (sampled.isEmpty()) {
            return Optional.empty();
        }

        NeighborConnections connections = NeighborConnections.fromBits(
                sampled.stream()
                        .mapToInt(value -> value.connections().bits())
                        .reduce(0, (left, right) -> left | right));
        String sourceTextureId = sampled.stream()
                .map(value -> value.sourceSurface()
                        .sprite()
                        .contents()
                        .name()
                        .toString())
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        values -> String.join(", ", values)));
        TextureAtlasSprite sourceSprite = firstSourceSprite(sampled);
        ProceduralConnectionPlan plan;
        AdaptedComposition composition = null;
        if (resolved == ConnectionMethod.TOP) {
            Optional<TextureAtlasSprite> topSprite = MinecraftTopSurfaceResolver.resolve(
                    checked.level(),
                    checked.pos(),
                    checked.state(),
                    checked.face(),
                    checked.rules().rules(),
                    checked.surfaces());
            if (topSprite.isPresent()) {
                sourceSprite = topSprite.orElseThrow();
                sourceTextureId = sourceSprite.contents().name().toString();
                plan = ProceduralConnectionPlan.sourceReplacement();
            } else {
                plan = new ProceduralConnectionPlan(
                        ProceduralConnectionPlan.Mode.PASSTHROUGH,
                        List.of());
            }
        } else {
            composition = previewComposition(
                    checked.level(),
                    checked.pos(),
                    checked.surface(),
                    checkedTint.color(
                            checked.level(),
                            checked.pos(),
                            checked.state(),
                            checked.surface()),
                    resolved,
                    sampled,
                    checkedTint);
            plan = composition.plan().connectionPlan();
        }

        Optional<PreviewFaceResult> exactFace = provider.exactFace(query, sampled);
        int sourceTint = checkedTint.color(
                checked.level(),
                checked.pos(),
                sampled.getFirst().sourceState(),
                sampled.getFirst().sourceSurface());
        PreviewFaceResult faceResult;
        if (exactFace.isPresent()) {
            faceResult = exactFace.orElseThrow();
        } else if (composition != null) {
            faceResult = faceResult(composition);
        } else {
            faceResult = PreviewFaceResult.fromPlan(sourceSprite, plan, sourceTint);
        }
        String blockId = BuiltInRegistries.BLOCK
                .getKey(checked.state().getBlock())
                .toString();
        return Optional.of(new NativePreviewSnapshot(
                checked.rules().generation(),
                checked.surfaces().generation(),
                provider.engineId(),
                routeSource,
                blockId,
                checked.donorState().map(value -> BuiltInRegistries.BLOCK
                        .getKey(value.getBlock())
                        .toString()),
                checked.pos(),
                checked.donorPosition(),
                checked.connectionPlaceholder(),
                sourceTextureId,
                sourceSprite,
                checked.face(),
                requested,
                resolved,
                connections,
                plan,
                faceResult));
    }

    private static AdaptedComposition previewComposition(
            BlockAndTintGetter level,
            BlockPos pos,
            FaceSurface receiver,
            int receiverTint,
            ConnectionMethod faceMethod,
            List<PreviewSample> samples,
            TintResolver tintResolver) {
        String receiverKey = "receiver";
        LinkedHashMap<String, TextureAtlasSprite> sprites = new LinkedHashMap<>();
        sprites.put(receiverKey, receiver.sprite());
        ArrayList<PreviewCompositionSample> neutralSamples = new ArrayList<>();
        for (int index = 0; index < samples.size(); index++) {
            PreviewSample sample = samples.get(index);
            String sourceKey = "sample:" + index;
            sprites.put(sourceKey, sample.sourceSurface().sprite());
            neutralSamples.add(new PreviewCompositionSample(
                    sourceKey,
                    sample.connections(),
                    sample.renderMethod(),
                    sample.sourceSurface().frameProfile(),
                    sample.sourceSurface().overlayProfile(),
                    tintResolver.color(
                            level,
                            pos,
                            sample.sourceState(),
                            sample.sourceSurface())));
        }
        return new AdaptedComposition(
                PreviewCompositionComposer.compose(
                        receiverKey,
                        receiverTint,
                        faceMethod,
                        neutralSamples),
                sprites);
    }

    private static PreviewFaceResult faceResult(AdaptedComposition composition) {
        ArrayList<PreviewFaceResult.Layer> layers = new ArrayList<>();
        for (PreviewFaceLayer layer : composition.plan().layers()) {
            TextureAtlasSprite sprite = Objects.requireNonNull(
                    composition.sprites().get(layer.sourceKey()),
                    "preview composition sprite");
            layers.add(new PreviewFaceResult.Layer(
                    sprite,
                    layer.x0(),
                    layer.y0(),
                    layer.x1(),
                    layer.y1(),
                    layer.u0(),
                    layer.v0(),
                    layer.u1(),
                    layer.v1(),
                    layer.tint()));
        }
        return new PreviewFaceResult(layers);
    }

    private static TextureAtlasSprite firstSourceSprite(List<PreviewSample> samples) {
        return samples.getFirst().sourceSurface().sprite();
    }

    @FunctionalInterface
    public interface TintResolver {
        int color(
                BlockAndTintGetter level,
                BlockPos pos,
                BlockState state,
                FaceSurface surface);
    }

    private record AdaptedComposition(
            PreviewCompositionPlan plan,
            Map<String, TextureAtlasSprite> sprites) {
        private AdaptedComposition {
            Objects.requireNonNull(plan, "plan");
            sprites = Map.copyOf(Objects.requireNonNull(sprites, "sprites"));
        }
    }
}
