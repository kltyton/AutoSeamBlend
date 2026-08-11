package com.kltyton.autoseamblend.compat.athena.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.platform.NativeImage;
import com.kltyton.autoseamblend.engine.query.AcceptedNativeDocument;
import com.kltyton.autoseamblend.engine.query.NativeDocumentIdentity;
import com.kltyton.autoseamblend.engine.query.NativeQueryObservation;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import org.junit.jupiter.api.Test;

/**
 * 中文：AthenaNativeOwnershipPolicy 的 Loader 无关所有权合同测试。锁定 26.1.2 语义：
 * missing 精灵拒绝；任一候选（非缺失）与渲染精灵同名即命中；predicate 命中时绝不
 * 发布 exact(empty)（该构造在 1.21.1 会抛 IllegalArgumentException），文档身份无法
 * 可靠解析时返回明确 unknown。纯构造测试精灵，不链接 Athena 模型或 Loader 类型。
 *
 * <p>English: Loader-neutral contract tests for AthenaNativeOwnershipPolicy. Locks the
 * 26.1.2 semantics: missing sprites are rejected; any non-missing candidate sharing the
 * rendered sprite's name claims ownership; a hit never publishes exact(empty) (that
 * construction throws IllegalArgumentException on 1.21.1), and an unresolvable document
 * identity yields an explicit unknown. Test sprites are constructed purely; no Athena
 * model or Loader types are linked.
 */
class AthenaNativeOwnershipPolicyContractTest {

    @Test
    void missingSpriteRejectsMissingTextureLocation() {
        assertTrue(
                AthenaNativeOwnershipPolicy.missingSprite(
                        missingSpriteInstance()),
                "missing atlas sprite must be rejected");
        assertFalse(
                AthenaNativeOwnershipPolicy.missingSprite(
                        sprite("minecraft:test_sprite")),
                "a real sprite must not be treated as missing");
    }

    @Test
    void ownsByCandidateSpritesMatchesAnyAcceptedCandidateSprite() {
        TextureAtlasSprite rendered =
                sprite("minecraft:rendered");
        assertTrue(
                AthenaNativeOwnershipPolicy.ownsByCandidateSprites(
                        List.of(
                                sprite("minecraft:other"),
                                rendered),
                        rendered));
        assertFalse(
                AthenaNativeOwnershipPolicy.ownsByCandidateSprites(
                        List.of(
                                sprite("minecraft:other")),
                        rendered));
        assertFalse(
                AthenaNativeOwnershipPolicy.ownsByCandidateSprites(
                        List.of(),
                        rendered));
    }

    @Test
    void ownershipNeverPublishesEmptyExactAndReturnsUnknownWhenIdentityUnavailable() {
        // 中文：exact(empty) 在 1.21.1 会抛异常；26.1.2 语义为 identity 缺失时返回明确
        // unknown 诊断，绝不伪造精确文档。
        // English: exact(empty) throws on 1.21.1; the 26.1.2 semantics return an explicit
        // unknown diagnostic when the identity is missing and never forge an exact document.
        NativeQueryObservation ownedUnknown =
                AthenaNativeOwnershipPolicy.resolveObservation(
                        true,
                        Optional.empty());
        assertTrue(
                ownedUnknown.acceptedDocuments().isEmpty());
        assertEquals(
                Optional.of(
                        "ATHENA_ACCEPTED_MODEL_DOCUMENT_IDENTITY_UNAVAILABLE"),
                ownedUnknown.unknownDiagnostic());

        NativeDocumentIdentity identity =
                NativeDocumentIdentity.resourceOnly(
                        "minecraft:athena/test.json");
        NativeQueryObservation ownedExact =
                AthenaNativeOwnershipPolicy.resolveObservation(
                        true,
                        Optional.of(identity));
        assertEquals(
                List.of(
                        AcceptedNativeDocument.identityOnly(identity)),
                ownedExact.acceptedDocuments());
        assertEquals(
                Optional.empty(),
                ownedExact.unknownDiagnostic());

        NativeQueryObservation notOwned =
                AthenaNativeOwnershipPolicy.resolveObservation(
                        false,
                        Optional.empty());
        assertTrue(
                notOwned.acceptedDocuments().isEmpty());
        assertEquals(
                Optional.empty(),
                notOwned.unknownDiagnostic());
        assertThrows(
                IllegalArgumentException.class,
                () -> NativeQueryObservation.exact(
                        List.of()));
    }

    private static TextureAtlasSprite missingSpriteInstance() {
        return new TestSprite(
                TextureAtlas.LOCATION_BLOCKS,
                MissingTextureAtlasSprite.create());
    }

    /** 中文：位于假定 2048x2048 Atlas 原点的 16x16 真实（非 missing）测试精灵。 / English: 16x16 real (non-missing) test sprite at the assumed 2048x2048 atlas origin. */
    private static TextureAtlasSprite sprite(
            String name) {
        NativeImage image =
                new NativeImage(16, 16, false);
        SpriteContents contents = new SpriteContents(
                new ResourceLocation(name),
                new FrameSize(16, 16),
                image,
                AnimationMetadataSection.EMPTY);
        return new TestSprite(
                TextureAtlas.LOCATION_BLOCKS,
                contents);
    }

    private static final class TestSprite
            extends TextureAtlasSprite {
        private TestSprite(
                ResourceLocation atlasLocation,
                SpriteContents contents) {
            super(
                    atlasLocation,
                    contents,
                    2048,
                    2048,
                    0,
                    0);
        }
    }
}
