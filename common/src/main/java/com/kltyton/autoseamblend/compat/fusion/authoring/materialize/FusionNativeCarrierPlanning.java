package com.kltyton.autoseamblend.compat.fusion.authoring.materialize;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringDraft;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringProjectDrafts;
import com.kltyton.autoseamblend.authoring.materialize.TextureSourceSnapshot;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * 中文：保存 Fusion 共享载体的帧、尺寸、路径和扩展元数据规则；资源读取仍由 Loader 适配器负责。
 *
 * English: Owns Fusion shared-carrier frame, size, path, and extension-metadata rules while
 * resource reads remain in the Loader adapter.
 */
public final class FusionNativeCarrierPlanning {
    private FusionNativeCarrierPlanning() {
    }

    /**
     * 中文：FULL 的逻辑 8x6 表在原生 8x8 帧中保留两行 padding。
     * English: Fusion FULL keeps two padding rows in its native 8x8 frame for an 8x6 logical sheet.
     */
    public static int carrierRows(FusionNativeEvidenceLayout layout) {
        Objects.requireNonNull(layout, "layout");
        return layout.columns() == 8 && layout.rows() == 6
                ? 8
                : layout.rows();
    }

    /**
     * 中文：按是否保留动画元数据选择 FULL 载体行数；动画 FULL 需要 6 行以通过 Fusion
     * 1.3.12 的 6:8 宽高比校验，其他布局不受动画标志影响。
     *
     * English: Selects FULL carrier rows by animation-metadata retention; animated FULL needs
     * 6 rows to pass Fusion 1.3.12's 6:8 aspect-ratio check while other layouts ignore the
     * animation flag.
     */
    public static int carrierRows(
            FusionNativeEvidenceLayout layout,
            boolean animated) {
        Objects.requireNonNull(layout, "layout");
        if (animated
                && layout.columns() == 8
                && layout.rows() == 6) {
            return 6;
        }
        return carrierRows(layout);
    }

