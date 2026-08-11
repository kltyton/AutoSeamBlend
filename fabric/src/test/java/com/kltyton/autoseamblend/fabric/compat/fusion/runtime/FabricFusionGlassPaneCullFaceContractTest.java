package com.kltyton.autoseamblend.fabric.compat.fusion.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 中文：RED 合同——玻璃板端盖 cull 桶必须保留。当前 wrapper 缓冲时以
 * ignored -> false 绕过调用方 cullTest（强制所有面不剔除），且 26.1.2 BakedQuad 不保留
 * cullFace，重发射会丢失调用方的 cull 桶。测试断言 wrapper 不再出现
 * ignored -> false 的绕过写法，并要求 cull face/过滤语义仍在 wrapper 中保留。行为级测试
 * 需要 FRAPI Renderer 运行时（普通 JUnit 无 renderer 注册），因此采用最小源合同。
 *
 * <p>English: RED contract -- glass pane cap cull buckets must be preserved. The wrapper
 * currently bypasses the caller cullTest with an ignored -> false predicate while buffering
 * (forcing every face unculled), and the 26.1.2 BakedQuad does not retain cullFace, so
 * re-emission drops the caller's cull bucket. The test asserts the wrapper no longer
 * contains the ignored -> false bypass and that cull face/filter semantics stay in the
 * wrapper. A behavioral test would need a FRAPI Renderer runtime (no renderer is registered
 * in a plain JUnit JVM), so the minimal source contract is used instead.
 */
class FabricFusionGlassPaneCullFaceContractTest {
    @Test
    void glassPaneCapsMustNotBypassCallerCullTestWithAlwaysFalsePredicate() {
        String source = productionSource(
                "FabricFusionConnectedBlockStateModel.java");
        assertFalse(
                source.contains("ignored -> false"),
                "glass pane caps: buffering with an always-false cull predicate forces "
                        + "every face unculled and re-emission drops the caller's cull "
                        + "bucket; the wrapper must honor the caller cullTest instead");
    }

    @Test
    void glassPaneCapsMustKeepCullFaceSemantics() {
        String source = productionSource(
                "FabricFusionConnectedBlockStateModel.java");
        assertTrue(
                source.contains("cullTest")
                        || source.contains("cullFace"),
                "glass pane caps: the wrapper must preserve cull face/filter semantics "
                        + "while re-emitting buffered quads");
    }

    /**
     * 中文：RED 合同——玻璃板顶/底 PaneCulling 依赖真实 RenderContext 的 transform 栈：
     * capture 必须是 push 到传入 context 的最外层 QuadTransform，super 发射链内的
     * PaneCapCullTransform 先运行，capture 记录 transform 后的 BakedQuad+材质+cull/
     * nominal/tag 并返回 false，捕获结束 popTransform 后再回放；不得绕过 cull 语义。
     *
     * <p>English: RED contract -- glass pane cap PaneCulling relies on the real
     * RenderContext transform stack: capture is the outermost QuadTransform pushed on
     * the incoming context, PaneCapCullTransform inside the super chain runs first, the
     * capture records the transformed BakedQuad plus material/cull/nominal/tag and
     * returns false, and replay happens after popTransform; cull semantics are never
     * bypassed.
     */
    @Test
    void glassPaneCapsMustRunFusionTransformStackThroughRendererCallback() {
        String source = productionSource(
                "FabricFusionConnectedBlockStateModel.java");
        assertTrue(
                source.contains("context.pushTransform(capture)"),
                "glass pane caps: the capture QuadTransform must be pushed on the "
                        + "real RenderContext before the super emission chain");
        assertTrue(
                source.contains("quad.toBakedQuad("),
                "glass pane caps: the capture must convert the transformed quad "
                        + "to a BakedQuad after PaneCapCullTransform ran");
        assertTrue(
                source.contains("return false;"),
                "glass pane caps: the capture must stop the stack so pre-existing "
                        + "outer transforms never run twice during capture");
        assertTrue(
                source.contains("context.popTransform()"),
                "glass pane caps: replay must happen only after the capture is "
                        + "removed from the real context");
    }

    /**
     * 中文：RED 合同——BakedQuad 往返不保留 cullFace/nominalFace/tag，capture 必须在
     * toBakedQuad 处捕获 transform 后 quad 的这三个语义，并用 CapturedQuad record
     * 缓冲（含材质），而不是裸 BakedQuad。
     *
     * <p>English: RED contract -- the BakedQuad round-trip does not retain
     * cullFace/nominalFace/tag, so the capture must take all three from the
     * transformed quad at toBakedQuad and buffer a CapturedQuad record (with
     * material), not a bare BakedQuad.
     */
    @Test
    void glassPaneCapsMustCaptureTransformedCullFaceNominalFaceAndTagInBufferedRecord() {
        String source = productionSource(
                "FabricFusionConnectedBlockStateModel.java");
        assertTrue(
                source.contains("new CapturedQuad(")
                        && source.contains("quad.cullFace()")
                        && source.contains("quad.nominalFace()")
                        && source.contains("quad.tag()"),
                "glass pane caps: the capture must record the transformed quad's "
                        + "cullFace, nominalFace, and tag with toBakedQuad");
        assertTrue(
                source.contains("record CapturedQuad("),
                "glass pane caps: the capture must buffer a CapturedQuad record, "
                        + "not a bare BakedQuad");
        assertTrue(
                source.contains("Direction cullFace,")
                        && source.contains("Direction nominalFace,")
                        && source.contains("int tag)"),
                "glass pane caps: CapturedQuad must retain the quad plus nullable "
                        + "cullFace/nominalFace/tag captured after the transform");
        assertTrue(
                source.contains("ArrayList<CapturedQuad>"),
                "glass pane caps: the wrapper must buffer CapturedQuad records "
                        + "so the transformed cullFace survives the BakedQuad "
                        + "round-trip");
    }

