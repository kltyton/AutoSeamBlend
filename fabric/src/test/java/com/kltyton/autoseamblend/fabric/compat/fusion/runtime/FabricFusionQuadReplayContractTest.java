package com.kltyton.autoseamblend.fabric.compat.fusion.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 中文：RED 合同——玻璃板端盖 cull 桶必须经真实 RenderContext 的 transform 栈捕获并统一恢复。
 * 1.21.1 的 RenderContext 自己持有 transform 栈（Indigo LIFO：最后 push 的先执行，false
 * 短路丢弃 quad；AbstractRenderContext.stackTransform 从 size()-1 走到 0）。生产代码必须
 * 直接把 capture QuadTransform 装到传入的真实 context 上：在 super 发射链之前
 * context.pushTransform(capture)，PaneCullingModel 在 super 调用链内把
 * PaneCapCullTransform push 到同一真实栈（因此 PaneCap 先运行），capture 在内部 pane
 * transform 之后记录 BakedQuad + material + cullFace + nominalFace + tag 并 return false，
 * 阻止 capture 以下的已有外层 transform 在捕获期重复运行；finally 必须 pop；回放前
 * capture 已从真实 context 移除，回放让外层 transform 恰好运行一次。禁止恢复 fake
 * CapturingRenderContext/BufferingEmitter/自建 TransformStack（真实 emitter 在每次
 * getEmitter() 和每次 emit() 后 clear，fake 捕获发生在真实 block emitter 生命周期完成前）。
 * 所有最终发射分支仍通过同一 prepareEmission 恢复 material+cullFace+nominalFace+tag，
 * overlay 用 CUTOUT material 但同样恢复源 cull/nominal/tag（精确匹配 26.1.2 人工通过版的
 * emitOverlayQuad(prepareEmission(...)) 形状）。断言以源码契约为对象，避免只匹配空白。
 *
 * <p>English: RED contract -- glass-pane cap cull buckets must be captured through the real
 * RenderContext transform stack and restored uniformly. In 1.21.1 the RenderContext owns the
 * transform stack itself (Indigo LIFO: last pushed runs first; false short-circuits and
 * discards the quad; AbstractRenderContext.stackTransform walks size()-1 down to 0).
 * Production code must install the capture QuadTransform directly on the incoming real
 * context: context.pushTransform(capture) before the super emission chain runs,
 * PaneCullingModel pushes PaneCapCullTransform onto that same real stack inside the super
 * chain (so PaneCap runs first), the capture records the BakedQuad plus
 * material/cullFace/nominalFace/tag after the inner pane transform and returns false, which
 * stops pre-existing outer transforms below the capture from running during capture; finally
 * must pop; replay runs only after the capture is removed from the real context so outer
 * transforms run exactly once. The fake CapturingRenderContext/BufferingEmitter/manual
 * TransformStack must stay removed (the real emitter clears on every getEmitter() and after
 * every emit(); the fake capture ran before the real block emitter completed its geometry
 * lifecycle). Every final emission branch still restores material+cullFace+nominalFace+tag
 * through one prepareEmission helper, and overlay uses CUTOUT material while still restoring
 * the captured source cull/nominal/tag (exactly the user-accepted 26.1.2
 * emitOverlayQuad(prepareEmission(...)) shape). Assertions target source contracts rather
 * than whitespace-only patterns.
 */
class FabricFusionQuadReplayContractTest {
    private static final String MODEL_RELATIVE =
            "com/kltyton/autoseamblend/fabric/compat/fusion/runtime/"
                    + "FabricFusionConnectedBlockStateModel.java";
    private static final String EMITTING_RELATIVE =
            "com/kltyton/autoseamblend/fabric/runtime/render/"
                    + "FabricQuadEmitting.java";
    private static final String TRACER_RELATIVE =
            "com/kltyton/autoseamblend/fabric/compat/fusion/diagnostic/"
                    + "FabricFusionPaneQuadTrace.java";

