package com.kltyton.autoseamblend.fabric.compat.continuity.preview;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Consumer;
import me.pepperbell.continuity.api.client.ProcessingDataKey;
import me.pepperbell.continuity.api.client.QuadProcessor;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;

/**
 * 中文：预览场景与精确面采样共用的短生命周期原生处理数据，不跨帧共享可变引擎状态。
 * 契约与 Fabric Continuity 3.0.0+1.21 的 ProcessingContextImpl 一致：额外 Quad 累积在
 * meshBuilder，由 drainExtraQuads 在处理器链结束后回放到目标 emitter（等价 outputTo）。
 *
 * English: Short-lived native processing data shared by the preview scene and the
 * exact-face sampler, mirroring Fabric Continuity 3.0.0+1.21's
 * ProcessingContextImpl: extra quads accumulate in the meshBuilder and
 * drainExtraQuads replays them to a target emitter after the processor chain
 * (equivalent to outputTo). Mutable engine state is never shared across frames.
 */
final class FabricContinuityPreviewProcessingContext
        implements QuadProcessor.ProcessingContext {
    private final IdentityHashMap<ProcessingDataKey<?>, Object>
            data = new IdentityHashMap<>();
    private final MeshBuilder meshBuilder =
            RendererAccess.INSTANCE
                    .getRenderer()
                    .meshBuilder();
    private final List<Consumer<QuadEmitter>>
            emitterConsumers = new ArrayList<>();
    private final List<Mesh> meshes = new ArrayList<>();
    private boolean hasExtraQuads;

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getData(
            ProcessingDataKey<T> key) {
        return (T) data.computeIfAbsent(
                key,
                ignored -> key
                        .getValueSupplier()
                        .get());
    }

    @Override
    public void addEmitterConsumer(
            Consumer<QuadEmitter> consumer) {
        emitterConsumers.add(consumer);
    }

    @Override
    public void addMesh(Mesh mesh) {
        meshes.add(mesh);
    }

    @Override
    public QuadEmitter getExtraQuadEmitter() {
        return meshBuilder.getEmitter();
    }

    @Override
    public void markHasExtraQuads() {
        hasExtraQuads = true;
    }

    /**
     * 中文：按 ProcessingContextImpl.outputTo 的顺序把全部额外 Quad 源回放到目标
     * emitter（emitterConsumers → addMesh → meshBuilder.build），并清空本次状态；
     * 返回是否存在任何额外 Quad 源。
     *
     * English: Replays every extra-quad source into the target emitter in
     * ProcessingContextImpl.outputTo order (emitterConsumers → added meshes →
     * meshBuilder.build), then clears the run state; returns whether any extra-quad
     * source existed.
     */
    boolean drainExtraQuads(QuadEmitter target) {
        boolean declared = !emitterConsumers.isEmpty()
                || !meshes.isEmpty()
                || hasExtraQuads;
        if (!declared) {
            return false;
        }
        for (Consumer<QuadEmitter> consumer
                : emitterConsumers) {
            consumer.accept(target);
        }
        emitterConsumers.clear();
        for (Mesh mesh : meshes) {
            mesh.outputTo(target);
        }
        meshes.clear();
        if (hasExtraQuads) {
            meshBuilder.build().outputTo(target);
        }
        hasExtraQuads = false;
        return true;
    }
}
