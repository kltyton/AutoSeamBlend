package com.kltyton.autoseamblend.compat.fusion.runtime;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.query.AcceptedNativeDocument;
import com.kltyton.autoseamblend.engine.query.NativeDocumentIdentity;
import com.kltyton.autoseamblend.engine.query.NativeQueryObservation;
import com.supermartijn642.fusion.api.texture.DefaultTextureTypes;
import com.supermartijn642.fusion.api.texture.SpriteHelper;
import com.supermartijn642.fusion.api.texture.TextureType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：Fusion 公共原生所有权状态机；Loader 只提供模型表面探针，不能改变 unknown/no-match
 * 与 accepted 的判定语义。
 *
 * English: Shared Fusion native-ownership state machine; Loaders supply only model-surface probes
 * and cannot change the unknown/no-match/accepted decision semantics.
 */
public final class FusionNativeQueryOwnership {
    private final ConcurrentMap<Long, Map<BlockState, QueryProbe>> generations =
            new ConcurrentHashMap<>();
    private Map<BlockState, QueryProbe> capturing = new LinkedHashMap<>();
    private long capturingGeneration = -1;
    private FusionAcceptedModifierDocumentCatalog.Snapshot capturingDocuments =
            FusionAcceptedModifierDocumentCatalog.Snapshot.empty();

    public String engineId() {
        return "fusion";
    }

    public EngineFamily family() {
        return EngineFamily.FUSION;
    }

    public NativeQueryObservation observe(
            long generation,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            Direction face,
            TextureAtlasSprite sprite) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(sprite, "sprite");
        TextureType<?, ?> textureType =
                SpriteHelper.getTextureType(sprite);
        boolean connecting =
                textureType == DefaultTextureTypes.CONNECTING;
        // 中文：FIXED 在 Fusion 中由 base 纹理类型表示（FusionMethodMapping 路由为
        // fusion:base）；BASE 精灵只有在 probe/catalog 确实匹配时才走 exact/unknown 决策，
        // 未拥有或普通（VANILLA）精灵保持 noMatch，不扩大 model-level 语义。
        // English: FIXED is represented by Fusion's base texture type (FusionMethodMapping
        // routes it to fusion:base); a BASE sprite only enters the exact/unknown decision when
        // the probe and catalog really match, while unowned and ordinary (VANILLA) sprites
        // stay noMatch without broadening model-level semantics.
        boolean base =
                textureType == DefaultTextureTypes.BASE;
        if (!connecting && !base) {
            return NativeQueryObservation.noMatch();
        }
        Map<BlockState, QueryProbe> probes = generations.get(generation);
        if (probes == null) {
            return connecting
                    ? NativeQueryObservation.unknown(
                            "FUSION_MODEL_CAPTURE_GENERATION_UNAVAILABLE")
                    : NativeQueryObservation.noMatch();
        }
        QueryProbe probe = probes.get(state);
        if (probe == null || !probe.matches(level, pos, state, face, sprite)) {
            return connecting
                    ? NativeQueryObservation.unknown(
                            "FUSION_CONNECTING_SPRITE_DOCUMENT_IDENTITY_UNAVAILABLE")
                    : NativeQueryObservation.noMatch();
        }
        if (probe.documents().isEmpty()) {
            return connecting
                    ? NativeQueryObservation.unknown(
                            "FUSION_CONNECTING_SPRITE_DOCUMENT_IDENTITY_UNAVAILABLE")
                    : NativeQueryObservation.noMatch();
        }
        return NativeQueryObservation.exact(probe.documents());
    }

    public synchronized void beginModelCapture(
            long generation,
            FusionAcceptedModifierDocumentCatalog.Snapshot documents) {
        if (generation <= 0) {
            throw new IllegalArgumentException("generation must be positive");
        }
        generations.keySet().removeIf(value -> value < generation - 1);
        capturing = new LinkedHashMap<>();
        capturingGeneration = generation;
        capturingDocuments = Objects.requireNonNull(documents, "documents");
    }

    public synchronized void captureModel(
            BlockState state,
            SurfaceProbe probe) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(probe, "probe");
        List<NativeDocumentIdentity> identities = capturingDocuments.documents(state);
        capturing.put(
                state,
                new QueryProbe(
                        probe,
                        identities.stream()
                                .map(AcceptedNativeDocument::identityOnly)
                                .toList()));
    }

    public synchronized void endModelCapture() {
        if (capturingGeneration < 0) {
            throw new IllegalStateException("Fusion ownership capture has not begun");
        }
        generations.put(capturingGeneration, Map.copyOf(capturing));
        capturing = new LinkedHashMap<>();
        capturingGeneration = -1;
        capturingDocuments = FusionAcceptedModifierDocumentCatalog.Snapshot.empty();
    }

    public synchronized void abortModelCapture(long generation) {
        generations.remove(generation);
        if (capturingGeneration == generation) {
            capturing = new LinkedHashMap<>();
            capturingGeneration = -1;
            capturingDocuments = FusionAcceptedModifierDocumentCatalog.Snapshot.empty();
        }
    }

    public synchronized void purgeUnselected() {
        generations.clear();
        capturing = new LinkedHashMap<>();
        capturingGeneration = -1;
        capturingDocuments = FusionAcceptedModifierDocumentCatalog.Snapshot.empty();
    }

    @FunctionalInterface
    public interface SurfaceProbe {
        boolean contains(
                BlockAndTintGetter level,
                BlockPos pos,
                BlockState state,
                Direction face,
                TextureAtlasSprite sprite);
    }

    private record QueryProbe(
            SurfaceProbe probe,
            List<AcceptedNativeDocument> documents) {
        private QueryProbe {
            Objects.requireNonNull(probe, "probe");
            documents = List.copyOf(Objects.requireNonNull(documents, "documents"));
        }

        private boolean matches(
                BlockAndTintGetter level,
                BlockPos pos,
                BlockState state,
                Direction face,
                TextureAtlasSprite sprite) {
            return probe.contains(level, pos, state, face, sprite);
        }
    }
}
