package com.kltyton.autoseamblend.authoring.format.mcpatcher;

import com.kltyton.autoseamblend.authoring.storage.ManagedPackIdentity;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;

/**
 * 中文：为所有 Loader 严格解析 MCPatcher 扩展、执行副本和来源信息。
 * English: Strictly resolves MCPatcher extensions, execution copies, and provenance for every
 * loader.
 */
public final class MCPatcherExecutionDocument {
    private MCPatcherExecutionDocument() {}

    public static Result prepare(
            Properties author,
            ResourceLocation resourceId,
            PackResources pack,
            int packPriority) {
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(pack, "pack");
        MCPatcherExecutionProperties.Result parsed =
                MCPatcherExecutionProperties.prepare(author);
        if (parsed.rejection().isPresent()) {
            return Result.rejected(parsed.rejection().orElseThrow());
        }
        MCPatcherExecutionProperties.Prepared prepared = parsed.prepared().orElseThrow();
        return Result.accepted(
                prepared.runtimeProperties(),
                new MCPatcherAuthorExtension(
                        pack.packId(),
                        resourceId,
                        packPriority,
                        ManagedPackIdentity.matches(pack),
                        prepared.requestedMethod(),
                        prepared.resolvedMethod(),
                        prepared.exactSurfaceResolutionRequired(),
                        prepared.compatibility()));
    }

    public record Result(
            Optional<Properties> runtimeProperties,
            Optional<MCPatcherAuthorExtension> extension,
            Optional<String> rejection) {
        public Result {
            runtimeProperties = Objects.requireNonNull(runtimeProperties, "runtimeProperties");
            extension = Objects.requireNonNull(extension, "extension");
            rejection = Objects.requireNonNull(rejection, "rejection");
            if (runtimeProperties.isPresent() != extension.isPresent()
                    || runtimeProperties.isPresent() == rejection.isPresent()) {
                throw new IllegalArgumentException("exactly one result branch is required");
            }
        }

        public static Result accepted(
                Properties runtime,
                MCPatcherAuthorExtension extension) {
            return new Result(
                    Optional.of(runtime), Optional.of(extension), Optional.empty());
        }

        public static Result rejected(String reason) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("rejection reason must not be blank");
            }
            return new Result(Optional.empty(), Optional.empty(), Optional.of(reason));
        }
    }
}
