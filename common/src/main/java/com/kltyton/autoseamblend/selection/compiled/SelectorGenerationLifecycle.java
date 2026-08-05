package com.kltyton.autoseamblend.selection.compiled;

import com.kltyton.autoseamblend.config.model.ConfigSnapshot;
import com.kltyton.autoseamblend.config.runtime.FzzyConfigRuntime;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.block.Block;

/**
 * 中文：统一 Fabric 与 NeoForge 的 PLAY 注册表绑定、配置捕获和选择器准备边界。
 *
 * English: Shared PLAY-registry binding, configuration capture, and selector-preparation boundary
 * for Fabric and NeoForge.
 *
 * <p>Loader runtimes retain only their publication, logging, and Loader-specific snapshot shape.
 * This class owns the one registry reference, the common reason/generation validation, Fzzy
 * configuration capture, and the sole compiler entry point.</p>
 */
public final class SelectorGenerationLifecycle {
    private static volatile RegistryAccess playRegistries;

    private SelectorGenerationLifecycle() {}

    public static synchronized void bindPlayRegistries(RegistryAccess registries) {
        playRegistries = Objects.requireNonNull(registries, "registries");
    }

    public static synchronized void unbindPlayRegistries() {
        playRegistries = null;
    }

    public static Preparation prepare(String reason, long generation) {
        requireReason(reason);
        requireGeneration(generation);
        ConfigSnapshot config = FzzyConfigRuntime.current();
        return new Preparation(
                config,
                SelectorGenerationCompiler.compileMinecraftBlocks(
                        config,
                        playRegistries,
                        generation,
                        reason));
    }

    public static Preparation bootstrap(boolean automaticDiscovery) {
        ConfigSnapshot config = ConfigSnapshot.capture(
                automaticDiscovery,
                Map.of(),
                Map.of());
        return new Preparation(
                config,
                SelectorGenerationCompiler.compileMinecraftBlocks(
                        config,
                        null,
                        0,
                        "bootstrap"));
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }

    private static void requireGeneration(long generation) {
        if (generation < 0) {
            throw new IllegalArgumentException(
                    "generation must be non-negative");
        }
    }

    public record Preparation(
            ConfigSnapshot config,
            SelectorGenerationCompiler.Result<Block> compiled) {
        public Preparation {
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(compiled, "compiled");
        }
    }
}
