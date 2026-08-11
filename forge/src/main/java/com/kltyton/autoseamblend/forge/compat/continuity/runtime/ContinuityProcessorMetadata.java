package com.kltyton.autoseamblend.forge.compat.continuity.runtime;

import com.kltyton.autoseamblend.authoring.format.mcpatcher.MCPatcherAuthorExtension;
import com.kltyton.autoseamblend.authoring.format.mcpatcher.MCPatcherMethodCodec;
import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import com.kltyton.autoseamblend.engine.ownership.NativeSlotIntent;
import com.kltyton.autoseamblend.engine.query.AcceptedNativeDocument;
import com.kltyton.autoseamblend.engine.query.NativeDocumentIdentity;
import com.kltyton.autoseamblend.compat.continuity.document.ContinuityPropertiesExtensionCarrier;
import com.kltyton.autoseamblend.compat.continuity.runtime.ContinuityAcceptedHolderEvidence;
import com.kltyton.autoseamblend.compat.continuity.runtime.ContinuityOverlaySlotIntent;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import me.pepperbell.continuity.api.client.QuadProcessor;
import me.pepperbell.continuity.client.processor.overlay.SimpleOverlayQuadProcessor;
import me.pepperbell.continuity.client.processor.overlay.StandardOverlayQuadProcessor;
import me.pepperbell.continuity.client.properties.BaseCtmProperties;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/** 中文：组装真实原生 holder 列表时使用的不可变已接受 holder 来源。 / English: Immutable accepted-holder provenance consumed when the real native holder list is assembled. */
public record ContinuityProcessorMetadata(
        ContinuityAcceptedHolderEvidence acceptedEvidence,
        boolean nativeSpritesPresent,
        boolean presentResourceFailedAtlas,
        boolean additiveOverlay,
        Optional<NativeOverlaySelector> overlaySelection,
        List<ContinuityOverlaySlotIntent> overlaySlotIntents) {
    private static final Map<QuadProcessor, ContinuityProcessorMetadata>
            PENDING = Collections.synchronizedMap(
                    new IdentityHashMap<>());

    public ContinuityProcessorMetadata {
        acceptedEvidence = Objects.requireNonNull(acceptedEvidence, "acceptedEvidence");
        overlaySelection = Objects.requireNonNull(
                overlaySelection,
                "overlaySelection");
        overlaySlotIntents = List.copyOf(Objects.requireNonNull(
                overlaySlotIntents,
                "overlaySlotIntents"));
        if (resolvedMethod() == ConnectionMethod.AUTO) {
            throw new IllegalArgumentException(
                    "accepted holder method must be concrete");
        }
        if (overlaySelection.isPresent()
                && overlaySlotIntents.size() != 17) {
            throw new IllegalArgumentException(
                    "standard overlay metadata must describe 17 slots");
        }
        if (!additiveOverlay
                && (!overlaySlotIntents.isEmpty()
                        || overlaySelection.isPresent())) {
            throw new IllegalArgumentException(
                    "replacement processors cannot carry overlay slot metadata");
        }
    }

    public com.kltyton.autoseamblend.engine.ownership.SourceTier sourceTier() {
        return acceptedEvidence.sourceTier();
    }

    public Optional<com.kltyton.autoseamblend.engine.ownership.AutoBlendPolicy> strategyPolicy() {
        return acceptedEvidence.strategyPolicy();
    }

    public Optional<NativeDocumentIdentity> documentIdentity() {
        return acceptedEvidence.queryIdentity();
    }

    public int packPriority() {
        return acceptedEvidence.packPriority();
    }

    public ConnectionMethod requestedMethod() {
        return acceptedEvidence.requestedMethod().orElse(ConnectionMethod.NONE);
    }

    public ConnectionMethod resolvedMethod() {
        return acceptedEvidence.resolvedMethod().orElse(ConnectionMethod.NONE);
    }

    public List<NativeSlot> nativeSlots() {
        return acceptedEvidence.nativeSlots();
    }

    public static void register(
            BaseCtmProperties properties,
            QuadProcessor processor,
            Function<ResourceLocation, TextureAtlasSprite> spriteGetter) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(processor, "processor");
        Objects.requireNonNull(spriteGetter, "spriteGetter");
        Optional<MCPatcherAuthorExtension> extension =
                ((ContinuityPropertiesExtensionCarrier) properties)
                        .autoseamblend$authorExtension();
        List<NativeSlot> nativeSlots =
                ((ContinuityPropertiesExtensionCarrier) properties)
                        .autoseamblend$nativeSlots();
        Optional<ConnectionMethod> nativeMethod = Optional.of(
                MCPatcherMethodCodec
                        .parsePublic(properties.getMethod())
                        .filter(value -> value != ConnectionMethod.AUTO)
                        .orElse(ConnectionMethod.NONE));
        ContinuityAcceptedHolderEvidence acceptedEvidence =
                ContinuityAcceptedHolderEvidence.from(
                        extension,
                        properties.getPackId(),
                        properties.getResourceId().toString(),
                        0,
                        nativeMethod,
                        nativeSlots,
                        Optional.empty());
        boolean additive = processor instanceof StandardOverlayQuadProcessor
                || processor instanceof SimpleOverlayQuadProcessor;
        OverlayMetadata overlay = overlayMetadata(
                properties,
                processor,
                spriteGetter,
                nativeSlots);
        ContinuityProcessorMetadata metadata =
                new ContinuityProcessorMetadata(
                        acceptedEvidence,
                        acceptedEvidence.nativeSpritesPresent(),
                        presentResourceFailedAtlas(
                                nativeSlots,
                                spriteGetter),
                        additive,
                        overlay.selection(),
                        overlay.intents());
        ContinuityProcessorMetadata previous =
                PENDING.put(processor, metadata);
        if (previous != null) {
            throw new IllegalStateException(
                    "processor received duplicate metadata");
        }
    }

    public static ContinuityProcessorMetadata take(
            QuadProcessor processor) {
        ContinuityProcessorMetadata metadata =
                PENDING.remove(Objects.requireNonNull(
                        processor,
                        "processor"));
        if (metadata != null) {
            return metadata;
        }
        return new ContinuityProcessorMetadata(
                new ContinuityAcceptedHolderEvidence(
                        com.kltyton.autoseamblend.engine.ownership.SourceTier.NATIVE_AUTHOR,
                        Optional.empty(),
                        "continuity",
                        "unknown",
                        0,
                        Optional.of(ConnectionMethod.NONE),
                        Optional.of(ConnectionMethod.NONE),
                        List.of(),
                        Optional.empty(),
                        Optional.empty()),
                true,
                false,
                false,
                Optional.empty(),
                List.of());
    }

    /**
     * 中文：把已接受 holder 的身份与证据作为一个不可变查询文档交给中立路由边界。
     *
     * English:
     * Exposes an accepted holder's identity and evidence to the neutral routing boundary as one
     * immutable query document.
     */
    public Optional<AcceptedNativeDocument> acceptedDocument(
            int documentOrder) {
        return acceptedEvidence.acceptedQueryDocument(documentOrder);
    }

    private static boolean presentResourceFailedAtlas(
            List<NativeSlot> nativeSlots,
            Function<ResourceLocation, TextureAtlasSprite>
                    spriteGetter) {
        for (NativeSlot slot : nativeSlots) {
            if (slot.intent()
                    != NativeSlotIntent.PRESENT) {
                continue;
            }
            ResourceLocation id = slot.spriteId()
                    .map(ResourceLocation::tryParse)
                    .orElse(null);
            TextureAtlasSprite sprite = id == null
                    ? null
                    : spriteGetter.apply(id);
            if (sprite == null
                    || QuadConversions.isMissingSprite(
                            sprite)) {
                return true;
            }
        }
        return false;
    }

    private static OverlayMetadata overlayMetadata(
            BaseCtmProperties properties,
            QuadProcessor processor,
            Function<ResourceLocation, TextureAtlasSprite> spriteGetter,
            List<NativeSlot> nativeSlots) {
        if (!(processor instanceof StandardOverlayQuadProcessor standard)) {
            return new OverlayMetadata(Optional.empty(), List.of());
        }
        List<net.minecraft.client.resources.model.Material> spriteIds =
                properties.getSpriteIds();
        if (spriteIds.size() != 17
                || nativeSlots.size() != spriteIds.size()) {
            return new OverlayMetadata(Optional.empty(), List.of());
        }
        java.util.ArrayList<ContinuityOverlaySlotIntent> intents =
                new java.util.ArrayList<>(spriteIds.size());
        TextureAtlasSprite markerSource = null;
        for (int index = 0;
                index < spriteIds.size();
                index++) {
            ResourceLocation spriteId =
                    spriteIds.get(index).texture();
            ContinuityOverlaySlotIntent intent =
                    ContinuityOverlaySlotIntent.from(
                            nativeSlots.get(index).intent());
            if (intent == ContinuityOverlaySlotIntent.PRESENT) {
                TextureAtlasSprite sprite =
                        spriteGetter.apply(spriteId);
                if (markerSource == null
                        && sprite != null
                        && !QuadConversions.isMissingSprite(
                                sprite)) {
                    markerSource = sprite;
                }
            }
            intents.add(intent);
        }
        Optional<NativeOverlaySelector> selection =
                markerSource == null
                        ? Optional.empty()
                        : Optional.of(NativeOverlaySelector.copyOf(
                                standard,
                                markerSource));
        return new OverlayMetadata(selection, intents);
    }

    private record OverlayMetadata(
            Optional<NativeOverlaySelector> selection,
            List<ContinuityOverlaySlotIntent> intents) {}
}
