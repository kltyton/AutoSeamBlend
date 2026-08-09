package com.kltyton.autoseamblend.compat.fusion.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/**
 * 中文：RED 合同——共享 Fusion 文档定位归一化必须存在于 common。Fabric 侧以 stripped
 * model id（minecraft:block/glass）查询，而 accepted 文档键是完整文件 ID
 * （minecraft:fusion/model_modifiers/blocks/block/glass.json），恒 miss；归一化语义应由
 * common 的 FusionModifierDocumentLocation.resourceId(Identifier) 提供。当前共享类尚不
 * 存在，因此本测试通过反射加载并以明确 AssertionError 失败（RED），而不是编译失败。
 *
 * <p>English: RED contract -- shared Fusion document-location normalization must live in
 * common. The Fabric side queries with a stripped model id (minecraft:block/glass) while
 * accepted-document keys are full file IDs
 * (minecraft:fusion/model_modifiers/blocks/block/glass.json), so the lookup always misses;
 * normalization semantics belong to common's
 * FusionModifierDocumentLocation.resourceId(Identifier). The shared class does not exist
 * yet, so this test loads it via reflection and fails with an explicit AssertionError (RED)
 * instead of a compile error.
 */
class FusionAcceptedDocumentLocationContractTest {
    private static final String SHARED_TYPE =
            "com.kltyton.autoseamblend.compat.fusion.runtime"
                    + ".FusionModifierDocumentLocation";
    private static final Identifier STRIPPED_MODEL_ID =
            Identifier.parse("minecraft:block/glass");
    private static final Identifier FULL_FILE_ID =
            Identifier.parse(
                    "minecraft:fusion/model_modifiers/blocks/block/glass.json");

    @Test
    void sharedDocumentLocationNormalizesStrippedModelIdToFullFileId() {
        try {
            Class<?> type = Class.forName(SHARED_TYPE);
            Method resourceId =
                    type.getMethod(
                            "resourceId",
                            Identifier.class);
            Object normalized = resourceId.invoke(
                    null,
                    STRIPPED_MODEL_ID);
            assertEquals(
                    FULL_FILE_ID,
                    normalized,
                    "stripped model id must normalize to the full "
                            + "Fusion modifier file id");
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(
                    "shared FusionModifierDocumentLocation is missing: "
                            + "Fabric stripped-model-id lookups currently miss the "
                            + "full-file-id accepted-document keys (H2)",
                    exception);
        }
    }
}
