package com.kltyton.autoseamblend.authoring.property;

import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringFile;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringDraft;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringProject;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringProjectDrafts;
import com.kltyton.autoseamblend.authoring.selector.MinecraftNativeBlockSelectorResolver;
import com.kltyton.autoseamblend.authoring.selector.NativeBlockSelectorResolver;
import com.kltyton.autoseamblend.authoring.storage.ManagedPackLayout;
import com.kltyton.autoseamblend.authoring.storage.ManagedPathPolicy;
import com.kltyton.autoseamblend.authoring.template.ManagedAuthoringTemplates;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.reload.rule.ManagedRule;
import com.kltyton.autoseamblend.reload.rule.ManagedRuleRuntime;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * 中文：Loader 中立属性文档加载边界；只负责原生资源 I/O、注册表选择器解析和 Minecraft 类型
 * 转换。CTM Mod 等 Loader 独占家族的 principal/companion/parse 通过注册的扩展接入。
 *
 * English: Loader-neutral native-property loading boundary responsible only for
 * native resource I/O, registry-backed selector resolution, and Minecraft
 * conversion. Loader-exclusive families such as CTM Mod join through a
 * registered extension.
 */
public final class NativePropertyDocumentLoader {
    private static final ConcurrentMap<
                    EngineFamily,
                    FamilyExtension>
            EXTENSIONS = new ConcurrentHashMap<>();

    private final com.kltyton.autoseamblend.authoring.property.NativePropertyDocument delegate;

    private NativePropertyDocumentLoader(
            com.kltyton.autoseamblend.authoring.property.NativePropertyDocument delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    /**
     * 中文：注册 Loader 独占格式家族（如 NeoForge 的 CTM Mod）的属性文档加载扩展。
     *
     * English: Registers a property-document loading extension for a
     * Loader-exclusive format family such as CTM Mod on NeoForge.
     */
    public static void registerFamily(
            EngineFamily family,
            FamilyExtension extension) {
        EXTENSIONS.put(
                Objects.requireNonNull(family, "family"),
                Objects.requireNonNull(extension, "extension"));
    }

    public static NativePropertyDocumentLoader load(
            Minecraft minecraft,
            EngineFamily family,
            ManagedAuthoringDraft draft) throws IOException {
        return load(minecraft, family, draft, "");
    }

    public static NativePropertyDocumentLoader load(
            Minecraft minecraft,
            EngineFamily family,
            ManagedAuthoringDraft draft,
            String preferredSourcePath) throws IOException {
        Objects.requireNonNull(minecraft, "minecraft");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(preferredSourcePath, "preferredSourcePath");
        ManagedAuthoringProject generated = ManagedAuthoringTemplates.create(
                family,
                List.of(ManagedAuthoringProjectDrafts.createRule(draft)));
        FamilyExtension extension = EXTENSIONS.get(family);
        ManagedAuthoringFile principal = extension != null
                ? extension.principal(generated.documents())
                : NativePropertyCompanionCollector.principal(
                        family,
                        generated.documents());
        String sourcePath = preferredSourcePath.isBlank()
                ? ManagedRuleRuntime.current()
                        .rule(family, draft.targetBlockId())
                        .map(ManagedRule::documentPath)
                        .orElse(principal.relativePath())
                : safePath(preferredSourcePath, "preferredSourcePath");
        Optional<byte[]> managedSource = readManaged(minecraft, sourcePath);
        byte[] source = managedSource.isPresent()
                ? managedSource.orElseThrow()
                : readResource(minecraft, sourcePath).orElse(principal.content());
        Map<String, byte[]> companions = extension != null
                ? extension.collect(
                        sourcePath,
                        source,
                        generated.documents(),
                        principal.relativePath(),
                        path -> readDocument(minecraft, path))
                : NativePropertyCompanionCollector.collect(
                        family,
                        sourcePath,
                        source,
                        generated.documents(),
                        principal.relativePath(),
                        path -> readDocument(minecraft, path));
        return parse(
                family,
                Optional.of(draft.targetBlockId()),
                principal.relativePath(),
                sourcePath,
                source,
                companions,
                draft.requestedMethod(),
                draft.compatibility());
    }

    /**
     * 中文：无接收方条目直接读取原生主文档，不生成模板或从显示身份推导接收方。
     * English: Loads a targetless native principal without generating a
     * template or deriving a receiver from its display identity.
     */
    public static NativePropertyDocumentLoader loadTargetless(
            Minecraft minecraft,
            EngineFamily family,
            String documentPath) throws IOException {
        Objects.requireNonNull(minecraft, "minecraft");
        Objects.requireNonNull(family, "family");
        String sourcePath = safePath(documentPath, "documentPath");
        Optional<byte[]> managedSource = readManaged(minecraft, sourcePath);
        byte[] source = managedSource.isPresent()
                ? managedSource.orElseThrow()
                : readResource(minecraft, sourcePath)
                        .orElseThrow(() -> new IOException(
                                "NATIVE_PROPERTY_DOCUMENT_MISSING:" + sourcePath));
        Map<String, byte[]> companions = NativePropertyCompanionCollector.collect(
                family,
                sourcePath,
                source,
                List.of(),
                sourcePath,
                path -> readDocument(minecraft, path));
        return parse(
                family,
                Optional.empty(),
                sourcePath,
                sourcePath,
                source,
                companions,
                ConnectionMethod.NONE,
                false);
    }

    private static NativePropertyDocumentLoader parse(
            EngineFamily family,
            Optional<String> targetBlockId,
            String documentPath,
            String sourceDocumentPath,
            byte[] sourceDocument,
            Map<String, byte[]> companionDocuments,
            ConnectionMethod fallbackMethod,
            boolean fallbackCompatibility) throws IOException {
        FamilyExtension extension = EXTENSIONS.get(family);
        com.kltyton.autoseamblend.authoring.property.NativePropertyDocument parsed =
                extension != null
                        ? extension.parse(
                                targetBlockId,
                                documentPath,
                                sourceDocumentPath,
                                sourceDocument,
                                companionDocuments,
                                fallbackMethod,
                                fallbackCompatibility,
                                MinecraftNativeBlockSelectorResolver.ALL_STATES)
                        : com.kltyton.autoseamblend.authoring.property.NativePropertyDocument.parse(
                        family,
                        targetBlockId,
                        documentPath,
                        sourceDocumentPath,
                        sourceDocument,
                        companionDocuments,
                        fallbackMethod,
                        fallbackCompatibility,
                        MinecraftNativeBlockSelectorResolver.ALL_STATES);
        return new NativePropertyDocumentLoader(parsed);
    }

    private static Optional<byte[]> readManaged(
            Minecraft minecraft,
            String relative) throws IOException {
        ManagedPackLayout layout = ManagedPackLayout.current(minecraft);
        Path resolved = ManagedPathPolicy.resolveContained(
                layout.resourcePacksRoot(),
                layout.root(),
                relative);
        return Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS)
                ? Optional.of(Files.readAllBytes(resolved))
                : Optional.empty();
    }

