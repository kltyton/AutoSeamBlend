package com.kltyton.autoseamblend.mixin.minecraft;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 中文：在资源重载期间只读访问已解码源像素。 / English: Read-only resource-reload access to the decoded source pixels. */
@Mixin(SpriteContents.class)
public interface SpriteContentsImageAccessor {
    @Accessor("originalImage")
    NativeImage autoseamblend$originalImage();
}
