package com.kltyton.autoseamblend.engine.ownership.evidence;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.server.packs.resources.Resource;

/**
 * 中文：验证原生纹理载体是否能按公共槽位布局整除为有效帧；不包含 Loader 资源发现或引擎规则解析。
 *
 * English: Validates whether a native texture carrier can be divided into
 * valid frames for a common slot layout without owning Loader resource
 * discovery or engine rule parsing.
 */
public final class NativeTextureSheetValidator {
    private NativeTextureSheetValidator() {}

    public static boolean valid(
            Resource resource,
            int columns,
            int rows,
            NativeResourceSource.SheetFramePolicy policy) {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(policy, "policy");
        if (columns <= 0 || rows <= 0) {
            return false;
        }
        try (var input = resource.open();
                NativeImage image = NativeImage.read(input)) {
            int sheetWidth = image.getWidth();
            int sheetHeight = image.getHeight();
            Optional<AnimationMetadataSection> animation =
                    resource.metadata().getSection(
                            AnimationMetadataSection.TYPE);
            FrameSize frame;
            int readableHeight = sheetHeight;
            if (policy == NativeResourceSource.SheetFramePolicy.FUSION) {
                if (columns == 8
                        && rows == 6
                        && sheetWidth == sheetHeight) {
                    if (animation.isPresent()) {
                        return false;
                    }
                    readableHeight = Math.multiplyExact(
                                    sheetHeight,
                                    rows)
                            / columns;
                    frame = new FrameSize(
                            sheetWidth,
                            readableHeight);
                } else if (animation.isPresent()) {
                    AnimationMetadataSection metadata =
                            animation.orElseThrow();
                    if (metadata.frameWidth().isEmpty()
                            && metadata.frameHeight().isEmpty()) {
                        int tileSize = Math.min(
                                sheetWidth / columns,
                                sheetHeight / rows);
                        frame = new FrameSize(
                                Math.multiplyExact(columns, tileSize),
                                Math.multiplyExact(rows, tileSize));
                    } else {
                        frame = new FrameSize(
                                metadata.frameWidth().orElse(sheetWidth),
                                metadata.frameHeight().orElse(sheetHeight));
                    }
                } else {
                    frame = new FrameSize(
                            sheetWidth,
                            sheetHeight);
                }
            } else {
                frame = animation
                        .map(value -> value.calculateFrameSize(
                                sheetWidth,
                                sheetHeight))
                        .orElseGet(() -> new FrameSize(
                                sheetWidth,
                                sheetHeight));
            }
            if (frame.width() <= 0
                    || frame.height() <= 0
                    || sheetWidth % frame.width() != 0
                    || readableHeight % frame.height() != 0
                    || frame.width() % columns != 0
                    || frame.height() % rows != 0) {
                return false;
            }
            return true;
        } catch (IOException
                | IllegalArgumentException
                | IllegalStateException
                | ArithmeticException exception) {
            return false;
        }
    }
}