    private static Optional<byte[]> readResource(
            Minecraft minecraft,
            String relative) throws IOException {
        String[] parts = relative.split("/", 3);
        if (parts.length != 3 || !parts[0].equals("assets")) {
            return Optional.empty();
        }
        ResourceLocation resourceId;
        try {
            resourceId = ResourceLocation.fromNamespaceAndPath(parts[1], parts[2]);
        } catch (RuntimeException exception) {
            throw new IOException("NATIVE_PROPERTY_RESOURCE_PATH_INVALID", exception);
        }
        var resource = minecraft.getResourceManager().getResource(resourceId);
        if (resource.isEmpty()) {
            return Optional.empty();
        }
        try (var input = resource.orElseThrow().open()) {
            return Optional.of(input.readAllBytes());
        }
    }

    private static Optional<byte[]> readDocument(
            Minecraft minecraft,
            String path) throws IOException {
        Optional<byte[]> managed = readManaged(minecraft, path);
        return managed.isPresent() ? managed : readResource(minecraft, path);
    }

    private static String safePath(String value, String label) {
        if (value == null
                || value.isBlank()
                || value.indexOf('\\') >= 0
                || value.startsWith("/")
                || value.contains("../")
                || value.contains("/..")) {
            throw new IllegalArgumentException(label + " is not a safe relative path");
        }
        return value;
    }

    /**
     * 中文：向 Common 工作台暴露不可变文档；此适配器不再复制状态或编辑语义。
     * English: Exposes the immutable Common document to the workbench; this
     * adapter no longer duplicates state or editing semantics.
     */
    public com.kltyton.autoseamblend.authoring.property.NativePropertyDocument document() {
        return delegate;
    }

    /**
     * 中文：在保持同一原生来源身份时替换不可变 Common 文档。
     * English: Replaces the immutable Common document while preserving the
     * same native source identity.
     */
    public NativePropertyDocumentLoader withDocument(
            com.kltyton.autoseamblend.authoring.property.NativePropertyDocument value) {
        com.kltyton.autoseamblend.authoring.property.NativePropertyDocument validated =
                Objects.requireNonNull(value, "value");
        if (delegate.family() != validated.family()
                || !delegate.documentPath().equals(validated.documentPath())
                || !delegate.sourceDocumentPath().equals(validated.sourceDocumentPath())
                || !Arrays.equals(delegate.sourceDocument(), validated.sourceDocument())
                || !documentsEqual(
                        delegate.companionDocuments(),
                        validated.companionDocuments())) {
            throw new IllegalArgumentException("native property source identity changed");
        }
        return validated == delegate ? this : new NativePropertyDocumentLoader(validated);
    }

    private static boolean documentsEqual(
            Map<String, byte[]> left,
            Map<String, byte[]> right) {
        return left.size() == right.size()
                && left.entrySet().stream().allMatch(entry ->
                        right.containsKey(entry.getKey())
                                && Arrays.equals(
                                        entry.getValue(),
                                        right.get(entry.getKey())));
    }

    public EngineFamily family() { return delegate.family(); }
    public String documentPath() { return delegate.documentPath(); }
    public String sourceDocumentPath() { return delegate.sourceDocumentPath(); }
    public byte[] sourceDocument() { return delegate.sourceDocument(); }

    /**
     * 中文：Loader 独占格式家族的主文档选择、伴随收集与解析契约。
     *
     * English: Principal selection, companion collection, and parsing contract
     * for a Loader-exclusive format family.
     */
    public interface FamilyExtension {
        ManagedAuthoringFile principal(
                List<ManagedAuthoringFile> documents);

        Map<String, byte[]> collect(
                String sourcePath,
                byte[] source,
                List<ManagedAuthoringFile> templateDocuments,
                String templatePrincipalPath,
                NativePropertyCompanionCollector.DocumentReader reader)
                throws IOException;

        com.kltyton.autoseamblend.authoring.property.NativePropertyDocument parse(
                Optional<String> targetBlockId,
                String documentPath,
                String sourceDocumentPath,
                byte[] sourceDocument,
                Map<String, byte[]> companionDocuments,
                ConnectionMethod fallbackMethod,
                boolean fallbackCompatibility,
                NativeBlockSelectorResolver selectorResolver)
                throws IOException;
    }
}
