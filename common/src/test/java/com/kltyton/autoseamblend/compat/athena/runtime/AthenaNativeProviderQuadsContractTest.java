package com.kltyton.autoseamblend.compat.athena.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import earth.terrarium.athena.api.client.models.AthenaQuad;
import earth.terrarium.athena.api.client.utils.CtmState;
import java.util.List;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

/**
 * 中文：AthenaNativeProvider.quads 的 Loader 无关持久合同测试，锁定 Athena 4.0.6 官方
 * ConnectedBlockModel 字节码合同：allTrue 快路径返回单个 AthenaQuad.withSprite(1) 全面
 * （槽 1、bounds 0..1、depth 0、NONE）；其他状态返回四个 withState 象限（bounds 与
 * CtmUtils.getTexture 真值表槽位）。使用空 TextureAtlasSprite[5]（provider 不访问元素）。
 * 覆盖 Direction.values() × 代表 CtmState（全 false、全 true、边、角），并拒绝错误角色
 * 数与 null 参数。当前实现全状态返回四象限，allTrue 断言应失败。
 *
 * <p>English: Loader-neutral contract tests for AthenaNativeProvider.quads, locking the
 * Athena 4.0.6 ConnectedBlockModel bytecode contract: the allTrue fast path returns a single
 * AthenaQuad.withSprite(1) full face (slot 1, bounds 0..1, depth 0, NONE); other states
 * return the four withState quadrants (exact bounds and CtmUtils.getTexture truth-table
 * slots). An empty TextureAtlasSprite[5] is used because the provider never dereferences
 * elements. Covers Direction.values() x representative CtmStates (all-false, all-true,
 * edges, corners) and rejects wrong role counts and null arguments. The current
 * implementation returns four quadrants for every state, so the allTrue assertions fail.
 */
class AthenaNativeProviderQuadsContractTest {
    private static final TextureAtlasSprite[] EMPTY_SPRITES =
            new TextureAtlasSprite[5];

    private static final CtmState ALL_TRUE = new CtmState(
            true, true, true, true, true, true, true, true);
    private static final CtmState ALL_FALSE = new CtmState(
            false, false, false, false, false, false, false, false);
    private static final CtmState UP_ONLY = new CtmState(
            true, false, false, false, false, false, false, false);
    private static final CtmState LEFT_ONLY = new CtmState(
            false, false, true, false, false, false, false, false);
    private static final CtmState UP_LEFT = new CtmState(
            true, false, true, false, false, false, false, false);
    private static final CtmState UP_LEFT_UPLEFT = new CtmState(
            true, false, true, false, true, false, false, false);
    private static final CtmState EDGE = new CtmState(
            true, false, false, true, false, true, false, false);
    private static final CtmState CORNERS = new CtmState(
            false, true, true, false, false, false, true, false);
    private static final CtmState DOWN_RIGHT = new CtmState(
            false, true, false, true, false, false, false, true);

    @Test
    void allTrueFastPathEmitsSingleFullFaceQuad() {
        for (Direction face : Direction.values()) {
            List<AthenaQuad> quads = assertDoesNotThrow(
                    () -> AthenaNativeProvider.quads(
                            ALL_TRUE,
                            face,
                            EMPTY_SPRITES));
            // 中文：官方 ConnectedBlockModel 字节码 allTrue 分支返回
            // AthenaQuad.withSprite(1)（left=0,right=1,top=1,bottom=0,depth=0,NONE）。
            // English: The official ConnectedBlockModel bytecode allTrue branch returns a
            // single AthenaQuad.withSprite(1) (left=0,right=1,top=1,bottom=0,depth=0,NONE).
            assertEquals(
                    List.of(AthenaQuad.withSprite(1)),
                    quads,
                    "allTrue must emit exactly one full-face quad with slot 1");
            AthenaQuad quad = quads.get(0);
            assertEquals(1, quad.sprite());
            assertEquals(0.0F, quad.left(), 1.0e-6F);
            assertEquals(1.0F, quad.right(), 1.0e-6F);
            assertEquals(1.0F, quad.top(), 1.0e-6F);
            assertEquals(0.0F, quad.bottom(), 1.0e-6F);
            assertEquals(0.0F, quad.depth(), 1.0e-6F);
            assertEquals(Rotation.NONE, quad.rotation());
        }
    }

    @Test
    void representativePartialStatesEmitFourQuadrantQuadsWithExactBoundsAndSlots() {
        List<CtmState> states = List.of(
                ALL_FALSE,
                UP_ONLY,
                LEFT_ONLY,
                UP_LEFT,
                UP_LEFT_UPLEFT,
                EDGE,
                CORNERS,
                DOWN_RIGHT);
        for (Direction face : Direction.values()) {
            for (CtmState state : states) {
                List<AthenaQuad> quads = assertDoesNotThrow(
                        () -> AthenaNativeProvider.quads(
                                state,
                                face,
                                EMPTY_SPRITES));
                assertEquals(
                        4,
                        quads.size(),
                        "four half-face quads per partial state/face");
                assertQuadrant(
                        quads.get(0),
                        cornerSlot(
                                state.up(),
                                state.left(),
                                state.upLeft()),
                        0.0F,
                        0.5F,
                        1.0F,
                        0.5F);
                assertQuadrant(
                        quads.get(1),
                        cornerSlot(
                                state.up(),
                                state.right(),
                                state.upRight()),
                        0.5F,
                        1.0F,
                        1.0F,
                        0.5F);
                assertQuadrant(
                        quads.get(2),
                        cornerSlot(
                                state.down(),
                                state.left(),
                                state.downLeft()),
                        0.0F,
                        0.5F,
                        0.5F,
                        0.0F);
                assertQuadrant(
                        quads.get(3),
                        cornerSlot(
                                state.down(),
                                state.right(),
                                state.downRight()),
                        0.5F,
                        1.0F,
                        0.5F,
                        0.0F);
            }
        }
    }

    @Test
    void rejectsWrongRoleCountAndNullArguments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AthenaNativeProvider.quads(
                        ALL_FALSE,
                        Direction.NORTH,
                        new TextureAtlasSprite[4]));
        assertThrows(
                NullPointerException.class,
                () -> AthenaNativeProvider.quads(
                        null,
                        Direction.NORTH,
                        EMPTY_SPRITES));
        assertThrows(
                NullPointerException.class,
                () -> AthenaNativeProvider.quads(
                        ALL_FALSE,
                        null,
                        EMPTY_SPRITES));
    }

    /** 中文：CtmUtils.getTexture(up,left,upLeft) 真值表的独立复刻，用于锁定槽位合同。 / English: Independent replica of the CtmUtils.getTexture(up,left,upLeft) truth table locking the slot contract. */
    private static int cornerSlot(
            boolean up,
            boolean left,
            boolean upLeft) {
        if (up && left) {
            return upLeft ? 1 : 2;
        }
        if (up) {
            return 3;
        }
        if (left) {
            return 4;
        }
        return 0;
    }

    private static void assertQuadrant(
            AthenaQuad quad,
            int slot,
            float left,
            float right,
            float top,
            float bottom) {
        assertEquals(slot, quad.sprite(), "quadrant slot");
        assertEquals(left, quad.left(), 1.0e-6F);
        assertEquals(right, quad.right(), 1.0e-6F);
        assertEquals(top, quad.top(), 1.0e-6F);
        assertEquals(bottom, quad.bottom(), 1.0e-6F);
        assertEquals(0.0F, quad.depth(), 1.0e-6F);
        assertEquals(Rotation.NONE, quad.rotation());
    }
}
