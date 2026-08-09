package com.kltyton.autoseamblend.fabric.compat.athena.runtime.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.StateSurface;
import java.util.List;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：RED 契约——pane 首烤表面收集（FabricPaneSurfaceRoles.surfaceFacesFromModel）必须
 * 按 quad 几何方向分组、按精灵去重，并把透明性判定为与 1.21.1 ce33d6c
 * surfaceFacesFromModel 相同的全像素 isTransparent 语义；body/edge 角色选择与 sibling
 * cap 借用属于 common AthenaPaneSurfaceRoles（由该 common 契约测试覆盖），不再在本文件
 * 重复。当前 26.1.2 Fabric 没有等价收集器时测试先红。
 *
 * <p>English: RED contract -- the pane first-bake surface collection
 * (FabricPaneSurfaceRoles.surfaceFacesFromModel) must group by quad geometry direction,
 * deduplicate by sprite, and decide transparency with the same all-pixels isTransparent
 * semantics as the 1.21.1 ce33d6c surfaceFacesFromModel; body/edge role selection and
 * sibling cap borrowing belong to the common AthenaPaneSurfaceRoles (covered by that
 * common contract test) and are not duplicated here. The test fails first while the 26.1.2
 * Fabric side has no equivalent collector.
 */
class FabricPaneSurfaceRolesContractTest {

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.setVersion(
                DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @Test
    void surfaceFacesFromModelGroupsByGeometryDirectionAndSprite() {
        BakedQuad upBody = PaneTestQuads.quad(
                PaneTestSprites.BODY,
                Direction.UP,
                0.1F,
                0.2F,
                new float[][] {
                    {0.0F, 16.0F, 0.0F},
                    {16.0F, 16.0F, 0.0F},
                    {16.0F, 16.0F, 16.0F},
                    {0.0F, 16.0F, 16.0F}
                });
        BakedQuad eastEdge = PaneTestQuads.quad(
                PaneTestSprites.EDGE,
                Direction.EAST,
                0.3F,
                0.4F,
                new float[][] {
                    {0.0F, 0.0F, 0.0F},
                    {0.0F, 16.0F, 0.0F},
                    {0.0F, 16.0F, 4.0F},
                    {0.0F, 0.0F, 4.0F}
                });
        BlockStateModel model = stubModel(
                List.of(
                        part(Direction.UP, upBody),
                        part(Direction.EAST, eastEdge)));
        BlockState state = Blocks.GLASS_PANE.defaultBlockState();

        StateSurface collected =
                FabricPaneSurfaceRoles
                        .surfaceFacesFromModel(
                                state,
                                model);

        assertEquals(
                state,
                collected.state(),
                "collected surface must bind the queried state");
        assertTrue(
                collected.faces()
                        .getOrDefault(
                                Direction.UP,
                                List.of())
                        .stream()
                        .anyMatch(surface ->
                                surface.sprite()
                                                .contents()
                                                .name()
                                                .equals(
                                                        PaneTestSprites.BODY
                                                                .contents()
                                                                .name())
                                        && !surface.fullyTransparent()),
                "UP bucket must carry the non-transparent body sprite");
        assertTrue(
                collected.faces()
                        .getOrDefault(
                                Direction.EAST,
                                List.of())
                        .stream()
                        .anyMatch(surface ->
                                surface.sprite()
                                                .contents()
                                                .name()
                                                .equals(
                                                        PaneTestSprites.EDGE
                                                                .contents()
                                                                .name())
                                        && surface.fullyTransparent()),
                "EAST bucket must carry the fully-transparent edge sprite");
        assertFalse(
                collected.faces()
                        .getOrDefault(
                                Direction.NORTH,
                                List.of())
                        .stream()
                        .findAny()
                        .isPresent(),
                "missing directions must stay absent");
    }

    private static BlockStateModel stubModel(
            List<BlockStateModelPart> parts) {
        return new BlockStateModel() {
            @Override
            public void collectParts(
                    RandomSource random,
                    List<BlockStateModelPart> output) {
                output.addAll(parts);
            }

            @Override
            public Material.Baked particleMaterial() {
                return null;
            }

            @Override
            public int materialFlags() {
                return 0;
            }
        };
    }

    private static BlockStateModelPart part(
            Direction direction,
            BakedQuad quad) {
        return new BlockStateModelPart() {
            @Override
            public List<BakedQuad> getQuads(
                    Direction cullFace) {
                return cullFace == direction
                        ? List.of(quad)
                        : List.of();
            }

            @Override
            public boolean useAmbientOcclusion() {
                return false;
            }

            @Override
            public Material.Baked particleMaterial() {
                return null;
            }

            @Override
            public int materialFlags() {
                return 0;
            }
        };
    }
}
