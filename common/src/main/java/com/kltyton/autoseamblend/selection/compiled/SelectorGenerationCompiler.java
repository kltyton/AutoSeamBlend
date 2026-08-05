package com.kltyton.autoseamblend.selection.compiled;

import com.kltyton.autoseamblend.config.model.ConfigSnapshot;
import com.kltyton.autoseamblend.selection.minecraft.MinecraftSelectorResolver;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.block.Block;

/**
 * 中文：从配置与当前注册表编译不可变选择器代次；Loader 只负责代次发布、日志与失败回退。
 *
 * English: Compiles an immutable selector generation from configuration and the current
 * registry; loaders retain only publication, logging, and failure fallback.
 */
public final class SelectorGenerationCompiler {
    private SelectorGenerationCompiler() {}

    /**
     * 中文：编译 Minecraft 方块选择器，统一 Fabric 与 NeoForge 的注册表解析路径。
     *
     * English: Compiles Minecraft block selectors through one registry-resolution path shared by
     * Fabric and NeoForge.
     */
    public static Result<Block> compileMinecraftBlocks(
            ConfigSnapshot config,
            RegistryAccess registryAccess,
            long generation,
            String publicationReason) {
        return compile(
                config,
                new MinecraftSelectorResolver(registryAccess),
                generation,
                publicationReason);
    }

    public static <T> Result<T> compile(
            ConfigSnapshot config,
            ConnectionRuleSet.Resolver<T> resolver,
            long generation,
            String publicationReason) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(resolver, "resolver");
        if (generation < 0) {
            throw new IllegalArgumentException("generation must be non-negative");
        }
        if (publicationReason == null || publicationReason.isBlank()) {
            throw new IllegalArgumentException("publicationReason must not be blank");
        }
        ConnectionRuleSet.Compilation<T> compilation = ConnectionRuleSet.compile(
                config.targets(),
                config.excludedTargets(),
                resolver);
        Optional<CompiledSelectorState<T>> state = compilation.valid()
                ? Optional.of(new CompiledSelectorState<>(
                        generation,
                        compilation.rules(),
                        config.automaticDiscovery(),
                        compilation.validSelectorCount(),
                        publicationReason,
                        compilation.diagnostics()))
                : Optional.empty();
        return new Result<>(compilation, state);
    }

    public record Result<T>(
            ConnectionRuleSet.Compilation<T> compilation,
            Optional<CompiledSelectorState<T>> state) {
        public Result {
            Objects.requireNonNull(compilation, "compilation");
            state = Objects.requireNonNull(state, "state");
            if (state.isPresent() != compilation.valid()) {
                throw new IllegalArgumentException(
                        "compiled selector state presence must match compilation validity");
            }
        }

        public boolean valid() {
            return compilation.valid();
        }

        public List<String> diagnostics() {
            return compilation.diagnostics();
        }

        public List<String> deferredSelectors() {
            return compilation.deferredSelectors();
        }

        public CompiledSelectorState<T> stateOrThrow() {
            return state.orElseThrow(() -> new IllegalStateException(
                    "invalid selector compilation has no published state"));
        }
    }
}
