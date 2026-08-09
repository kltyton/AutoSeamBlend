package com.kltyton.autoseamblend.fabric.compat.continuity.runtime;

import com.kltyton.autoseamblend.fabric.reload.lifecycle.FabricModelCapture;
import com.kltyton.autoseamblend.fabric.reload.lifecycle.FabricModelLifecycle;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import me.pepperbell.continuity.client.model.CtmBakedModel;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.model.WrapperBakedModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：按 reload 会话暂存 clean/base 与 Continuity wrapper；预览只读取当前发布代次的模型。
 *
 * English: Stages clean/base and Continuity wrapper models per reload session;
 * preview reads only the currently published generation models.
 */
public final class FabricContinuityModelLifecycle {
    private static volatile long activeSession = -1;
    private static volatile Map<BlockState, FabricBakedModel>
            nativeModels = Map.of();

    private FabricContinuityModelLifecycle() {}

    public static synchronized void begin(long session) {
        activeSession = session;
        nativeModels = new LinkedHashMap<>();
    }

    public static BakedModel captureBase(
            long session,
            BakedModel model,
            ModelModifier.AfterBake.Context context) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(context, "context");
        BlockState state =
                FabricModelLifecycle.resolveState(context);
        if (state != null) {
            FabricModelCapture.capture(
                    session,
                    state,
                    model);
        }
        return model;
    }

    public static synchronized BakedModel finishCapture(
            long session,
            BakedModel model,
            ModelModifier.AfterBake.Context context) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(context, "context");
        if (session != activeSession) {
            return model;
        }
        Set<BakedModel> visited =
                Collections.newSetFromMap(
                        new IdentityHashMap<>());
        BakedModel current = model;
        boolean containsContinuity = false;
        while (current != null
                && visited.add(current)) {
            if (current instanceof CtmBakedModel) {
                containsContinuity = true;
            }
            if (!(current
                    instanceof WrapperBakedModel)) {
                break;
            }
            current =
                    ((WrapperBakedModel) current)
                            .getWrappedModel();
        }
        BlockState state =
                FabricModelLifecycle.resolveState(context);
        if (containsContinuity
                && model
                        instanceof FabricBakedModel
                                fabricModel
                && state != null) {
            nativeModels.put(
                    state,
                    fabricModel);
        }
        return model;
    }

    public static synchronized Optional<FabricBakedModel>
            previewModel(BlockState state) {
        return Optional.ofNullable(
                nativeModels.get(state));
    }
}