    public static Optional<CellSize> cellSize(
            TextureSourceSnapshot source,
            FusionNativeEvidenceLayout layout) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(layout, "layout");
        if (source.frameWidth() % layout.columns() != 0) {
            return Optional.empty();
        }
        int width = source.frameWidth() / layout.columns();
        if (layout.columns() == 8
                && layout.rows() == 6
                && source.frameWidth() == source.frameHeight()) {
            return Optional.of(new CellSize(width, width));
        }
        if (source.frameHeight() % layout.rows() != 0) {
            return Optional.empty();
        }
        return Optional.of(new CellSize(
                width,
                source.frameHeight() / layout.rows()));
    }

    /**
     * 中文：Fusion 1.3.12 的 createTexture 在读取动画元数据帧尺寸之前，先用整张 PNG 的
     * 宽高判断 FULL+animated，整图方形即拒绝；当动画 FULL 载体整图恰好为方形时，在底部
     * 追加一行不参与播放的空动画帧，使整图非方形且不改变原帧索引。其他情况返回同一实例。
     *
     * English: Fusion 1.3.12 createTexture validates FULL+animated against the whole PNG
     * width/height before reading the animation frame size and rejects square sheets; when an
     * animated FULL carrier is exactly square, one empty animation-frame row is appended at the
     * bottom so the sheet is no longer square while original frame indices stay valid. Any other
     * carrier is returned as the same instance.
     */
    public static TextureSourceSnapshot avoidAnimatedFullSquareSheet(
            TextureSourceSnapshot carrier,
            FusionNativeEvidenceLayout layout) {
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(layout, "layout");
        if (carrier.animated()
                && layout.columns() == 8
                && layout.rows() == 6
                && carrier.sheetWidth() == carrier.sheetHeight()) {
            // 中文：整图方形才会被 Fusion 1.3.12 拒绝；只在这种情况下追加一个空帧行。
            // English: Fusion 1.3.12 rejects only whole-image-square sheets; append one empty
            // frame row only in that case.
            return carrier.appendEmptyFrameRows(1);
        }
        return carrier;
    }

    /**
     * 中文：按 Fusion 共享表元数据重新解释同一整图的动画帧边界。
     * English: Reinterprets animation-frame bounds over the same full-sheet pixels using Fusion
     * shared-sheet metadata.
     */
    public static TextureSourceSnapshot normalizeFrames(
            TextureSourceSnapshot source,
            FusionNativeEvidenceLayout layout) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(layout, "layout");
        JsonObject root = parseObject(source.sourceMetadata());
        if (!(root.get("animation") instanceof JsonObject animation)) {
            return source;
        }
        Integer width = integer(animation.get("width"));
        Integer height = integer(animation.get("height"));
        int frameWidth;
        int frameHeight;
        if (width == null && height == null) {
            int tileSize = Math.min(
                    source.sheetWidth() / layout.columns(),
                    source.sheetHeight() / layout.rows());
            frameWidth = Math.multiplyExact(layout.columns(), tileSize);
            frameHeight = Math.multiplyExact(layout.rows(), tileSize);
        } else {
            frameWidth = width == null ? source.sheetWidth() : width;
            frameHeight = height == null ? source.sheetHeight() : height;
        }
        int frameCount = Math.multiplyExact(
                source.sheetWidth() / frameWidth,
                source.sheetHeight() / frameHeight);
        LinkedHashSet<Integer> frames = new LinkedHashSet<>();
        if (animation.get("frames") instanceof com.google.gson.JsonArray entries) {
            for (JsonElement entry : entries) {
                Integer index = entry instanceof JsonObject object
                        ? integer(object.get("index"))
                        : integer(entry);
                if (index == null) {
                    throw new IllegalArgumentException(
                            "FUSION_ANIMATION_FRAME_INVALID");
                }
                frames.add(index);
            }
        }
        if (frames.isEmpty()) {
            for (int frame = 0; frame < frameCount; frame++) {
                frames.add(frame);
            }
        }
        return source.withFrameLayout(
                frameWidth,
                frameHeight,
                true,
                frames.stream().mapToInt(Integer::intValue).toArray());
    }

    public static byte[] generatedBaseMetadata(
            ConnectionMethod requestedMethod,
            boolean compatibility,
            byte[] sourceMetadata) {
        Objects.requireNonNull(requestedMethod, "requestedMethod");
        JsonObject root = parseObject(sourceMetadata);
        JsonObject fusion = new JsonObject();
        fusion.addProperty("type", "base");
        fusion.addProperty("method", requestedMethod.serializedName());
        fusion.addProperty("compatibility", compatibility);
        root.add("fusion", fusion);
        return jsonBytes(root);
    }

    public static byte[] generatedConnectingMetadata(
            ConnectionMethod requestedMethod,
            boolean compatibility,
            String layout,
            byte[] sourceMetadata,
            int frameWidth,
            int frameHeight,
            boolean animated,
            int[] frameIndices,
            boolean excludePadding) {
        Objects.requireNonNull(requestedMethod, "requestedMethod");
        Objects.requireNonNull(layout, "layout");
        JsonObject root = parseObject(sourceMetadata);
        root.remove("fusion");
        JsonObject fusion = new JsonObject();
        fusion.addProperty("type", "connecting");
        fusion.addProperty("layout", layout);
        JsonObject connections = new JsonObject();
        connections.addProperty("type", "is_same_block");
        fusion.add("connections", connections);
        fusion.addProperty("method", requestedMethod.serializedName());
        fusion.addProperty("compatibility", compatibility);
        root.add("fusion", fusion);
        if (animated && root.get("animation") instanceof JsonObject animation) {
            animation.addProperty("width", frameWidth);
            animation.addProperty("height", frameHeight);
            if (excludePadding) {
                // 中文：底部空帧行不在原 frameIndices 内；若原动画元数据没有 frames 数组，
                // 必须显式写出原帧索引，否则播放器会按整表逐帧播放空帧；已有 frames 数组
                // （可能含逐帧 time 对象）必须原样保留，不得覆盖时长。
                // English: The bottom empty frame row is outside the original frameIndices;
                // when the source animation metadata has no frames array the original indices
                // must be written explicitly or the player would animate over the empty frame;
                // an existing frames array (possibly with per-frame time objects) is kept
                // as-is and durations are never overwritten.
                if (!animation.has("frames")) {
                    JsonArray explicit = new JsonArray();
                    for (int index : Objects.requireNonNull(
                            frameIndices,
                            "frameIndices")) {
                        explicit.add(index);
                    }
                    animation.add("frames", explicit);
                }
            }
        }
        return jsonBytes(root);
    }

    public static Optional<ResourceLocation> resourceId(String path) {
        Objects.requireNonNull(path, "path");
        String[] segments = path.split("/", 3);
        if (segments.length != 3 || !"assets".equals(segments[0])) {
            return Optional.empty();
        }
        return Optional.of(new ResourceLocation(
                segments[1],
                segments[2]));
    }

    public static String generatedPath(ManagedAuthoringDraft draft) {
        Objects.requireNonNull(draft, "draft");
        return "assets/autoseamblend/textures/generated/"
                + draft.resolvedMethod().serializedName()
                + '/'
                + ManagedAuthoringProjectDrafts.createRule(draft).managedStem()
                + "/sheet.png";
    }

    public static Optional<ResourceLocation> textureId(
            String reference,
            String defaultNamespace) {
        if (reference == null
                || reference.isBlank()
                || reference.startsWith("#")) {
            return Optional.empty();
        }
        Objects.requireNonNull(defaultNamespace, "defaultNamespace");
        String normalized = reference.trim();
        if (normalized.startsWith("textures/")) {
            normalized = normalized.substring("textures/".length());
        }
        if (normalized.endsWith(".png")) {
            normalized = normalized.substring(
                    0,
                    normalized.length() - ".png".length());
        }
        ResourceLocation parsed = normalized.indexOf(':') >= 0
                ? ResourceLocation.tryParse(normalized)
                : ResourceLocation.tryParse(defaultNamespace + ':' + normalized);
        return Optional.ofNullable(parsed);
    }

    private static JsonObject parseObject(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        try {
            JsonElement parsed = JsonParser.parseString(
                    new String(bytes, StandardCharsets.UTF_8));
            return parsed instanceof JsonObject object
                    ? object.deepCopy()
                    : new JsonObject();
        } catch (RuntimeException exception) {
            return new JsonObject();
        }
    }

    private static byte[] jsonBytes(JsonObject root) {
        return (root.toString() + '\n').getBytes(StandardCharsets.UTF_8);
    }

    private static Integer integer(JsonElement value) {
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        try {
            return value.getAsInt();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public record CellSize(int width, int height) {
        public CellSize {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException(
                        "Fusion carrier cell size must be positive");
            }
        }
    }
}
