package com.kltyton.autoseamblend.authoring.materialize;

import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringDraft;
import com.kltyton.autoseamblend.authoring.property.NativePropertyDocumentLoader;
import com.kltyton.autoseamblend.authoring.template.ManagedAuthoringTemplates;
import com.kltyton.autoseamblend.engine.EngineFamily;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.Minecraft;

/**
 * 中文：按已安装引擎隔离的连接纹理编辑来源注册表；公共 UILib 前端不会链接第三方引擎类型。
 *
 * English:
 * Registry of installed-engine-specific connection-texture editing sources.
 * The shared UILib frontend never links third-party engine types.
 */
public final class ConnectionTextureSources {
    private static final ConnectionTextureProviderRegistry<Provider>
            PROVIDERS = new ConnectionTextureProviderRegistry<>();

    private ConnectionTextureSources() {}

    public static void register(
            Provider provider) {
        Objects.requireNonNull(provider, "provider");
        PROVIDERS.register(
                provider.family(),
                provider);
    }

    /** 中文：只检查指定格式的中立绘画来源是否已注册。 / English: Checks registration of the neutral painting source for one format. */
    public static boolean available(
            EngineFamily family) {
        return PROVIDERS.available(family);
    }

    public static Optional<ConnectionTextureSet> capture(
            Minecraft minecraft,
            EngineFamily family,
            ManagedAuthoringDraft draft,
            NativePropertyDocumentLoader document)
            throws IOException {
        Objects.requireNonNull(minecraft, "minecraft");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(document, "document");
        Provider provider = PROVIDERS.find(family).orElse(null);
        return provider == null
                ? Optional.empty()
                : Optional.of(provider.capture(
                        minecraft,
                        draft,
                        document));
    }

    /**
     * 中文：仅当来源路径和字节与当前 Draft 生成的 AutoSeamBlend 主模板完全一致时，证明它是可合成的受管 authoring 占位文档。
     *
     * English: Proves a managed authoring placeholder only when both source
     * path and bytes exactly match the AutoSeamBlend principal template for
     * the current draft.
     */
    public static boolean managedAuthoringTemplate(
            ManagedAuthoringDraft draft,
            NativePropertyDocumentLoader document) {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(document, "document");
        return ConnectionTextureDraftContext.managedAuthoringTemplate(
                draft,
                document.family(),
                document.sourceDocumentPath(),
                document.sourceDocument(),
                ManagedAuthoringTemplates::create);
    }

    public interface Provider {
        EngineFamily family();

        ConnectionTextureSet capture(
                Minecraft minecraft,
                ManagedAuthoringDraft draft,
                NativePropertyDocumentLoader document)
                throws IOException;
    }
}
