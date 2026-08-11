package com.kltyton.autoseamblend.authoring.materialize;

import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringDraft;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringFile;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringProject;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringProjectDrafts;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringRule;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/**
 * 中文：跨 Loader 的连接纹理编辑草稿上下文；原生格式模板仍由对应 Loader 注入。
 * English: Cross-Loader connected-texture draft context; each Loader still injects its native
 * format template factory.
 */
public final class ConnectionTextureDraftContext {
    private ConnectionTextureDraftContext() {}

    /**
     * 中文：冻结用于补齐缺失原生槽位的代表面与基础纹理。
     * English: Freezes the representative face and base texture used to fill missing native slots.
     */
    public static DraftInputs draftInputs(
            Minecraft minecraft,
            ManagedAuthoringDraft draft) throws IOException {
        Objects.requireNonNull(minecraft, "minecraft");
        Objects.requireNonNull(draft, "draft");
        ResourceLocation blockId = new ResourceLocation(draft.targetBlockId());
        Block block = BuiltInRegistries.BLOCK.getOptional(blockId).orElse(null);
        if (block == null) {
            throw new IOException("CONNECTION_TEXTURE_TARGET_UNAVAILABLE:" + blockId);
        }
        var representative = ReloadPublication.current().surfaces()
                .representative(block)
                .orElseThrow(() -> new IOException(
                        "CONNECTION_TEXTURE_BASE_UNAVAILABLE:" + blockId));
        return new DraftInputs(
                representative.surface(),
                TextureSourceSnapshot.capture(
                        representative.surface().sprite().contents(),
                        minecraft.getResourceManager()));
    }

    /**
     * 中文：统一所有引擎对无槽位方法的拒绝诊断。
     * English: Normalizes the rejection diagnostic for methods with no materializable slots.
     */
    public static void requireSlots(
            ConnectionMethod method,
            boolean slotsAvailable) throws IOException {
        Objects.requireNonNull(method, "method");
        if (!slotsAvailable) {
            throw new IOException(
                    "CONNECTION_TEXTURE_METHOD_HAS_NO_SLOTS:" + method.serializedName());
        }
    }

    /**
     * 中文：只有路径和字节都等于当前草稿生成的主模板时，才承认受管占位文档。
     * English: Recognizes a managed placeholder only when both path and bytes equal the principal
     * template generated for the current draft.
     */
    public static boolean managedAuthoringTemplate(
            ManagedAuthoringDraft draft,
            EngineFamily family,
            String sourceDocumentPath,
            byte[] sourceDocument,
            NativeTemplateFactory templates) {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(sourceDocumentPath, "sourceDocumentPath");
        byte[] source = Objects.requireNonNull(sourceDocument, "sourceDocument").clone();
        Objects.requireNonNull(templates, "templates");
        ManagedAuthoringRule rule = ManagedAuthoringProjectDrafts.createRule(draft);
        return templates.create(family, List.of(rule)).documents().stream()
                .filter(file -> file.relativePath().equals(sourceDocumentPath))
                .map(ManagedAuthoringFile::content)
                .anyMatch(content -> Arrays.equals(content, source));
    }

    @FunctionalInterface
    public interface NativeTemplateFactory {
        ManagedAuthoringProject create(
                EngineFamily family,
                List<ManagedAuthoringRule> rules);
    }

    /**
     * 中文：缺槽合成使用的不可变代表面输入。
     * English: Immutable representative-face input used for missing-slot synthesis.
     *
     * @param surface 中文：缺槽合成使用的代表面。 / English: Representative face for missing-slot synthesis.
     * @param source 中文：冻结的源纹理快照。 / English: Frozen source texture snapshot.
     */
    public record DraftInputs(
            FaceSurface surface,
            TextureSourceSnapshot source) {
        public DraftInputs {
            Objects.requireNonNull(surface, "surface");
            Objects.requireNonNull(source, "source");
        }
    }
}
