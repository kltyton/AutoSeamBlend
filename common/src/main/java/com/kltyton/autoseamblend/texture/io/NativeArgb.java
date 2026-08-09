package com.kltyton.autoseamblend.texture.io;

import net.minecraft.util.FastColor;

/**
 * 中文：1.21.1 NativeImage.getPixelRGBA/setPixelRGBA 使用 ABGR 打包（内存字节按 RGBA 存储，
 * 小端读/写为 {@code A<<24|B<<16|G<<8|R}），而项目内部 IR 是直通 ARGB（{@code A<<24|R<<16|G<<8|B}）。
 * 本类是 NativeImage 与 IR 之间唯一的转换边界，与 26.1.2 getPixel/setPixel 的 ARGB 语义对齐。
 *
 * English: On 1.21.1, NativeImage.getPixelRGBA/setPixelRGBA use ABGR packing (bytes stored
 * RGBA, read/written little-endian as {@code A<<24|B<<16|G<<8|R}), while the project IR is straight
 * ARGB ({@code A<<24|R<<16|G<<8|B}). This class is the sole conversion boundary between NativeImage
 * and the IR, matching the ARGB semantics of 26.1.2's getPixel/setPixel.
 */
public final class NativeArgb {
    private NativeArgb() {}

    /**
     * 中文：NativeImage 像素（ABGR）→ 项目 IR（ARGB）。
     *
     * English: NativeImage pixel (ABGR) to project IR (ARGB).
     */
    public static int toIr(int nativeAbgr) {
        return FastColor.ARGB32.color(
                FastColor.ABGR32.alpha(nativeAbgr),
                FastColor.ABGR32.red(nativeAbgr),
                FastColor.ABGR32.green(nativeAbgr),
                FastColor.ABGR32.blue(nativeAbgr));
    }

    /**
     * 中文：项目 IR（ARGB）→ NativeImage 像素（ABGR）。
     *
     * English: Project IR (ARGB) to NativeImage pixel (ABGR).
     */
    public static int toNative(int argb) {
        return FastColor.ABGR32.fromArgb32(argb);
    }
}
