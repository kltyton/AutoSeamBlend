package com.kltyton.autoseamblend.authoring.export;

import com.kltyton.autoseamblend.authoring.materialize.TextureSourceSnapshot;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringRule;
import com.kltyton.autoseamblend.export.managed.ManagedExportIr;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：引擎导出提供器使用的不可变纯数据捕获；支持有接收方表面和无接收方原生透传。
 *
 * English:
 * Immutable project-data capture consumed by an engine export provider,
 * supporting both receiver surfaces and targetless native passthrough.
 */
public final class ExportDraft {
    private final ManagedAuthoringRule rule;
    private final ExportSurfaceSnapshot surface;
    private final TextureSourceSnapshot source;
    private final Optional<TextureSourceSnapshot>
            topSource;
    private final Optional<NativeDocumentSnapshot>
            nativeDocument;
    private final long ruleGeneration;
    private final long surfaceGeneration;

    public ExportDraft(
            ManagedAuthoringRule rule,
            ExportSurfaceSnapshot surface,
            TextureSourceSnapshot source,
            Optional<TextureSourceSnapshot>
                    topSource,
            Optional<NativeDocumentSnapshot>
                    nativeDocument,
            long ruleGeneration,
            long surfaceGeneration) {
        this.rule = Objects.requireNonNull(
                rule,
                "rule");
        this.surface = Objects.requireNonNull(
                surface,
                "surface");
        this.source = Objects.requireNonNull(
                source,
                "source");
        this.topSource = Objects.requireNonNull(
                topSource,
                "topSource");
        this.nativeDocument = Objects.requireNonNull(
                nativeDocument,
                "nativeDocument");
        requireGenerations(
                ruleGeneration,
                surfaceGeneration);
        this.ruleGeneration = ruleGeneration;
        this.surfaceGeneration = surfaceGeneration;
        String renderedTexture = surface.textureId();
        if (!renderedTexture.equals(
                        rule.sourceTextureId())
                || !renderedTexture.equals(
                        source.sourceTextureId())) {
            throw new IllegalArgumentException(
                    "EXPORT_SOURCE_TEXTURE_CHANGED");
        }
    }

    public ExportDraft(
            ManagedAuthoringRule rule,
            ExportSurfaceSnapshot surface,
            TextureSourceSnapshot source,
            Optional<TextureSourceSnapshot>
                    topSource,
            long ruleGeneration,
            long surfaceGeneration) {
        this(
                rule,
                surface,
                source,
                topSource,
                Optional.empty(),
                ruleGeneration,
                surfaceGeneration);
    }

    private ExportDraft(
            NativeDocumentSnapshot document,
            long ruleGeneration,
            long surfaceGeneration) {
        this.rule = null;
        this.surface = null;
        this.source = null;
        this.topSource = Optional.empty();
        this.nativeDocument = Optional.of(
                Objects.requireNonNull(
                        document,
                        "document"));
        requireGenerations(
                ruleGeneration,
                surfaceGeneration);
        this.ruleGeneration = ruleGeneration;
        this.surfaceGeneration = surfaceGeneration;
    }

    public static ExportDraft targetless(
            NativeDocumentSnapshot document,
            long ruleGeneration,
            long surfaceGeneration) {
        return new ExportDraft(
                document,
                ruleGeneration,
                surfaceGeneration);
    }

    public boolean targetless() {
        return rule == null;
    }

    public ManagedAuthoringRule rule() {
        requireSurfaceDraft();
        return rule;
    }

    public ExportSurfaceSnapshot surface() {
        requireSurfaceDraft();
        return surface;
    }

    public TextureSourceSnapshot source() {
        requireSurfaceDraft();
        return source;
    }

    public Optional<TextureSourceSnapshot>
            topSource() {
        return topSource;
    }

    public Optional<NativeDocumentSnapshot>
            nativeDocument() {
        return nativeDocument;
    }

    public long ruleGeneration() {
        return ruleGeneration;
    }

    public long surfaceGeneration() {
        return surfaceGeneration;
    }

    /**
     * 中文：把无接收方原生文档作为文档身份透传，不创建虚假的方块目标或纹理表面。
     *
     * English:
     * Passes a targetless native document through under its document identity
     * without inventing a block target or texture surface.
     */
    public ManagedExportIr.Rule targetlessRule(
            int order) throws IOException {
        return targetlessRule(order, NativeDocumentBaker::bakedPassthrough);
    }

