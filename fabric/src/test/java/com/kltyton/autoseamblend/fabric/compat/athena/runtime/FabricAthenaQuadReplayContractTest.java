package com.kltyton.autoseamblend.fabric.compat.athena.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 中文：Athena 经真实 RenderContext 捕获 vanilla quad 后，BakedQuad 往返不能丢失原始
 * material/cullFace/nominalFace/tag。草方块侧面自带透明 overlay quad；若用默认实体材质
 * 回放，其透明区域会成为不透明黑色矩形。
 *
 * <p>English: After Athena captures vanilla quads through the real RenderContext, the
 * BakedQuad round-trip must retain the original material/cullFace/nominalFace/tag. Grass
 * sides contain a transparent overlay quad; replaying it with the default solid material
 * turns its transparent region into an opaque black rectangle.
 */
class FabricAthenaQuadReplayContractTest {
    @Test
    void captureRetainsMaterialAndFaceMetadata() {
        String source = productionModelSource();
        String captureBody = privateEmitBody(source);

        assertTrue(
                captureBody.contains("new CapturedQuad("),
                "the real-context transform must capture a metadata carrier");
        assertTrue(
                captureBody.contains("quad.material()")
                        && captureBody.contains("quad.cullFace()")
                        && captureBody.contains("quad.nominalFace()")
                        && captureBody.contains("quad.tag()"),
                "capture must retain material/cullFace/nominalFace/tag before converting "
                        + "to BakedQuad");
        assertTrue(
                Pattern.compile(
                                "record\\s+CapturedQuad\\(\\s*BakedQuad\\s+quad,"
                                        + "\\s*RenderMaterial\\s+material,"
                                        + "\\s*Direction\\s+cullFace,"
                                        + "\\s*Direction\\s+nominalFace,"
                                        + "\\s*int\\s+tag\\s*\\)",
                                Pattern.DOTALL)
                        .matcher(source)
                        .find(),
                "CapturedQuad must retain the BakedQuad and all four FRAPI metadata fields");
    }

    @Test
    void passthroughRestoresCapturedMaterialAndFaces() {
        String source = productionModelSource();
        String helper = methodBody(
                source,
                "private static void passthrough(");

        assertTrue(
                helper.contains("captured.quad()")
                        && helper.contains("captured.material()")
                        && helper.contains("captured.cullFace()"),
                "passthrough must rebuild through the explicit material/cullFace overload");
        assertTrue(
                Pattern.compile(
                                "prepared\\.nominalFace\\(\\s*captured\\.nominalFace\\(\\)\\s*\\)",
                                Pattern.DOTALL)
                        .matcher(helper)
                        .find()
                        && helper.contains("prepared.tag(captured.tag())"),
                "passthrough must restore nominalFace and tag before emission");
        assertFalse(
                helper.contains("FabricQuadEmitting.fromBakedQuad(\n"
                        + "                        output,\n"
                        + "                        quad)"),
                "passthrough must not use the default-material two-argument replay");
    }

    @Test
    void everyPassthroughBranchUsesCapturedRecord() {
        String source = productionModelSource();
        String emitBody = methodBody(
                source,
                "private static void emitQuad(");

        assertTrue(
                Pattern.compile(
                                "private\\s+static\\s+void\\s+emitQuad\\(.*?"
                                        + "CapturedQuad\\s+captured\\s*\\)",
                                Pattern.DOTALL)
                        .matcher(source)
                        .find(),
                "emitQuad must receive the captured metadata carrier");
        assertFalse(
                emitBody.contains("passthrough(output, quad)"),
                "no Athena passthrough branch may discard captured material metadata");
        assertTrue(
                emitBody.contains("passthrough(output, captured)"),
                "Athena passthrough branches must replay the captured record");
    }

    private static String privateEmitBody(String source) {
        return methodBody(source, "private void emitBlockQuads(");
    }

    private static String methodBody(
            String source,
            String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            throw new AssertionError("method not found: " + signature);
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
        throw new AssertionError("incomplete method: " + signature);
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
