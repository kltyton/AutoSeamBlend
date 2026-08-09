package com.kltyton.autoseamblend.runtime.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.inference.InferenceDecision;
import com.kltyton.autoseamblend.inference.InferenceFacts;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：锁定 1.21.1 multipart 模型目录与运行时玻璃板状态不一致时的 prepared 方法回退。
 * English: Locks prepared-method fallback when the 1.21.1 multipart model catalog and the
 * runtime glass-pane state disagree.
 */
class PreparedSurfaceMethodsMultipartFallbackTest {
    private static final ResourceLocation GLASS =
            ResourceLocation.fromNamespaceAndPath("minecraft", "block/glass");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.setVersion(DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @Test
    void missingExactPaneStateUsesUnambiguousSiblingMethod() {
        BlockState donor = Blocks.GLASS_PANE.defaultBlockState();
        BlockState runtimeCross = donor
                .setValue(IronBarsBlock.NORTH, true)
                .setValue(IronBarsBlock.EAST, true)
                .setValue(IronBarsBlock.SOUTH, true)
                .setValue(IronBarsBlock.WEST, true);
        PreparedSurfaceMethods.Snapshot snapshot = snapshot(Map.of(
                key(donor), prepared(ConnectionMethod.CTM)));

        assertEquals(
                Optional.of(ConnectionMethod.CTM),
                snapshot.method(runtimeCross, Direction.NORTH, GLASS),
                "runtime pane state must reuse the same block/direction/sprite method");
    }

    @Test
    void conflictingSiblingMethodsDoNotGuess() {
        BlockState base = Blocks.GLASS_PANE.defaultBlockState();
        BlockState east = base.setValue(IronBarsBlock.EAST, true);
        BlockState runtimeCross = base
                .setValue(IronBarsBlock.NORTH, true)
                .setValue(IronBarsBlock.SOUTH, true);
        PreparedSurfaceMethods.Snapshot snapshot = snapshot(Map.of(
                key(base), prepared(ConnectionMethod.CTM),
                key(east), prepared(ConnectionMethod.HORIZONTAL)));

        assertTrue(
                snapshot.method(runtimeCross, Direction.NORTH, GLASS).isEmpty(),
                "conflicting sibling methods must reject cross-state fallback");
    }

    @Test
    void routerUsesExistingMultipartSurfaceFallback() throws IOException {
        String source = Files.readString(routerSource(), StandardCharsets.UTF_8);
        assertTrue(
                source.matches(
                        "(?s).*surfaces\\(\\)\\.face\\(\\s*state,"
                                + "\\s*quad\\.getDirection\\(\\),"
                                + "\\s*quad\\.getDirection\\(\\),"
                                + "\\s*sprite\\s*\\).*"),
                "exact router must call the four-argument multipart surface lookup");
    }

    private static PreparedSurfaceMethods.Snapshot snapshot(
            Map<PreparedSurfaceMethods.Key, PreparedSurfaceMethods.PreparedMethod> methods) {
        return new PreparedSurfaceMethods.Snapshot(1, "multipart-test", methods);
    }

    private static PreparedSurfaceMethods.Key key(BlockState state) {
        return new PreparedSurfaceMethods.Key(state, Direction.NORTH, GLASS);
    }

    private static PreparedSurfaceMethods.PreparedMethod prepared(ConnectionMethod method) {
        return new PreparedSurfaceMethods.PreparedMethod(
                InferenceFacts.unknown(),
                new InferenceDecision(
                        ConnectionMethod.AUTO,
                        Optional.of(method),
                        false,
                        InferenceDecision.Confidence.CERTAIN,
                        List.of("multipart_test"),
                        List.of()));
    }

    private static Path routerSource() {
        Path relative = Paths.get(
                "com/kltyton/autoseamblend/engine/routing/query/EngineQueryRouterCore.java");
        List<Path> candidates = List.of(
                Paths.get("src/main/java").resolve(relative),
                Paths.get("common/src/main/java").resolve(relative),
                Paths.get("1.21.1/AutoSeamBlend-1.21.1/common/src/main/java")
                        .resolve(relative));
        return candidates.stream()
                .filter(Files::exists)
                .findFirst()
                .orElse(candidates.get(0));
    }
}
