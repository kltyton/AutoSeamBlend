package com.kltyton.autoseamblend.frontend.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class WorkbenchCandidateLifecycleContractTest {
    @Test
    void addingATargetUpdatesTheActiveCandidateScanOnBothLoaders() throws IOException {
        assertCandidateSynchronization("fabric", "fabric");
        assertCandidateSynchronization("forge", "forge");
    }

    private static void assertCandidateSynchronization(
            String module,
            String loaderPackage) throws IOException {
        String className = loaderPackage.equals("fabric")
                ? "FabricWorkbenchNativePort.java"
                : "ForgeWorkbenchNativePort.java";
        Path source = projectRoot().resolve(Path.of(
                module,
                "src/main/java/com/kltyton/autoseamblend",
                loaderPackage,
                "frontend/uilib/controller",
                className));
        String contents = Files.readString(source).replace("\r\n", "\n");

        assertTrue(contents.contains("synchronizeCandidateAvailability(blockId);"), source.toString());
        assertTrue(contents.contains(
                "candidates.removeIf(row -> row.receiverBlockId()\n"
                        + "                .filter(blockId::equals)\n"
                        + "                .isPresent());"), source.toString());
        assertTrue(contents.contains("existing.add(blockId);"), source.toString());
    }

    private static Path projectRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("forge/build.gradle"))
                    && Files.isRegularFile(current.resolve("fabric/build.gradle"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("AutoSeamBlend project root is unavailable");
    }
}
