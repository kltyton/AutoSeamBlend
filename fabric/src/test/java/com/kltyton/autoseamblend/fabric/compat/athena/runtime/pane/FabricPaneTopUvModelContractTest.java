package com.kltyton.autoseamblend.fabric.compat.athena.runtime.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：RED 契约——26.1.2 Fabric 顶臂 UV 修正必须等于已验收 26.1.2 NeoForge
 * PaneTopUvModel 与 1.21.1 ce33d6c FabricPaneTopUvModel 语义：竖直面、精灵为 edge、
 * 且 maxX-minX &gt; maxZ-minZ 时按 u'=getU(localV)、v'=getV(localU) 交换 UV；南北向窄臂、
 * 非 edge 精灵保持不变。当前 26.1.2 Fabric 没有等价路径，测试先红。
 *
 * <p>English: RED contract -- the 26.1.2 Fabric top-arm UV correction must equal the accepted
 * 26.1.2 NeoForge PaneTopUvModel and 1.21.1 ce33d6c FabricPaneTopUvModel semantics: a
 * vertical-face quad bound to the edge sprite with maxX-minX &gt; maxZ-minZ swaps UVs via
 * u'=getU(localV) and v'=getV(localU); narrow north/south arms and foreign sprites stay
 * unchanged. The 26.1.2 Fabric side has no equivalent yet, so the test fails first.
 */
class FabricPaneTopUvModelContractTest {

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.setVersion(
                DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @Test
    void eastWestTopArmRemapsUvVertically() {
        TextureAtlasSprite edge = PaneTestSprites.EDGE;
        BakedQuad source = PaneTestQuads.wideTop(
                edge,
                0.3F,
                0.7F);

        BakedQuad corrected =
                FabricPaneTopUvModel.correct(
                        source,
                        edge);

        // 中文：归一化精灵下 localU=u、localV=v，修正后 u'=v、v'=u，与 NeoForge 测试一致。
        // English: With a normalized sprite localU=u and localV=v, the corrected
        // UVs are u'=v and v'=u, matching the NeoForge test.
        assertEquals(
                0.7F,
                u(corrected),
                1.0e-4F,
                "corrected U must equal the source V");
        assertEquals(
                0.3F,
                v(corrected),
                1.0e-4F,
                "corrected V must equal the source U");
    }

    @Test
    void northSouthArmStaysUnchanged() {
        TextureAtlasSprite edge = PaneTestSprites.EDGE;
        BakedQuad source = PaneTestQuads.narrowTop(
                edge,
                0.3F,
                0.7F);

        BakedQuad result =
                FabricPaneTopUvModel.correct(
                        source,
                        edge);

        assertSame(
                source,
                result,
                "a north/south top arm (maxX-minX <= maxZ-minZ) must stay unchanged");
    }

    @Test
    void foreignSpriteStaysUnchanged() {
        TextureAtlasSprite edge = PaneTestSprites.EDGE;
        TextureAtlasSprite body = PaneTestSprites.BODY;
        BakedQuad source = PaneTestQuads.wideTop(
                body,
                0.3F,
                0.7F);

        BakedQuad result =
                FabricPaneTopUvModel.correct(
                        source,
                        edge);

        assertSame(
                source,
                result,
                "a quad bound to a non-edge sprite must stay unchanged");
    }

    @Test
    void pureUvSwapMatchesNeoForgeFormula() {
        TextureAtlasSprite edge = PaneTestSprites.EDGE;

        FabricPaneTopUvModel.UvSwap swapped =
                FabricPaneTopUvModel.swap(
                        0.3F,
                        0.7F,
                        edge);

        assertEquals(
                0.7F,
                swapped.u(),
                1.0e-4F,
                "swapped U must equal the source V");
        assertEquals(
                0.3F,
                swapped.v(),
                1.0e-4F,
                "swapped V must equal the source U");
    }

    private static float u(BakedQuad quad) {
        return net.minecraft.client.model.geom.builders.UVPair
                .unpackU(quad.packedUV(0));
    }

    private static float v(BakedQuad quad) {
        return net.minecraft.client.model.geom.builders.UVPair
                .unpackV(quad.packedUV(0));
    }
}
