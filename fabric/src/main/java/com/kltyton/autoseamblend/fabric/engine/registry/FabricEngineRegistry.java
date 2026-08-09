package com.kltyton.autoseamblend.fabric.engine.registry;

import com.kltyton.autoseamblend.engine.EngineDescriptor;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.capability.CapabilityMatrix;
import com.kltyton.autoseamblend.engine.capability.CapabilityState;
import com.kltyton.autoseamblend.engine.capability.CapabilitySurface;
import com.kltyton.autoseamblend.engine.plan.NativeMethodMapping;
import com.kltyton.autoseamblend.engine.query.ConnectionQuery;
import com.kltyton.autoseamblend.engine.query.EngineQueryContext;
import com.kltyton.autoseamblend.engine.query.QueryObservation;
import com.kltyton.autoseamblend.engine.registry.EngineAdapterProvision;
import com.kltyton.autoseamblend.engine.registry.EngineDefinition;
import com.kltyton.autoseamblend.engine.registry.EngineDefinitionCatalog;
import com.kltyton.autoseamblend.engine.registry.EngineDiscoveries;
import com.kltyton.autoseamblend.engine.registry.EngineDiagnostic;
import com.kltyton.autoseamblend.engine.registry.EngineDiscovery;
import com.kltyton.autoseamblend.engine.registry.EngineRegistryAccess;
import com.kltyton.autoseamblend.engine.registry.EngineRegistryBuilder;
import com.kltyton.autoseamblend.engine.registry.EngineRegistryRuntimeState;
import com.kltyton.autoseamblend.engine.registry.EngineSelectionRequest;
import com.kltyton.autoseamblend.authoring.export.NativeExportRuntime;
import com.kltyton.autoseamblend.authoring.materialize.ConnectionTextureSources;
import com.kltyton.autoseamblend.authoring.preview.PreviewRuntime;
import com.kltyton.autoseamblend.engine.routing.query.NativeObservationBridge;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.selection.method.MethodSlotDomain;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 中文：Fabric 只保留 Loader 发现与适配器工厂桥；Discover→Validate→Select 状态机由
 * common 统一执行。Fabric 不声明 CTM Mod。
 *
 * English: Fabric keeps only loader discovery and adapter-factory bridges. The
 * common module owns the Discover-Validate-Select state machine; Fabric does
 * not declare CTM Mod.
 */
public final class FabricEngineRegistry {
    private static final EngineDefinitionCatalog DEFINITIONS = EngineDefinitionCatalog.of(List.of(
            EngineDefinition.of(
                    "continuity",
                    EngineFamily.MCPATCHER,
                    "mcpatcher",
                    "3.0.1-beta.2+26.1",
                    "Continuity accepted properties, processor, and Fabric model lifecycle",
                    "me/pepperbell/continuity/client/resource/CtmPropertiesLoader.class",
                    "me/pepperbell/continuity/client/model/CtmBlockStateModel.class",
                    "me/pepperbell/continuity/api/client/QuadProcessor.class"),
            EngineDefinition.of(
                    "fusion",
                    EngineFamily.FUSION,
                    "fusion",
                    "1.3.12",
                    "Fusion Fabric public texture-type, model, and quad APIs",
                    "com/supermartijn642/fusion/api/texture/DefaultTextureTypes.class",
                    "com/supermartijn642/fusion/api/texture/custom/BlockStateQuadProcessor.class",
                    "com/supermartijn642/fusion/api/texture/TextureType.class"),
            EngineDefinition.of(
                    "athena",
                    EngineFamily.ATHENA,
                    "athena",
                    "4.7.3",
                    "Athena Fabric public model, CtmState, provider, and pane APIs",
                    "earth/terrarium/athena/api/client/fabric/AthenaBakedModel.class",
                    "earth/terrarium/athena/api/client/utils/CtmState.class",
                    "earth/terrarium/athena/impl/client/models/ctm/FourtySevenSliceCtmProvider.class",
                    "earth/terrarium/athena/impl/client/models/PaneConnectedBlockModel.class")));
    private static final EngineDiscovery DISCOVERY = EngineDiscoveries.classpath(
            modId -> FabricLoader.getInstance()
                    .getModContainer(modId)
                    .map(container -> container.getMetadata()
                            .getVersion()
                            .getFriendlyString()),
            FabricEngineRegistry.class);
    public static final EngineRegistryAccess<EngineRegistryRuntimeState> RUNTIME =
            new EngineRegistryAccess<>(
                    DEFINITIONS,
                    DISCOVERY,
                    () -> EngineRegistryBuilder.build(
                            DEFINITIONS,
                            DISCOVERY,
                            FabricEngineRegistry::provide),
                    registry -> new EngineRegistryRuntimeState(
                            registry,
                            registry.select(
                                    EngineSelectionRequest.automatic())));

