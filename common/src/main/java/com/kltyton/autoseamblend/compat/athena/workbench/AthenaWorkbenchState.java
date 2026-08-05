package com.kltyton.autoseamblend.compat.athena.workbench;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 中文：在不可变 Athena 工作台草稿中编码原生 JSON、物理槽像素和撤销历史。
 * English: Encodes native JSON, physical-slot pixels, and undo history in immutable Athena
 * workbench drafts.
 *
 * <p>This codec is independent of UILib, Minecraft, and either Loader.</p>
 */
public final class AthenaWorkbenchState {
    public static final String DOCUMENT_PATH = "document.path";
    public static final String DOCUMENT_SOURCE = "document.source";
    public static final String CAPTURED_SOURCE = "document.captured";
    public static final String PROPERTY_PREFIX = "property.";
    public static final String SLOT_PREFIX = "paint.slot.";
    public static final String DIRTY_PREFIX = "paint.dirty.";
    public static final String UNDO = "paint.undo";
    public static final String REDO = "paint.redo";
    private static final int HISTORY_LIMIT = 32;

    private AthenaWorkbenchState() {}

    /** 中文：深复制字节状态，防止会话修订互相污染。 / English: Deep-copies byte state so revisions cannot alias one another. */
    public static LinkedHashMap<String, byte[]> copy(Map<String, byte[]> source) {
        LinkedHashMap<String, byte[]> result = new LinkedHashMap<>();
        Objects.requireNonNull(source, "source").forEach((key, value) ->
                result.put(Objects.requireNonNull(key, "state key"),
                        Objects.requireNonNull(value, "state value").clone()));
        return result;
    }

    public static void putText(Map<String, byte[]> state, String key, String value) {
        state.put(Objects.requireNonNull(key, "key"),
                Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8));
    }

    public static String text(Map<String, byte[]> state, String key) {
        byte[] value = Objects.requireNonNull(state, "state").get(key);
        if (value == null) {
            throw new IllegalStateException("ATHENA_WORKBENCH_STATE_MISSING:" + key);
        }
        return new String(value, StandardCharsets.UTF_8);
    }

    public static void putPixels(Map<String, byte[]> state, int slot, int[] pixels) {
        state.put(SLOT_PREFIX + slot, encodePixels(pixels));
    }

    public static int[] pixels(Map<String, byte[]> state, int slot) {
        byte[] value = Objects.requireNonNull(state, "state").get(SLOT_PREFIX + slot);
        if (value == null) {
            throw new IllegalStateException("ATHENA_WORKBENCH_SLOT_MISSING:" + slot);
        }
        return decodePixels(value);
    }

    public static void markDirty(Map<String, byte[]> state, int slot) {
        state.put(DIRTY_PREFIX + slot, new byte[] {1});
    }

    public static boolean dirty(Map<String, byte[]> state, int slot) {
        return Objects.requireNonNull(state, "state").containsKey(DIRTY_PREFIX + slot);
    }

    public static List<Snapshot> history(Map<String, byte[]> state, String key) {
        byte[] bytes = Objects.requireNonNull(state, "state").get(key);
        return bytes == null ? List.of() : decodeHistory(bytes);
    }

    public static void putHistory(Map<String, byte[]> state, String key, List<Snapshot> history) {
        List<Snapshot> frozen = trim(Objects.requireNonNull(history, "history"));
        if (frozen.isEmpty()) {
            state.remove(key);
        } else {
            state.put(key, encodeHistory(frozen));
        }
    }

    public static List<Snapshot> push(List<Snapshot> history, Snapshot snapshot) {
        ArrayList<Snapshot> next = new ArrayList<>(Objects.requireNonNull(history, "history"));
        next.add(Objects.requireNonNull(snapshot, "snapshot"));
        return trim(next);
    }

    private static List<Snapshot> trim(List<Snapshot> history) {
        int start = Math.max(0, history.size() - HISTORY_LIMIT);
        return List.copyOf(history.subList(start, history.size()));
    }

    private static byte[] encodePixels(int[] pixels) {
        Objects.requireNonNull(pixels, "pixels");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(pixels.length);
                for (int pixel : pixels) output.writeInt(pixel);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static int[] decodePixels(byte[] bytes) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int length = input.readInt();
            if (length < 0 || length > 16_777_216) {
                throw new IllegalStateException("ATHENA_WORKBENCH_PIXEL_STATE_INVALID");
            }
            int[] pixels = new int[length];
            for (int index = 0; index < length; index++) pixels[index] = input.readInt();
            if (input.read() != -1) {
                throw new IllegalStateException("ATHENA_WORKBENCH_PIXEL_STATE_TRAILING_DATA");
            }
            return pixels;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static byte[] encodeHistory(List<Snapshot> history) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(history.size());
                for (Snapshot snapshot : history) {
                    output.writeInt(snapshot.slot());
                    int[] pixels = snapshot.pixels();
                    output.writeInt(pixels.length);
                    for (int pixel : pixels) output.writeInt(pixel);
                }
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static List<Snapshot> decodeHistory(byte[] bytes) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int size = input.readInt();
            if (size < 0 || size > HISTORY_LIMIT) {
                throw new IllegalStateException("ATHENA_WORKBENCH_HISTORY_INVALID");
            }
            ArrayList<Snapshot> history = new ArrayList<>(size);
            for (int item = 0; item < size; item++) {
                int slot = input.readInt();
                int length = input.readInt();
                if (slot < 0 || length < 0 || length > 16_777_216) {
                    throw new IllegalStateException("ATHENA_WORKBENCH_HISTORY_INVALID");
                }
                int[] pixels = new int[length];
                for (int index = 0; index < length; index++) pixels[index] = input.readInt();
                history.add(new Snapshot(slot, pixels));
            }
            if (input.read() != -1) {
                throw new IllegalStateException("ATHENA_WORKBENCH_HISTORY_TRAILING_DATA");
            }
            return List.copyOf(history);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public record Snapshot(int slot, int[] pixels) {
        public Snapshot {
            if (slot < 0) throw new IllegalArgumentException("slot must be non-negative");
            pixels = Objects.requireNonNull(pixels, "pixels").clone();
        }

        @Override
        public int[] pixels() {
            return pixels.clone();
        }
    }
}
