package com.kltyton.autoseamblend.forge.bootstrap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ForgeEntryPointContractTest {
    @Test
    void forge121EntrypointExposesTheConstructorUsedByJavaFml() {
        assertDoesNotThrow(
                () -> ForgeEntryPoint.class.getDeclaredConstructor());
    }

    @Test
    void fzzyConfigUsesTheModdevRemappingConfiguration() throws IOException {
        Path script = projectRoot().resolve("forge/build.gradle");
        String buildScript = readText(script);

        assertTrue(
                buildScript.contains(
                        "modImplementation(\"me.fzzyhmstrs:fzzy_config:${fzzy_config_forge_version}\")"));
    }

    @Test
    void validationRuntimeLoadsOnlyTheSelectedConnectedTextureEngine() throws IOException {
        String buildScript = readText(projectRoot().resolve("forge/build.gradle"));

        assertTrue(buildScript.contains(
                "continuity: \"maven.modrinth:continuity:${continuity_forge_id}\""));
        assertTrue(buildScript.contains(
                "constancy : \"io.github.thinkingstudios:constancy:${constancy_forge_version}@jar\""));
        assertTrue(buildScript.contains("validationEngine == 'continuity'"));
        assertTrue(buildScript.contains(
                "modRuntimeOnly(\"maven.modrinth:connector:${connector_forge_id}\")"));
        assertTrue(buildScript.contains(
                "modRuntimeOnly(\"maven.modrinth:forgified-fabric-api:${forgified_fabric_api_forge_id}\")"));
        assertTrue(buildScript.contains("tasks.register('patchConstancyRuntimeJar', Zip)"));
        assertTrue(buildScript.contains("exclude 'continuity.mixins.json'"));
        assertTrue(buildScript.contains("from('src/validation/resources')"));
        assertTrue(buildScript.contains(
                "attribute(\n                    MinecraftMappings.ATTRIBUTE"));
        assertTrue(buildScript.contains("'constancyValidationNamed'"));
        assertTrue(buildScript.contains("validationEngine == 'constancy'"));
        assertTrue(buildScript.contains(
                "runtimeOnly(files(configurations.constancyValidationNamed))"));
        assertTrue(buildScript.contains(
                "runtimeOnly(validationEngineDependencies[validationEngine])"));
        assertFalse(buildScript.contains(
                "modRuntimeOnly(\"curse.maven:ctm-267602:${ctm_forge_file}\")"));
        assertFalse(buildScript.contains(
                "modRuntimeOnly(\"maven.modrinth:athena-ctm:${athena_forge_id}\")"));
    }

    @Test
    void officiallyMappedValidationEnginesUseTheNamedRuntimeTransform() throws IOException {
        String buildScript = readText(projectRoot().resolve("forge/build.gradle"));

        assertTrue(buildScript.contains(
                "if (validationEngine == 'ctm' || validationEngine == 'fusion' || validationEngine == 'athena')"));
        assertTrue(buildScript.contains(
                "modRuntimeOnly(validationEngineDependencies[validationEngine])"));
    }

    @Test
    void forgeTestsUseALazyNamedArtifactViewWithExplicitMappingProducerOrdering()
            throws IOException {
        String buildScript = readText(projectRoot().resolve("forge/build.gradle"));

        assertTrue(buildScript.contains("configurations.compileClasspath.incoming.artifactView"));
        assertTrue(buildScript.contains("componentFilter { identifier ->"));
        assertTrue(buildScript.contains(
                "transformedForgeTestEngineArtifacts.builtBy(tasks.named('createMinecraftArtifacts'))"));
        assertTrue(buildScript.contains("testCompileOnly(transformedForgeTestEngineArtifacts)"));
        assertTrue(buildScript.contains("testRuntimeOnly(transformedForgeTestEngineArtifacts)"));
        assertFalse(buildScript.contains("configurations.compileClasspath.files"));
    }

    @Test
    void fusionUvRemappingUsesThe1201PixelSpaceSpriteApi() throws IOException {
        String processor = readText(projectRoot().resolve(
                "common/src/main/java/com/kltyton/autoseamblend/compat/fusion/runtime/FusionNativeQuadProcessor.java"));

        assertTrue(processor.contains("target.getU(u * 16.0F)"));
        assertTrue(processor.contains("target.getV(v * 16.0F)"));
    }

    @Test
    void spriteSourceRegistrationUsesTheForgeAccessTransformer() throws IOException {
        Path root = projectRoot();
        String registration = readText(root.resolve(
                "forge/src/main/java/com/kltyton/autoseamblend/forge/runtime/texture/atlas/ForgeGeneratedSpriteSourceRegistration.java"));
        String accessTransformer = readText(root.resolve(
                "common/src/main/resources/META-INF/accesstransformer.cfg"));

        assertTrue(accessTransformer.contains(
                "net.minecraft.client.renderer.texture.atlas.SpriteSources f_260548_"));
        assertTrue(registration.contains("SpriteSources.TYPES.put("));
        assertFalse(registration.contains("SpriteSourcesAccessor"));
    }

    @Test
    void spriteSourceTypeIsRegisteredBeforeTheOptionalEngineGate() throws IOException {
        String entrypoint = readText(projectRoot().resolve(
                "forge/src/main/java/com/kltyton/autoseamblend/forge/bootstrap/ForgeEntryPoint.java"));

        int registration = entrypoint.indexOf("ForgeGeneratedSpriteSourceRegistration.register();");
        int engineGate = entrypoint.indexOf("if (engines.engineRequired())");
        assertTrue(registration >= 0);
        assertTrue(engineGate >= 0);
        assertTrue(registration < engineGate);
        assertTrue(entrypoint.contains("install Continuity or Constancy"));
    }

    @Test
    void textureStitchPostUsesTheForgeModEventBus() throws IOException {
        String entrypoint = readText(projectRoot().resolve(
                "forge/src/main/java/com/kltyton/autoseamblend/forge/bootstrap/ForgeEntryPoint.java"));

        assertTrue(entrypoint.contains(
                "modEventBus.addListener(\n                ForgeGeneratedSpriteResolutionEvents::onTextureAtlasStitched)"));
        assertFalse(entrypoint.contains(
                "MinecraftForge.EVENT_BUS.addListener(\n                ForgeGeneratedSpriteResolutionEvents::onTextureAtlasStitched)"));
    }

    @Test
    void constancyRuntimeMixinsHaveADevelopmentRemappingRefmap() throws IOException {
        Path resources = projectRoot().resolve("forge/src/validation/resources");
        Path mixinConfig = resources.resolve("continuity.mixins.json");
        Path refmapFile = resources.resolve("autoseamblend.constancy.refmap.json");

        assertTrue(Files.isRegularFile(mixinConfig));
        assertTrue(Files.isRegularFile(refmapFile));

        String config = readText(mixinConfig);
        String refmap = readText(refmapFile);
        assertTrue(config.contains("\"refmap\": \"autoseamblend.constancy.refmap.json\""));

        String[] officialTargets = {
            "m_260886_(Lnet/minecraft/server/packs/resources/ResourceManager;)Ljava/util/List;",
            "m_5540_(Lnet/minecraft/server/packs/resources/PreparableReloadListener$PreparationBarrier;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;Lnet/minecraft/util/profiling/ProfilerFiller;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;",
            "m_245476_(Lnet/minecraft/util/profiling/ProfilerFiller;Ljava/util/Map;Lnet/minecraft/client/resources/model/ModelBakery;)Lnet/minecraft/client/resources/model/ModelManager$ReloadState;",
            "m_247616_(Lnet/minecraft/client/resources/model/ModelManager$ReloadState;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            "m_245515_(Ljava/util/Map;)V",
            "m_7392_(Lnet/minecraft/world/entity/item/FallingBlockEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            "m_135841_(Ljava/lang/String;)Z",
            "m_213713_(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;",
            "m_213829_(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/List;",
            "m_10624_(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/resources/ResourceLocation;",
            "m_6922_(Lnet/minecraft/world/level/block/piston/PistonMovingBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;m_111000_()V",
            "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;m_111077_()V",
            "f_203815_",
            "m_109282_(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/client/renderer/RenderType;",
            "m_109293_(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/client/renderer/RenderType;",
            "m_260881_(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/ResourceLocation;ILjava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;",
            "m_261295_(Ljava/util/List;ILjava/util/concurrent/Executor;)Lnet/minecraft/client/renderer/texture/SpriteLoader$Preparations;"
        };
        for (String target : officialTargets) {
            assertTrue(refmap.contains("\"" + target + "\": \"" + target + "\""), target);
        }
    }

    @Test
    void developmentAndProductionDeclareTheSameForgeMixinConfigurations() throws IOException {
        Path root = projectRoot();
        String buildScript = readText(root.resolve("forge/build.gradle"));
        String minecraftMixins = readText(root.resolve(
                "forge/src/main/resources/autoseamblend.forge.mixins.json"));

        assertTrue(buildScript.contains("annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'"));
        assertTrue(buildScript.contains("mixin {"));
        assertTrue(buildScript.contains("add sourceSets.main, 'autoseamblend.forge.refmap.json'"));
        assertTrue(buildScript.contains("attributes 'MixinConfigs': forgeMixinConfigs.join(',')"));
        assertTrue(buildScript.contains("'autoseamblend.forge.mixins.json'"));
        assertFalse(buildScript.contains("'autoseamblend.forge.platform.mixins.json'"));
        assertTrue(buildScript.contains("'autoseamblend.forge.continuity.common.mixins.json'"));
        assertTrue(buildScript.contains("'autoseamblend.forge.ctm.mixins.json'"));
        assertTrue(buildScript.contains("'autoseamblend.forge.fusion.common.mixins.json'"));
        assertTrue(buildScript.contains("'autoseamblend.forge.athena.common.mixins.json'"));
        assertTrue(buildScript.contains("'autoseamblend.forge.athena.mixins.json'"));
        assertFalse(buildScript.contains("'autoseamblend.common.minecraft.mixins.json'"));

        assertTrue(minecraftMixins.contains("\"refmap\": \"autoseamblend.forge.refmap.json\""));
        assertTrue(minecraftMixins.contains("\"ModelManagerInvoker\""));
        assertTrue(minecraftMixins.contains("\"SpriteContentsImageAccessor\""));
        assertTrue(minecraftMixins.contains("\"SpriteSourceListAccessor\""));
        assertTrue(minecraftMixins.contains("\"AtlasManagerInitialPreparationMixin\""));
    }

    @Test
    void connectorValidationRunsThePackagedModJar() throws IOException {
        String buildScript = readText(projectRoot().resolve("forge/build.gradle"));

        assertTrue(buildScript.contains("sourceSets.create('packagedValidation')"));
        assertTrue(buildScript.contains("extendsFrom(configurations.runtimeClasspath)"));
        assertTrue(buildScript.contains("files(tasks.named('jar').flatMap { it.archiveFile })"));
        assertTrue(buildScript.contains("sourceSet = packagedValidationSourceSet"));
        assertTrue(buildScript.contains("loadedMods.set([])"));
        assertTrue(buildScript.contains("taskBefore(tasks.named('jar'))"));
    }

    @Test
    void sharedResourcesDeclareMinecraft1201PackMetadata() throws IOException {
        Path metadata = projectRoot().resolve("common/src/main/resources/pack.mcmeta");

        assertTrue(Files.isRegularFile(metadata));
        String contents = readText(metadata);
        assertTrue(contents.contains("\"pack_format\": 15"));
        assertTrue(contents.contains("AutoSeamBlend resources"));
    }

    private static Path projectRoot() {
        Path workingDirectory = Path.of("").toAbsolutePath();
        return Files.exists(workingDirectory.resolve("forge/build.gradle"))
                ? workingDirectory
                : workingDirectory.getParent();
    }

    private static String readText(Path path) throws IOException {
        return Files.readString(path).replace("\r\n", "\n");
    }
}
