package com.kltyton.autoseamblend.compat.athena.runtime;

import com.kltyton.autoseamblend.engine.query.AcceptedNativeDocument;
import com.kltyton.autoseamblend.engine.query.NativeDocumentIdentity;
import com.kltyton.autoseamblend.engine.query.NativeQueryObservation;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * 中文：Athena 原生模型所有权的 Loader 无关裁决策略。只做三类纯决策：缺失纹理判定、
 * 候选精灵同名所有权、以及 owns + 文档身份到观察结果的裁决；不接触 Athena 模型、
 * WrappedGetter 或纹理槽提取，那些仍属于各 Loader 适配边界。
 *
 * <p>English: Loader-neutral adjudication policy for Athena native-model ownership. It only
 * makes three pure decisions: missing-sprite detection, same-name candidate-sprite ownership,
 * and the owns-plus-document-identity to observation verdict; it never touches the Athena
 * model, WrappedGetter, or texture-slot extraction, which remain in each Loader's adapter
 * boundary.
 */
public final class AthenaNativeOwnershipPolicy {
    private AthenaNativeOwnershipPolicy() {}

    /**
     * 中文：缺失纹理判定；与 26.1.2 逐字一致。
     *
     * <p>English: Missing-texture check; verbatim 26.1.2.
     */
    public static boolean missingSprite(
            TextureAtlasSprite sprite) {
        Objects.requireNonNull(sprite, "sprite");
        return sprite.contents()
                .name()
                .equals(MissingTextureAtlasSprite.getLocation());
    }

    /**
     * 中文：26.1.2 面/精灵所有权：任一候选（非缺失）与渲染精灵同名即命中。
     *
     * <p>English: 26.1.2 face/sprite ownership: any non-missing candidate sharing the
     * rendered sprite's name claims ownership.
     */
    public static boolean ownsByCandidateSprites(
            List<TextureAtlasSprite> candidates,
            TextureAtlasSprite rendered) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(rendered, "rendered");
        for (TextureAtlasSprite candidate : candidates) {
            if (candidate != null
                    && !missingSprite(candidate)
                    && candidate.contents()
                            .name()
                            .equals(rendered.contents()
                                    .name())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 中文：观察结果裁决：未命中→noMatch；命中且有文档身份→精确文档；命中但身份
     * 无法可靠解析→明确 unknown（绝不伪造 exact(empty)，原生构造会抛异常）。
     *
     * <p>English: Observation verdict: no match → noMatch; owned with a document identity
     * → exact document; owned but identity unresolvable → explicit unknown (never a forged
     * exact(empty), which the native constructor rejects).
     */
    public static NativeQueryObservation resolveObservation(
            boolean owns,
            Optional<NativeDocumentIdentity> identity) {
        Objects.requireNonNull(identity, "identity");
        if (!owns) {
            return NativeQueryObservation.noMatch();
        }
        return identity
                .map(value -> NativeQueryObservation.exact(
                        List.of(
                                AcceptedNativeDocument
                                        .identityOnly(value))))
                .orElseGet(() -> NativeQueryObservation.unknown(
                        "ATHENA_ACCEPTED_MODEL_DOCUMENT_IDENTITY_UNAVAILABLE"));
    }
}
