package com.kltyton.autoseamblend.compat.fusion.runtime;

import com.google.gson.JsonObject;
import com.kltyton.autoseamblend.authoring.format.fusion.FusionNativeDocument;
import com.mojang.blaze3d.platform.NativeImage;
import com.supermartijn642.fusion.api.model.custom.quad.MutableQuad;
import com.supermartijn642.fusion.api.texture.RawTextureInstance;
import com.supermartijn642.fusion.api.texture.TextureType;
import com.supermartijn642.fusion.api.texture.custom.BlockStateQuadProcessor;
import com.supermartijn642.fusion.api.texture.custom.ItemQuadProcessor;
import com.supermartijn642.fusion.api.texture.custom.SpriteBuilder;
import com.supermartijn642.fusion.api.texture.custom.SpriteInstance;
import com.supermartijn642.fusion.api.texture.custom.TextureCreationContext;
import com.supermartijn642.fusion.api.texture.custom.TextureInstance;
import com.supermartijn642.fusion.api.texture.custom.TextureOutput;
import com.supermartijn642.fusion.api.util.PropertyStore;
import com.supermartijn642.fusion.api.util.UserErrorException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.resources.Identifier;

/**
 * 中文：把 AutoSeamBlend 输入委托给 Fusion 的公开原生 texture/model/quad 生命周期。
 * English: Delegates AutoSeamBlend input to Fusion's public native texture/model/quad lifecycle.
 */
public final class FusionDelegatingTextureType
        implements TextureType<FusionNativeTextureData, FusionOpaqueTextureState> {
    private final Function<FusionNativeDocument, FusionNativeRoute> routeResolver;

    public FusionDelegatingTextureType(
            Function<FusionNativeDocument, FusionNativeRoute> routeResolver) {
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver");
    }

    @Override
    public FusionNativeTextureData deserialize(JsonObject json) {
        FusionNativeDocument document = FusionNativeDocument.of(json);
        FusionNativeRoute route = Objects.requireNonNull(
                routeResolver.apply(document),
                "routeResolver result");
        return FusionNativeTextureData.parse(document, route);
    }

    @Override
    public JsonObject serialize(FusionNativeTextureData data) {
        Objects.requireNonNull(data, "data");
        return FusionNativeDocument.of(data.serializeNative())
                .withExtensions(data.route().requestedMethod(), data.compatibility())
                .json();
    }

    @Override
    public void createTexture(
            TextureOutput<FusionOpaqueTextureState> output,
            TextureCreationContext context,
            FusionNativeTextureData data) throws UserErrorException {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(data, "data")
                .createTexture(new ForwardTarget(output), context);
    }

    @Override
    public BlockStateQuadProcessor<?> initializeBlockStateModelQuad(
            MutableQuad quad,
            SpriteInstance sprite,
            FusionOpaqueTextureState state,
            PropertyStore properties) {
        return Objects.requireNonNull(state, "state")
                .initializeBlockStateModelQuad(
                        Objects.requireNonNull(quad, "quad"),
                        Objects.requireNonNull(sprite, "sprite"),
                        Objects.requireNonNull(properties, "properties"));
    }

    @Override
    public ItemQuadProcessor<?> initializeItemModelQuad(
            MutableQuad quad,
            SpriteInstance sprite,
            FusionOpaqueTextureState state,
            PropertyStore properties) {
        return Objects.requireNonNull(state, "state")
                .initializeItemModelQuad(
                        Objects.requireNonNull(quad, "quad"),
                        Objects.requireNonNull(sprite, "sprite"),
                        Objects.requireNonNull(properties, "properties"));
    }

    static final class ForwardTarget {
        private final TextureOutput<FusionOpaqueTextureState> output;

        private ForwardTarget(TextureOutput<FusionOpaqueTextureState> output) {
            this.output = output;
        }

        <T, X> void create(
                TextureType<T, X> type,
                T data,
                FusionNativeRoute route,
                TextureCreationContext context) throws UserErrorException {
            type.createTexture(new ForwardingOutput<>(type, output, route), context, data);
        }
    }

    private static final class ForwardingOutput<T, X> implements TextureOutput<X> {
        private final TextureType<T, X> nativeType;
        private final TextureOutput<FusionOpaqueTextureState> delegate;
        private final FusionNativeRoute route;
        private final AtomicReference<X> customData = new AtomicReference<>();
        private final AtomicBoolean customDataSet = new AtomicBoolean();

        private ForwardingOutput(
                TextureType<T, X> nativeType,
                TextureOutput<FusionOpaqueTextureState> delegate,
                FusionNativeRoute route) {
            this.nativeType = nativeType;
            this.delegate = delegate;
            this.route = route;
        }

        @Override
        public SpriteBuilder createSprite() {
            return delegate.createSprite();
        }

        @Override
        public void setCustomData(X data) {
            customData.set(data);
            customDataSet.set(true);
            delegate.setCustomData(FusionOpaqueTextureState.of(nativeType, data, route));
        }

        @Override
        public void setCreationCallback(Consumer<TextureInstance<X>> callback) {
            Objects.requireNonNull(callback, "callback");
            delegate.setCreationCallback(instance -> {
                if (!customDataSet.get()) {
                    throw new IllegalStateException(
                            "Fusion custom data must be set before creation callback");
                }
                callback.accept(new NativeTextureInstanceView<>(
                        nativeType,
                        instance,
                        customData.get()));
            });
        }

        @Override
        public <Y> SubTextureOutput<Y> createSubTexture(
                RawTextureInstance<?, Y> texture,
                String suffix,
                NativeImage image,
                AnimationMetadataSection metadata) throws UserErrorException {
            // 中文：image 是 Fusion 借入的所有权；本桥接不复制、不缓存也不关闭。
            // English: Fusion borrows image ownership here; this bridge neither copies, retains, nor closes it.
            return delegate.createSubTexture(texture, suffix, image, metadata);
        }
    }

    private static final class NativeTextureInstanceView<T, X> implements TextureInstance<X> {
        private final TextureType<T, X> nativeType;
        private final TextureInstance<FusionOpaqueTextureState> delegate;
        private final X customData;

        private NativeTextureInstanceView(
                TextureType<T, X> nativeType,
                TextureInstance<FusionOpaqueTextureState> delegate,
                X customData) {
            this.nativeType = nativeType;
            this.delegate = delegate;
            this.customData = customData;
        }

        @Override
        public TextureType<?, X> getTextureType() {
            return nativeType;
        }

        @Override
        public Identifier getIdentifier() {
            return delegate.getIdentifier();
        }

        @Override
        public List<SpriteInstance> getSprites() {
            return delegate.getSprites();
        }

        @Override
        public SpriteInstance getDefaultSprite() {
            return delegate.getDefaultSprite();
        }

        @Override
        public X getCustomData() {
            return customData;
        }
    }
}
