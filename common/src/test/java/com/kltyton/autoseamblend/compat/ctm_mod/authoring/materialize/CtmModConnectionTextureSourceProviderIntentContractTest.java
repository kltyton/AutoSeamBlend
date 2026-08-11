package com.kltyton.autoseamblend.compat.ctm_mod.authoring.materialize;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kltyton.autoseamblend.engine.ownership.NativeSlotIntent;
import org.junit.jupiter.api.Test;

/**
 * 中文：CTM 载体意图分类合同（26.1.2 已验收语义）。未声明且无纹理引用的合成载体必须
 * 归为 OMITTED（可填充、可编辑）；已声明或有纹理引用但不可归类时保持 UNKNOWN（保护
 * 作者意图）。当前 1.21.1 实现把合成载体误判为 UNKNOWN，本测试应失败。
 *
 * <p>English: CTM carrier-intent classification contract (accepted 26.1.2 semantics).
 * Undeclared textureless synthetic carriers must be OMITTED (fillable/editable); declared or
 * texture-referencing carriers that cannot be classified stay UNKNOWN (author intent
 * protected). The current 1.21.1 implementation misclassifies synthetic carriers as UNKNOWN,
 * so this test is expected to fail.
 */
class CtmModConnectionTextureSourceProviderIntentContractTest {

    @Test
    void undeclaredTexturelessSyntheticCarrierIsOmitted() {
        NativeSlotIntent actual = CtmModConnectionTextureSourceProvider
                .resolveCarrierIntent(
                        false,
                        false,
                        false,
                        false,
                        false);
        assertEquals(
                NativeSlotIntent.OMITTED,
                actual,
                "undeclared textureless synthetic carrier must stay fillable/editable");
    }

    @Test
    void declaredCarrierWithoutResourceStaysUnknown() {
        NativeSlotIntent actual = CtmModConnectionTextureSourceProvider
                .resolveCarrierIntent(
                        false,
                        true,
                        false,
                        false,
                        false);
        assertEquals(
                NativeSlotIntent.UNKNOWN,
                actual,
                "declared but unresolvable carrier must stay protected UNKNOWN");
    }

    @Test
    void textureReferencingCarrierWithoutCaptureStaysUnknown() {
        NativeSlotIntent actual = CtmModConnectionTextureSourceProvider
                .resolveCarrierIntent(
                        false,
                        false,
                        true,
                        false,
                        false);
        assertEquals(
                NativeSlotIntent.UNKNOWN,
                actual,
                "texture-referencing but uncaptured carrier must stay protected UNKNOWN");
    }

    @Test
    void declaredMissingCarrierIsDeclaredMissing() {
        NativeSlotIntent actual = CtmModConnectionTextureSourceProvider
                .resolveCarrierIntent(
                        false,
                        true,
                        true,
                        false,
                        false);
        assertEquals(
                NativeSlotIntent.DECLARED_MISSING,
                actual,
                "declared carrier with missing texture is DECLARED_MISSING");
    }

    @Test
    void capturedCarrierIsPresent() {
        NativeSlotIntent actual = CtmModConnectionTextureSourceProvider
                .resolveCarrierIntent(
                        true,
                        true,
                        true,
                        true,
                        false);
        assertEquals(
                NativeSlotIntent.PRESENT,
                actual,
                "captured carrier is PRESENT");
    }

    @Test
    void authoringTemplateCarrierIsOmitted() {
        // 中文：真实流程中 authoringTemplate 时 declared 列表为空（provider 行 62），
        // 载体均为未声明合成；故此处 declared=false。26.1.2 分支顺序为
        // declared&&!resourcePresent 先于 authoringTemplate。
        // English: In the real flow authoringTemplate yields an empty declared list
        // (provider line 62), so carriers are undeclared synthetic; declared=false here.
        // 26.1.2 branch order keeps declared&&!resourcePresent ahead of authoringTemplate.
        NativeSlotIntent actual = CtmModConnectionTextureSourceProvider
                .resolveCarrierIntent(
                        false,
                        false,
                        false,
                        false,
                        true);
        assertEquals(
                NativeSlotIntent.OMITTED,
                actual,
                "authoring-template carrier is OMITTED");
    }
}