    @Test
    void emittingHelperAddsExplicitMaterialAndCullFaceReplay()
            throws IOException {
        String source = emittingSource();

        // 中文：保留 2 参数 API（Athena 调用点不得改变），仍以 quad.getDirection() 回放。
        // English: The 2-arg API is retained for Athena call sites unchanged, still
        // replaying with quad.getDirection().
        assertTrue(
                matches(
                        source,
                        "fromBakedQuad\\(\\s*QuadEmitter emitter,"
                                + "\\s*BakedQuad quad\\s*\\)"),
                "2-arg fromBakedQuad(emitter, quad) must remain for Athena");
        assertTrue(
                source.contains("quad.getDirection()"),
                "2-arg helper keeps quad.getDirection() for the Athena path");

        // 中文：显式 4 参数重载直接调用 fromVanilla(quad, material, cullFace)。
        // English: The explicit 4-arg overload must call
        // fromVanilla(quad, material, cullFace) directly.
        assertTrue(
                matches(
                        source,
                        "fromBakedQuad\\(\\s*QuadEmitter emitter,"
                                + "\\s*BakedQuad quad,"
                                + "\\s*RenderMaterial material,"
                                + "\\s*Direction cullFace\\s*\\)"),
                "explicit fromBakedQuad(emitter, quad, material, cullFace) is missing");
        assertTrue(
                matches(
                        source,
                        "fromVanilla\\(\\s*quad,\\s*material,\\s*cullFace\\s*\\)"),
                "4-arg overload must forward to fromVanilla(quad, material, cullFace)");

        // 中文：overlay 使用 materialFinder().blendMode(BlendMode.CUTOUT).find()。
        // English: cutoutMaterial() must be built with
        // materialFinder().blendMode(BlendMode.CUTOUT).find().
        assertTrue(
                matches(
                        source,
                        "cutoutMaterial\\(\\).*?materialFinder\\(\\)"
                                + "\\s*\\.blendMode\\(BlendMode\\.CUTOUT\\)"
                                + "\\s*\\.find\\(\\)"),
                "cutoutMaterial() must use BlendMode.CUTOUT materialFinder chain");
    }

    /**
     * 中文：capture 必须是真实 context 上的 QuadTransform，且必须在 super 发射链运行之前
     * push；PaneCullingModel 在 super 调用链内 push PaneCapCullTransform（晚于 capture 的
     * push），Indigo LIFO 保证 PaneCap 先运行、capture 后看到内部 pane transform 的最终状态。
     *
     * <p>English: The capture must be a QuadTransform installed on the real context, pushed
     * before the super emission chain runs; PaneCullingModel pushes PaneCapCullTransform
     * inside the super chain (later than the capture push), so Indigo LIFO runs PaneCap
     * first and the capture sees the final state after the inner pane transform.
     */
    @Test
    void captureTransformIsInstalledOnRealContextBeforeDelegateEmission()
            throws IOException {
        String source = modelSource();
        String emitBody = methodBody(
                source,
                "private void emitBlockQuads(");

        assertTrue(
                emitBody.contains(
                        "RenderContext.QuadTransform capture"),
                "capture must be a RenderContext.QuadTransform");
        assertTrue(
                emitBody.contains("context.pushTransform("),
                "capture must be installed on the real RenderContext via pushTransform");
        assertTrue(
                emitBody.indexOf("context.pushTransform(")
                        < emitBody.indexOf("super.emitBlockQuads("),
                "capture must be pushed before the delegate emission chain runs so the "
                        + "inner PaneCapCullTransform is pushed after it (LIFO: PaneCap "
                        + "runs first, capture sees the final post-pane state)");
    }

    /**
     * 中文：capture 变换必须读取 transform 后的 quad/material/cullFace/nominalFace/tag，
     * 通过 SpriteFinder 反查精灵生成 BakedQuad，构造 CapturedQuad 后 return false 丢弃，
     * 从而阻止 capture 以下的已有外层 transform 在捕获期运行。
     *
     * <p>English: The capture transform must read the transformed
     * quad/material/cullFace/nominalFace/tag, resolve the sprite through the SpriteFinder
     * to build the BakedQuad, construct the CapturedQuad, and return false so pre-existing
     * outer transforms below the capture never run during capture.
     */
    @Test
    void captureTransformRecordsQuadAndMetadataAndReturnsFalse()
            throws IOException {
        String source = modelSource();
        String emitBody = methodBody(
                source,
                "private void emitBlockQuads(");

        assertTrue(
                emitBody.contains("quad.material()")
                        && emitBody.contains("quad.cullFace()")
                        && emitBody.contains("quad.nominalFace()")
                        && emitBody.contains("quad.tag()"),
                "capture must read the transformed material/cullFace/nominalFace/tag "
                        + "from the MutableQuadView");
        assertTrue(
                matches(
                        emitBody,
                        "quad\\.toBakedQuad\\(\\s*finder\\.find\\(quad\\)\\s*\\)"),
                "capture must convert the transformed quad to a BakedQuad via the "
                        + "SpriteFinder");
        assertTrue(
                emitBody.contains("new CapturedQuad("),
                "capture must construct a CapturedQuad carrying the five facts");
        assertTrue(
                emitBody.indexOf("new CapturedQuad(")
                        < emitBody.indexOf("return false;"),
                "capture must record the quad before returning false");
        assertTrue(
                emitBody.contains("return false;"),
                "capture must return false so outer transforms below the capture do not "
                        + "run during capture");
    }

