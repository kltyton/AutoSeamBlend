package com.kltyton.autoseamblend.authoring.capture;

import com.kltyton.autoseamblend.engine.query.ExactSurfaceIdentity;

/**
 * 中文：原生引擎捕获的最小身份合同；平台实现只负责线程和生命周期适配。
 *
 * English: Minimal identity contract for a native-engine capture; platform
 * implementations own the thread and lifecycle adaptation.
 */
public interface NativeSurfaceCapture {
    long generation();

    String engineId();

    ExactSurfaceIdentity identity();

    /**
     * 中文：owning/client 线程即时冻结的预览结果；不得保留 live world/model/atlas 对象。
     *
     * English: Preview result frozen immediately on the owning/client thread;
     * it must not retain live world, model, or atlas objects.
     */
    interface Preview extends NativeSurfaceCapture {}

    /**
     * 中文：可交给 worker 的纯冻结捕获；只允许项目 DTO、标量和克隆字节/数组。
     *
     * English: Worker-safe frozen capture containing only project DTOs, scalars,
     * and cloned bytes/arrays.
     */
    interface WorkerSafe extends NativeSurfaceCapture {}
}