    /**
     * 中文：RED 合同——所有最终发射分支（passthrough、top、native replacement、none、
     * empty 回退、overlay）必须在 fromBakedQuad(output) 之后恢复 source 的
     * cullFace/nominalFace/tag，且共用同一 prepareEmission helper。
     *
     * <p>English: RED contract -- every final emission branch (passthrough, top,
     * native replacement, none, empty fallback, overlay) must restore the source
     * cullFace/nominalFace/tag after fromBakedQuad(output) and share one
     * prepareEmission helper.
     */
    @Test
    void glassPaneCapsMustRestoreSourceCullFaceAfterFromBakedQuadOnEveryEmission() {
        String source = productionSource(
                "FabricFusionConnectedBlockStateModel.java");
        String helper = methodBody(
                source,
                "private static QuadEmitter prepareEmission(");
        assertTrue(
                helper.contains("fromBakedQuad("),
                "glass pane caps: prepareEmission must start from "
                        + "fromBakedQuad(output)");
        assertTrue(
                helper.contains("source.cullFace()")
                        && helper.contains("source.nominalFace()")
                        && helper.contains("source.tag()"),
                "glass pane caps: prepareEmission must restore the source "
                        + "cullFace/nominalFace/tag after fromBakedQuad");
        assertEquals(
                1,
                occurrences(source, "FabricQuadEmitting.fromBakedQuad"),
                "glass pane caps: every final emission branch (passthrough, top, "
                        + "native replacement, none, empty fallback, overlay) must go "
                        + "through the same prepareEmission helper");
        assertTrue(
                source.contains(
                        "for (CapturedQuad captured : capturedQuads)"),
                "glass pane caps: the main loop must iterate captured records so "
                        + "source cull/nominal/tag reach each branch");
    }

    /**
     * 中文：RED 合同——overlay 分支必须复用 prepareEmission，使 overlay replacement
     * 在写入 tint 前已恢复 source 的 cull 桶。
     *
     * <p>English: RED contract -- the overlay branch must reuse prepareEmission so
     * overlay replacements keep the source cull bucket before the tint is written.
     */
    @Test
    void glassPaneCapsOverlayEmissionMustUseSameRestoreHelper() {
        String source = productionSource(
                "FabricFusionConnectedBlockStateModel.java");
        String overlayBody = methodBody(
                source,
                "private static void emitOverlayQuad(");
        assertTrue(
                overlayBody.contains("prepareEmission("),
                "glass pane caps: the overlay branch must reuse prepareEmission "
                        + "so overlay replacements keep the source cull bucket");
        assertTrue(
                overlayBody.contains("output.color(vertex, tint)")
                        && overlayBody.contains("output.emit()"),
                "glass pane caps: the overlay branch must still write the tint "
                        + "before emitting the prepared quad");
    }

    /**
     * 中文：返回 Renderer quadEmitter(...) 回调 lambda 体的源码片段（从首个开括号
     * 到其后的闭括号；当前实现中回调体无嵌套块）。
     *
     * <p>English: Returns the source span of the Renderer quadEmitter(...) callback
     * lambda body (from its opening brace to the following closing brace; the current
     * callback body has no nested blocks).
     */
    private static String quadEmitterCallback(
            String source) {
        int marker = source.indexOf("quadEmitter(");
        if (marker < 0) {
            throw new AssertionError(
                    "Renderer quadEmitter callback not found");
        }
        int open = source.indexOf("{", marker);
        int close = source.indexOf("}", open);
        if (open < 0 || close < 0) {
            throw new AssertionError(
                    "Renderer quadEmitter callback body not found");
        }
        return source.substring(open + 1, close);
    }

    /**
     * 中文：返回从方法签名到下一个 @Override 的源码片段（最小方法体窗口）。
     *
     * <p>English: Returns the source span from the method signature to the next
     * @Override (a minimal method-body window).
     */
    private static String methodBody(
            String source,
            String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            throw new AssertionError(
                    "method not found: " + signature);
        }
        int end = source.indexOf("@Override", start);
        if (end < 0) {
            end = source.length();
        }
        return source.substring(start, end);
    }

    /**
     * 中文：统计 needle 在 source 中的出现次数。
     *
     * <p>English: Counts non-overlapping occurrences of needle in source.
     */
    private static int occurrences(
            String source,
            String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    /**
     * 中文：读取 Fabric 生产 wrapper 的当前源码（只读静态证据）；按测试工作目录依次尝试
     * 模块/工程/聚合仓库三种根位置，未找到时以明确断言失败。
     *
     * <p>English: Reads the current source of the Fabric production wrapper (read-only static
     * evidence), trying the module, project, and aggregate-repository root positions in order
     * from the test working directory, and fails explicitly when absent.
     */
    private static String productionSource(
            String fileName) {
        String relative =
                "com/kltyton/autoseamblend/fabric/compat/fusion/runtime/"
                        + fileName;
        List<Path> candidates = List.of(
                Path.of("src/main/java", relative),
                Path.of(
                        "fabric/src/main/java",
                        relative),
                Path.of(
                        "26.1.2/AutoSeamBlend-26.1.2/"
                                + "fabric/src/main/java",
                        relative));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                try {
                    return Files.readString(candidate);
                } catch (IOException exception) {
                    throw new UncheckedIOException(
                            exception);
                }
            }
        }
        throw new AssertionError(
                "production source not found from "
                        + Path.of("").toAbsolutePath());
    }
}