    /**
     * 中文：capture 必须在 finally 中从真实 context pop，且回放循环只能出现在 pop 之后，
     * 保证回放时 capture 已移除、已有外层 transform 恰好运行一次。
     *
     * <p>English: The capture must be popped from the real context in a finally block, and
     * the replay loop must only appear after that pop, so capture is removed before replay
     * and pre-existing outer transforms run exactly once.
     */
    @Test
    void captureTransformIsPoppedInFinallyBeforeReplay()
            throws IOException {
        String source = modelSource();
        String emitBody = methodBody(
                source,
                "private void emitBlockQuads(");

        int tryIndex = emitBody.indexOf("try {");
        int finallyIndex = emitBody.indexOf("finally");
        int popIndex = emitBody.indexOf("context.popTransform();");
        int replayIndex = emitBody.indexOf(
                "for (CapturedQuad captured : capturedQuads)");
        assertTrue(
                tryIndex >= 0 && finallyIndex > tryIndex,
                "the delegate emission must be wrapped in a try/finally");
        assertTrue(
                popIndex > finallyIndex,
                "context.popTransform() must live in the finally block");
        assertTrue(
                replayIndex > popIndex,
                "the capture must be removed from the real context before replay runs");
    }

    /**
     * 中文：fake CapturingRenderContext/BufferingEmitter/自建 TransformStack 必须删除；
     * 捕获只能通过真实 RenderContext 的 transform 栈发生。
     *
     * <p>English: The fake CapturingRenderContext/BufferingEmitter/manual TransformStack
     * must be deleted; capture may only happen through the real RenderContext transform
     * stack.
     */
    @Test
    void fakeCapturePipelineIsRemoved()
            throws IOException {
        String source = modelSource();
        assertFalse(
                source.contains("CapturingRenderContext"),
                "the fake capture context must be deleted");
        assertFalse(
                source.contains("BufferingEmitter"),
                "the fake buffering emitter must be deleted");
        assertFalse(
                source.contains("TransformStack"),
                "the manual transform stack must be deleted");
    }

    /**
     * 中文：捕获载体必须保存 transform 后的 quad、material、cullFace、nominalFace、tag
     * 五个事实；BakedQuad 往返会丢失后四个。
     *
     * <p>English: The capture carrier must keep the transformed quad plus
     * material/cullFace/nominalFace/tag; the BakedQuad round-trip loses the latter four.
     */
    @Test
    void capturedQuadRetainsFullMetadata()
            throws IOException {
        String source = modelSource();
        assertTrue(
                matches(
                        source,
                        "record CapturedQuad\\(\\s*BakedQuad quad,"
                                + "\\s*RenderMaterial material,"
                                + "\\s*Direction cullFace,"
                                + "\\s*Direction nominalFace,"
                                + "\\s*int tag\\s*\\)"),
                "CapturedQuad must retain quad, material, cullFace, nominalFace, and tag");
    }

    /**
     * 中文：1.21.1 multipart 目录可能缺少运行时十字状态的精确 surface；Fusion 回放必须把
     * capture 后的 nominalFace 交给已有四参数目录回退，不能继续只查三参数 exact key。
     *
     * <p>English: The 1.21.1 multipart catalog may miss the runtime cross state's exact
     * surface; Fusion replay must pass the captured nominalFace into the existing
     * four-argument catalog fallback instead of retaining the exact-only overload.
     */
    @Test
    void multipartPaneSurfaceLookupUsesCapturedNominalFaceFallback()
            throws IOException {
        String source = modelSource();
        String emitBody = methodBody(
                source,
                "private void emitBlockQuads(");

        assertTrue(
                matches(
                        emitBody,
                        "surfaces\\.face\\(\\s*state,"
                                + "\\s*quad\\.getDirection\\(\\),"
                                + "\\s*captured\\.nominalFace\\(\\),"
                                + "\\s*sprite\\s*\\)"),
                "Fusion pane replay must use lightFace + captured nominalFace + sprite");
    }

