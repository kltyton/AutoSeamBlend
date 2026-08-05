package com.kltyton.autoseamblend.fabric.compat.continuity.runtime;

import com.kltyton.autoseamblend.fabric.compat.continuity.mixin.WrapperBlockStateModelAccessor;
import com.kltyton.autoseamblend.fabric.reload.lifecycle.FabricModelCapture;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import me.pepperbell.continuity.client.model.CtmBlockStateModel;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：按 reload 会话暂存 clean/base 与 Continuity wrapper；预览只读取当前发布代次的模型。
 * English: Stages clean/base and Continuity wrapper models per reload session;
 * preview reads only the currently published generation models.
 */
public final class FabricContinuityModelLifecycle {
    private static volatile long activeSession = -1;
    private static volatile Map<BlockState, FabricBlockStateModel>
            nativeModels = Map.of();

    private FabricContinuityModelLifecycle() {}

    public static synchronized void begin(long session) {
        activeSession = session;
        nativeModels = new LinkedHashMap<>();
    }

    public static BlockStateModel captureBase(
            long session,
            BlockStateModel model,
            ModelModifier.AfterBakeBlock.Context context) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(context, "context");
        FabricModelCapture.capture(
                session,
                context.state(),
                model);
        return model;
    }

    public static synchronized BlockStateModel finishCapture(
            long session,
            BlockStateModel model,
            ModelModifier.AfterBakeBlock.Context context) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(context, "context");
        if (session != activeSession) {
            return model;
        }
        Set<BlockStateModel> visited =
                Collections.newSetFromMap(
                        new IdentityHashMap<>());
        BlockStateModel current = model;
        boolean containsContinuity = false;
        while (current != null
                && visited.add(current)) {
            if (current instanceof CtmBlockStateModel) {
                containsContinuity = true;
            }
            if (!(current
                    instanceof WrapperBlockStateModel)) {
                break;
            }
            current =
                    ((WrapperBlockStateModelAccessor)
                                    current)
                            .autoseamblend$wrapped();
        }
        if (containsContinuity
                && model
                        instanceof FabricBlockStateModel
                                fabricModel) {
            nativeModels.put(
                    context.state(),
                    fabricModel);
        }
        return model;
    }

    public static synchronized Optional<FabricBlockStateModel>
            previewModel(BlockState state) {
        return Optional.ofNullable(
                nativeModels.get(state));
    }
}
