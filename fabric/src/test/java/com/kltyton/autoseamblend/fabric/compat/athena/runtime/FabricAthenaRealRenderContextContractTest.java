package com.kltyton.autoseamblend.fabric.compat.athena.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 中文：Athena 必须通过传入的真实 FRAPI RenderContext 捕获委托模型发射的 quad。
 * 原版 BakedModel 的默认发射路径会把 context 转成 Indigo AbstractBlockRenderContext；
 * 自制 RenderContext 因而会在进入世界时发生 ClassCastException。
 *
 * <p>English: Athena must capture delegate quads through the incoming real FRAPI
 * RenderContext. Vanilla BakedModel's default emission path casts the context to Indigo's
 * AbstractBlockRenderContext, so a fabricated RenderContext crashes while entering a world.
 */
class FabricAthenaRealRenderContextContractTest {
    @Test
    void captureUsesIncomingRenderContextTransformStack() {
        String source = productionModelSource();
        String body = privateEmitBody(source);

        assertTrue(
                body.contains("RenderContext.QuadTransform capture"),
                "Athena capture must use a QuadTransform");
        assertTrue(
                body.contains("context.pushTransform(capture);"),
                "Athena capture must be installed on the incoming RenderContext");
        assertTrue(
                body.indexOf("context.pushTransform(capture);")
                        < body.indexOf("super.emitBlockQuads("),
                "the capture transform must be pushed before delegate emission");
        assertTrue(
                body.contains("quad.toBakedQuad(")
                        && body.contains("finder.find(quad)"),
                "the transform must capture the emitted quad through SpriteFinder");
        assertTrue(
                body.contains("return false;"),
                "captured delegate quads must be suppressed until replay");
    }

    @Test
    void captureIsAlwaysPoppedBeforeReplay() {
        String body = privateEmitBody(productionModelSource());
        int finallyIndex = body.indexOf("finally");
        int popIndex = body.indexOf("context.popTransform();");
        int replayIndex = body.indexOf("for (CapturedQuad captured : capturedQuads)");

        assertTrue(
                body.indexOf("try {") >= 0 && finallyIndex >= 0,
                "delegate emission must be protected by try/finally");
        assertTrue(
                popIndex > finallyIndex,
                "the capture transform must be popped in finally");
        assertTrue(
                replayIndex > popIndex,
                "replay must start only after the capture transform is removed");
    }

    @Test
    void fabricatedContextAndEmitterAreRemoved() {
        String source = productionModelSource();

        assertFalse(
                source.contains("CapturingRenderContext"),
                "the fabricated RenderContext is incompatible with Indigo");
        assertFalse(
                source.contains("BufferingEmitter"),
                "capture must not bypass the real Indigo emitter lifecycle");
    }

    private static String privateEmitBody(String source) {
        int start = source.indexOf("private void emitBlockQuads(");
        if (start < 0) {
            throw new AssertionError("private Athena emitBlockQuads method not found");
        }
        int bodyStart = source.indexOf('{', start);
        int depth = 0;
        for (int index = bodyStart; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart, index + 1);
                }
            }
        }
        throw new AssertionError("private Athena emitBlockQuads body is incomplete");
    }

    private static String productionModelSource() {
        String relative =
                "com/kltyton/autoseamblend/fabric/compat/athena/runtime/"
                        + "FabricAthenaConnectedBlockStateModel.java";
        List<Path> candidates = List.of(
                Path.of("src/main/java", relative),
                Path.of("fabric/src/main/java", relative));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                try {
                    return Files.readString(candidate);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            }
        }
        throw new AssertionError(
                "FabricAthenaConnectedBlockStateModel source not found from "
                        + Path.of("").toAbsolutePath());
    }
}
