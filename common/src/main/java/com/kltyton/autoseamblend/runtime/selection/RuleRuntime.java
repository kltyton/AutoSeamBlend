package com.kltyton.autoseamblend.runtime.selection;

import com.kltyton.autoseamblend.foundation.Constants;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.compiled.CompiledSelectorState;
import com.kltyton.autoseamblend.selection.compiled.CompiledSelectorView;
import com.kltyton.autoseamblend.selection.compiled.SelectorGenerationCompiler;
import com.kltyton.autoseamblend.selection.compiled.SelectorGenerationLifecycle;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.block.Block;

/** 中文：构建完整选择器代次，然后发布不可变热路径快照。 / English: Builds complete selector generations before publishing one immutable hot-path snapshot. */
public final class RuleRuntime {
    private static final AtomicReference<Publication> PUBLICATION = new AtomicReference<>();

    private RuleRuntime() {}

    public static void installPublication(Publication publication) {
        Objects.requireNonNull(publication, "publication");
        if (!PUBLICATION.compareAndSet(null, publication)) {
            throw new IllegalStateException("rule runtime publication already installed");
        }
    }

    private static Publication publication() {
        Publication publication = PUBLICATION.get();
        if (publication == null) {
            throw new IllegalStateException("rule runtime publication is not installed");
        }
        return publication;
    }

    public static Snapshot current() {
        return publication().current();
    }

    public static synchronized Snapshot refresh(String reason) {
        java.util.Optional<Snapshot> prepared = prepare(
                reason,
                publication().nextGeneration());
        if (prepared.isEmpty()) {
            return current();
        }
        return publication().publish(prepared.orElseThrow());
    }

    /** 中文：编译一个完整选择器候选但不发布。 / English: Compiles one complete selector candidate without publishing it. */
    public static java.util.Optional<Snapshot> prepare(
            String reason,
            long generation) {
        SelectorGenerationLifecycle.Preparation preparation =
                SelectorGenerationLifecycle.prepare(reason, generation);
        SelectorGenerationCompiler.Result<Block> compiled = preparation.compiled();
        if (!compiled.valid()) {
            Constants.LOG.error(
                    "Rejected AutoSeamBlend selector candidate; retained generation {} reason={}",
                    current().generation(),
                    reason);
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Snapshot(compiled.stateOrThrow()));
    }

    public static synchronized Snapshot bindPlayRegistries(RegistryAccess registryAccess) {
        SelectorGenerationLifecycle.bindPlayRegistries(registryAccess);
        return refresh("join-tag-refresh");
    }

    public static Snapshot bootstrapSnapshot() {
        return new Snapshot(
                SelectorGenerationLifecycle.bootstrap(true)
                        .compiled()
                        .stateOrThrow());
    }

    /**
     * 中文：通用选择器状态承载代次、自动发现与诊断，Loader 仅负责原子发布。
     * English: The shared selector state carries generations, discovery, and diagnostics while the Loader only publishes atomically.
     */
    public record Snapshot(CompiledSelectorState<Block> compiled)
            implements CompiledSelectorView<Block> {
        public Snapshot {
            Objects.requireNonNull(compiled, "compiled");
        }

        public Snapshot(
                long generation,
                ConnectionRuleSet<Block> rules,
                boolean automaticDiscovery,
                int selectorCount,
                String publicationReason,
                List<String> diagnostics) {
            this(new CompiledSelectorState<>(
                    generation,
                    rules,
                    automaticDiscovery,
                    selectorCount,
                    publicationReason,
                    diagnostics));
        }

    }

    /** 中文：Loader 对选择器根代次的最小发布端口。 / English: Minimal Loader publication port for selector root generations. */
    public interface Publication {
        Snapshot current();

        long nextGeneration();

        Snapshot publish(Snapshot candidate);
    }
}
