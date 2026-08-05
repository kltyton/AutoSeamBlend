package com.kltyton.autoseamblend.engine.ownership.evidence;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * 中文：把当前 Minecraft ResourceManager 适配为公共只读原生资源端口。
 * English: Adapts the current Minecraft ResourceManager to the shared read-only native-resource
 * port.
 */
public final class MinecraftNativeResourceSource implements NativeResourceSource {
    private final ResourceManager resources;

    public MinecraftNativeResourceSource(ResourceManager resources) {
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    @Override
    public Optional<byte[]> read(String resourceId) {
        Identifier identifier = Identifier.tryParse(resourceId);
        if (identifier == null) {
            return Optional.empty();
        }
        Optional<Resource> resource = resources.getResource(identifier);
        if (resource.isEmpty()) {
            return Optional.empty();
        }
        try (var input = resource.orElseThrow().open()) {
            return Optional.of(input.readAllBytes());
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    @Override
    public TextureResourceState inspectTexture(
            String spriteId,
            int columns,
            int rows,
            SheetFramePolicy policy) {
        Optional<String> textureFile = NativeResourceIdentifier.textureFile(spriteId);
        Identifier file = textureFile.map(Identifier::tryParse).orElse(null);
        Identifier nativeSprite = Identifier.tryParse(spriteId);
        if (file == null || nativeSprite == null) {
            return TextureResourceState.INVALID;
        }
        Identifier nativeFile = SpriteSource.TEXTURE_ID_CONVERTER.idToFile(nativeSprite);
        if (!file.equals(nativeFile)) {
            return TextureResourceState.INVALID;
        }
        Optional<Resource> resource = resources.getResource(file);
        if (resource.isEmpty()) {
            return TextureResourceState.MISSING;
        }
        if (policy == SheetFramePolicy.EXISTENCE_ONLY) {
            return TextureResourceState.PRESENT;
        }
        return NativeTextureSheetValidator.valid(
                        resource.orElseThrow(),
                        columns,
                        rows,
                        policy)
                ? TextureResourceState.PRESENT
                : TextureResourceState.INVALID;
    }
}