    private FabricEngineRegistry() {}

    private static EngineAdapterProvision provide(
            EngineDefinition definition) {
        String engineId = definition.descriptor().engineId();
        CapabilityMatrix capabilities =
                validatedCapabilities(definition);
        if (!capabilities.isComplete()) {
            List<String> missingCells =
                    java.util.Arrays.stream(ConnectionMethod.values())
                            .flatMap(method ->
                                    java.util.Arrays.stream(
                                                    CapabilitySurface.values())
                                            .filter(surface ->
                                                    !capabilities.supports(
                                                            method,
                                                            surface))
                                            .map(surface ->
                                                    method.serializedName()
                                                            + '/'
                                                            + surface.name()))
                            .toList();
            return EngineAdapterProvision.unavailable(
                    capabilities,
                    new EngineDiagnostic(
                            EngineDiagnostic.Severity.ERROR,
                            "ENGINE_CAPABILITY_INCOMPLETE",
                            engineId
                                    + " product capability hooks are incomplete",
                            Map.of(
                                    "cells",
                                    String.join(",", missingCells),
                                    "providers",
                                    providerSummary(definition))));
        }
        return EngineAdapterProvision.available(
                new ValidatedAdapter(
                        definition.descriptor(),
                        capabilities));
    }

    private static CapabilityMatrix validatedCapabilities(
            EngineDefinition definition) {
        EnumMap<ConnectionMethod, Map<CapabilitySurface, CapabilityState>>
                cells = new EnumMap<>(ConnectionMethod.class);
        for (ConnectionMethod method : ConnectionMethod.values()) {
            EnumMap<CapabilitySurface, CapabilityState> surfaces =
                    new EnumMap<>(CapabilitySurface.class);
            for (CapabilitySurface surface
                    : CapabilitySurface.values()) {
                surfaces.put(
                        surface,
                        providerRegistered(definition, surface)
                                        && behaviorDeclared(method)
                                ? CapabilityState.IMPLEMENTED
                                : CapabilityState.INCOMPLETE);
            }
            cells.put(method, surfaces);
        }
        return CapabilityMatrix.of(cells);
    }

    private static boolean providerRegistered(
            EngineDefinition definition,
            CapabilitySurface surface) {
        return switch (surface) {
            case RUNTIME -> NativeObservationBridge.registered(
                    definition.descriptor().engineId());
            case PREVIEW -> PreviewRuntime.available(
                    definition.descriptor().engineId());
            case PNG_MATERIALIZE -> ConnectionTextureSources.available(
                    definition.descriptor().family());
            case BAKED_EXPORT -> NativeExportRuntime.available(
                    definition.descriptor().engineId());
        };
    }

    private static boolean behaviorDeclared(
            ConnectionMethod method) {
        return switch (Objects.requireNonNull(
                method, "method")) {
            case AUTO, NONE -> true;
            default -> !MethodSlotDomain.of(method)
                    .slots()
                    .isEmpty();
        };
    }

    private static String providerSummary(
            EngineDefinition definition) {
        return java.util.Arrays.stream(CapabilitySurface.values())
                .map(surface -> surface.name()
                        + '='
                        + providerRegistered(
                                definition,
                                surface))
                .collect(java.util.stream.Collectors
                        .joining(","));
    }

    private record ValidatedAdapter(
            EngineDescriptor descriptor,
            CapabilityMatrix capabilities)
            implements com.kltyton.autoseamblend.engine.EngineAdapter {
        private ValidatedAdapter {
            Objects.requireNonNull(descriptor, "descriptor");
            Objects.requireNonNull(
                    capabilities,
                    "capabilities");
        }

        @Override
        public CapabilityMatrix capabilities() {
            return capabilities;
        }

        @Override
        public QueryObservation observe(
                ConnectionQuery query,
                EngineQueryContext nativeContext) {
            return NativeObservationBridge.observe(
                    descriptor,
                    query,
                    nativeContext);
        }

        @Override
        public NativeMethodMapping mapping(
                ConnectionMethod method) {
            return NativeMethodMapping.standard(
                    method,
                    value -> descriptor.formatId()
                            + ':'
                            + value.serializedName());
        }
    }
}