    /**
     * 中文：所有最终发射分支（passthrough、top、native replacement、none、empty 回退、
     * overlay）必须共用同一 prepareEmission 恢复 captured material/cullFace/nominalFace/tag；
     * 主循环只能通过 prepareEmission 发射，不得直接调 FabricQuadEmitting.fromBakedQuad。
     *
     * <p>English: Every final emission branch (passthrough, top, native replacement, none,
     * empty fallback, overlay) must share one prepareEmission restoring the captured
     * material/cullFace/nominalFace/tag; the main loop must only emit through
     * prepareEmission and never call FabricQuadEmitting.fromBakedQuad directly.
     */
    @Test
    void everyEmissionBranchRestoresMetadataThroughPrepareEmission()
            throws IOException {
        String source = modelSource();
        String helper = methodBody(
                source,
                "private static QuadEmitter prepareEmission(");
        assertTrue(
                helper.contains("FabricQuadEmitting.fromBakedQuad("),
                "prepareEmission must start from the shared fromBakedQuad entry");
        assertTrue(
                helper.contains("source.cullFace()")
                        && helper.contains("source.nominalFace()")
                        && helper.contains("source.tag()"),
                "prepareEmission must restore captured cullFace/nominalFace/tag");
        assertEquals(
                9,
                occurrences(source, "prepareEmission("),
                "8 emission call sites plus 1 helper declaration must all go through "
                        + "prepareEmission");
        assertEquals(
                1,
                occurrences(source, "FabricQuadEmitting.fromBakedQuad("),
                "the only direct fromBakedQuad call must live inside prepareEmission");
        assertTrue(
                source.contains(
                        "for (CapturedQuad captured : capturedQuads)"),
                "the main loop must iterate the real-context captured records so the "
                        + "metadata reaches every branch");
    }

    /**
     * 中文：overlay 出口复用 prepareEmission，显式 CUTOUT material，但仍恢复 captured source
     * 的 cullFace/nominalFace/tag（匹配 26.1.2 人工通过版），并保留 tint 写入；不得再使用
     * overlay cullFace=null 旧假设。
     *
     * <p>English: The overlay exit reuses prepareEmission with an explicit CUTOUT material
     * while still restoring the captured source cullFace/nominalFace/tag (matching the
     * accepted 26.1.2 shape) and keeps the tint write; the old overlay cullFace=null
     * assumption must be gone.
     */
    @Test
    void overlayUsesCutoutMaterialButRestoresSourceMetadata()
            throws IOException {
        String source = modelSource();
        String overlayBody = methodBody(
                source,
                "private static void emitOverlayQuad(");
        assertTrue(
                overlayBody.contains("prepareEmission("),
                "overlay replay must reuse prepareEmission");
        assertTrue(
                overlayBody.contains("cutoutMaterial()"),
                "overlay replay must keep the explicit CUTOUT material");
        assertTrue(
                overlayBody.contains("color(vertex, tint)")
                        && overlayBody.contains("output.emit()"),
                "overlay replay must keep the fixed ARGB tint write before emitting");
        assertFalse(
                overlayBody.contains("cullFace(null)"),
                "overlay must restore the captured source cullFace instead of null");
    }

    @Test
    void tracerIsRemovedFromFusionRuntime()
            throws IOException {
        String source = modelSource();
        assertFalse(
                source.contains("FabricFusionPaneQuadTrace"),
                "Fusion model must not reference the diagnostic tracer");
        assertFalse(
                Files.exists(sourcePath(TRACER_RELATIVE)),
                "diagnostic tracer file must be deleted");
    }

    private static String modelSource() throws IOException {
        return read(sourcePath(MODEL_RELATIVE));
    }

    private static String emittingSource() throws IOException {
        return read(sourcePath(EMITTING_RELATIVE));
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static Path sourcePath(String relative) {
        List<Path> candidates = List.of(
                Paths.get("src/main/java").resolve(relative),
                Paths.get("fabric/src/main/java").resolve(relative),
                Paths.get("1.21.1/AutoSeamBlend-1.21.1/fabric/src/main/java")
                        .resolve(relative));
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return candidates.get(0);
    }

    private static boolean matches(String source, String regex) {
        return Pattern.compile(regex, Pattern.DOTALL)
                .matcher(source)
                .find();
    }

    /**
     * 中文：按签名前缀定位方法体（首个 '{' 到配平 '}'），用于精确限定捕获/回放代码区域。
     * English: Locates a method body by signature prefix (first '{' to the balanced '}'),
     * scoping assertions to the exact capture/replay region.
     */
    private static String methodBody(String source, String signaturePrefix) {
        int start = source.indexOf(signaturePrefix);
        assertTrue(
                start >= 0,
                "missing method: " + signaturePrefix);
        int brace = source.indexOf('{', start);
        assertTrue(
                brace >= 0,
                "missing brace for: " + signaturePrefix);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(brace, i + 1);
                }
            }
        }
        throw new AssertionError(
                "unbalanced method body: " + signaturePrefix);
    }

    /** 中文：统计 needle 在 source 中的非重叠出现次数。 English: Counts non-overlapping occurrences of needle. */
    private static int occurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
