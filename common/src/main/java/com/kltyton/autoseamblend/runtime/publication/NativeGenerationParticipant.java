package com.kltyton.autoseamblend.runtime.publication;

/**
 * 中文：没有方块模型载体的可选原生引擎捕获，也必须参与根资源代次提交。
 *
 * English:
 * Optional native-engine capture without a block-model carrier that still participates in the
 * root resource-generation commit.
 */
public interface NativeGenerationParticipant {
    String engineId();

    boolean prepared(long generation);

    void abort(long generation);
}