    /**
     * 中文：允许 Loader 独占格式提供自己的 baked 主文档变换。
     * English: Allows a Loader-exclusive format to provide its own baked principal transform.
     */
    public ManagedExportIr.Rule targetlessRule(
            int order,
            PrincipalBakeTransform principalBakeTransform) throws IOException {
        if (!targetless()) {
            throw new IllegalStateException(
                    "surface export draft is not targetless");
        }
        NativeDocumentSnapshot document =
                nativeDocument.orElseThrow();
        boolean unresolvedAuto =
                document.authoringMethod()
                        == ConnectionMethod.AUTO;
        ArrayList<ManagedExportIr.Document> documents =
                new ArrayList<>();
        documents.add(unresolvedAuto
                ? new ManagedExportIr.Document(
                        document.documentPath(),
                        document.resolve(),
                        null,
                        null)
                : new ManagedExportIr.Document(
                        document.documentPath(),
                        document.resolve(),
                        document.documentPath(),
                        principalBakeTransform.bake(document)));
        for (Map.Entry<String, byte[]> entry
                : document.companionDocuments()
                        .entrySet()) {
            String path = entry.getKey();
            byte[] bytes = entry.getValue();
            documents.add(
                    unresolvedAuto
                            ? new ManagedExportIr.Document(
                                    path,
                                    bytes,
                                    null,
                                    null)
                            : new ManagedExportIr.Document(
                                    path,
                                    bytes,
                                    path,
                                    NativeDocumentBaker.bakedCompanion(
                                            document,
                                            path,
                                            bytes)));
        }
        String method = document.authoringMethod()
                .serializedName();
        return new ManagedExportIr.Rule(
                order,
                document.displayIdentity(),
                document.documentPath(),
                documents,
                method,
                method,
                List.of(
                        "native-targetless-passthrough"),
                List.of(),
                Map.of(),
                List.of(),
                unresolvedAuto
                        ? List.of(
                                "TARGETLESS_AUTO_UNRESOLVED")
                        : List.of(),
                List.of());
    }

    /** 中文：无 I/O 的原生主文档 baked 变换边界。 / English: I/O-free native principal baked-transform boundary. */
    @FunctionalInterface
    public interface PrincipalBakeTransform {
        byte[] bake(NativeDocumentSnapshot document) throws IOException;
    }

    public String managedGenerationHash() {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");
            if (targetless()) {
                update(digest, "targetless");
            } else {
                update(digest, rule.targetBlockId());
                update(digest, rule.sourceTextureId());
                update(digest, rule.originalModelId());
                update(
                        digest,
                        rule.requestedMethod()
                                .serializedName());
                update(
                        digest,
                        rule.resolvedMethod()
                                .serializedName());
                update(
                        digest,
                        Boolean.toString(
                                rule.compatibility()));
                update(
                        digest,
                        Boolean.toString(rule.pane()));
                update(digest, surface.textureId());
                update(
                        digest,
                        Float.toHexString(
                                surface.frameProfile()
                                        .left()));
                update(
                        digest,
                        Float.toHexString(
                                surface.frameProfile()
                                        .down()));
                update(
                        digest,
                        Float.toHexString(
                                surface.frameProfile()
                                        .right()));
                update(
                        digest,
                        Float.toHexString(
                                surface.frameProfile()
                                        .up()));
                for (int slot = 0;
                        slot < 17;
                        slot++) {
                    for (int y = 0;
                            y < 16;
                            y++) {
                        update(
                                digest,
                                Integer.toHexString(
                                        surface.overlayProfile()
                                                .rowBits(
                                                        1 << slot,
                                                        y)));
                    }
                }
                update(
                        digest,
                        topSource
                                .map(TextureSourceSnapshot
                                        ::sourceTextureId)
                                .orElse(""));
                rule.sourceTextureKeys().forEach(
                        value -> update(digest, value));
            }
            nativeDocument.ifPresent(document ->
                    update(digest, document));
            update(
                    digest,
                    Long.toString(ruleGeneration));
            update(
                    digest,
                    Long.toString(surfaceGeneration));
            return HexFormat.of()
                    .formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 unavailable",
                    exception);
        }
    }

    private static void update(
            MessageDigest digest,
            NativeDocumentSnapshot document) {
        update(
                digest,
                document.family().formatId());
        update(digest, document.documentPath());
        update(digest, document.displayIdentity());
        update(
                digest,
                document.authoringMethod()
                        .serializedName());
        update(
                digest,
                Boolean.toString(
                        document.authoringCompatibility()));
        update(digest, document.principalDocument());
        document.companionDocuments()
                .forEach((path, bytes) -> {
                    update(digest, path);
                    update(digest, bytes);
                });
        document.propertyPatch()
                .ifPresent(patch ->
                        patch.values()
                                .forEach((key, value) -> {
                                    update(digest, key);
                                    update(
                                            digest,
                                            value.orElse(
                                                    "<absent>"));
                                }));
    }

    private void requireSurfaceDraft() {
        if (targetless()) {
            throw new IllegalStateException(
                    "targetless export draft has no surface rule");
        }
    }

    private static void requireGenerations(
            long ruleGeneration,
            long surfaceGeneration) {
        if (ruleGeneration < 0
                || surfaceGeneration < 0) {
            throw new IllegalArgumentException(
                    "export generations must be non-negative");
        }
    }

    private static void update(
            MessageDigest digest,
            String value) {
        digest.update(value.getBytes(
                StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static void update(
            MessageDigest digest,
            byte[] value) {
        digest.update(value);
        digest.update((byte) 0);
    }
}
