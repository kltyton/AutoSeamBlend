package com.kltyton.autoseamblend.forge.engine.registry;

import com.kltyton.autoseamblend.engine.EngineAdapter;
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
import com.kltyton.autoseamblend.engine.registry.EngineRegistryBuilder;
import com.kltyton.autoseamblend.engine.registry.EngineRegistryAccess;
import com.kltyton.autoseamblend.engine.registry.EngineRegistryRuntimeState;
import com.kltyton.autoseamblend.engine.registry.EngineRegistrySnapshot;
import com.kltyton.autoseamblend.engine.registry.EngineSelection;
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
import java.util.Optional;
import net.minecraftforge.fml.ModList;

/**
 * 中文：Forge 只保留 Loader 发现与原生能力 provider 桥，Discover→Validate→Select 状态机与
 * 稳定排序由 common 统一执行。
 *
 * English: Forge keeps only loader discovery and native capability-provider bridges. The
 * common module owns the Discover-Validate-Select state machine and stable ordering.
 */
public final class ForgeEngineRegistry {
    private static final EngineDefinitionCatalog DEFINITIONS = EngineDefinitionCatalog.of(List.of(
            EngineDefinition.ofVersions(
                    "continuity",
                    EngineFamily.MCPATCHER,
                    "mcpatcher",
                    List.of(
                            "3.0.0+1.20.1.forge",
                            // Connector exposes the official Continuity version through Forge's
                            // DefaultArtifactVersion, which removes the SemVer build-metadata '+'.
                            "3.0.01.20.1.forge",
                            "0.1.1+1.20.1.forge.build.4"),
                    "Continuity 3.0.0+1.20.1.forge or Constancy 0.1.1+1.20.1.forge.build.4 properties and processor lifecycle",
                    "me/pepperbell/continuity/client/resource/CtmPropertiesLoader.class",
                    "me/pepperbell/continuity/client/model/QuadProcessors.class",
                    "me/pepperbell/continuity/client/processor/simple/CtmSpriteProvider.class"),
            EngineDefinition.of(
                    "ctm",
                    EngineFamily.CTM_MOD,
                    "ctm_mod",
                    "1.20.1-1.1.10",
                    "CTM Mod 1.20.1-1.1.10 public model, connection-check, and strategy APIs",
                    "team/chisel/ctm/client/model/AbstractCTMBakedModel.class",
                    "team/chisel/ctm/client/newctm/ConnectionCheck.class",
                    "team/chisel/ctm/client/util/CTMLogic.class"),
            EngineDefinition.of(
                    "fusion",
                    EngineFamily.FUSION,
                    "fusion",
                    "1.3.12",
                    "Fusion 1.3.12 public texture, layout, model, and quad APIs",
                    "com/supermartijn642/fusion/api/texture/DefaultTextureTypes.class",
                    "com/supermartijn642/fusion/api/texture/custom/QuadProcessor.class",
                    "com/supermartijn642/fusion/texture/types/connecting/layouts/ConnectingTextureLayoutHandler.class"),
            EngineDefinition.of(
                    "athena",
                    EngineFamily.ATHENA,
                    "athena",
                    "3.1.2",
                    "Athena 3.1.2 public model, CtmState, and provider APIs",
                    "earth/terrarium/athena/api/client/forge/AthenaBakedModel.class",
                    "earth/terrarium/athena/api/client/utils/CtmState.class",
                    "earth/terrarium/athena/impl/client/models/ctm/ConnectedTextureMap.class")));
    private static final EngineDiscovery DISCOVERY = EngineDiscoveries.classpath(
            modId -> ModList.get()
                    .getModContainerById(modId)
                    .map(container -> container.getModInfo().getVersion().toString()),
            ForgeEngineRegistry.class);
    public static final EngineRegistryAccess<EngineRegistryRuntimeState> RUNTIME =
            new EngineRegistryAccess<>(
                    DEFINITIONS,
                    DISCOVERY,
                    () -> EngineRegistryBuilder.build(
                            DEFINITIONS,
                            DISCOVERY,
                            ForgeEngineRegistry::provide),
                    registry -> new EngineRegistryRuntimeState(
                            registry,
                            registry.select(EngineSelectionRequest.automatic())));

    /**
     * 中文：返回锁定引擎的精确版本，供 Mixin 插件与发现流程共用同一判定。
     * English: Returns the locked engine version shared by mixin plugins and discovery.
     */
    public static String expectedVersion(String engineId) {
        return DEFINITIONS.require(engineId)
                .descriptor()
                .expectedVersion();
    }

    /**
     * 中文：返回同一家族已审计的可替换实现版本；未列出的版本继续 fail-closed。
     * English: Returns audited interchangeable implementation versions for one family;
     * unlisted versions remain fail-closed.
     */
    public static List<String> acceptedVersions(String engineId) {
        return DEFINITIONS.require(engineId).acceptedVersions();
    }

    public static boolean acceptsVersion(String engineId, String version) {
        return DEFINITIONS.require(engineId).acceptsVersion(version);
    }

    private ForgeEngineRegistry() {}

    /**
     * 中文：只执行模组、版本与类资源链接发现；调用者随后可安全注册已安装引擎的隔离 provider。
     * English: Performs only mod, version, and class-resource linkage discovery; callers may then
     * safely register isolated providers for installed engines.
     */
    /**
     * 中文：Forge provider 只负责原生能力证明和适配器构造；版本、链接门、状态与诊断聚合由
     * common builder 处理。
     * English: The Forge provider only proves native capabilities and constructs adapters;
     * versions, linkage gates, statuses, and diagnostic aggregation belong to the common builder.
     */
    private static EngineAdapterProvision provide(EngineDefinition definition) {
        CapabilityMatrix capabilities = validatedCapabilities(definition);
        if (!capabilities.isComplete()) {
            List<String> missingCells = java.util.Arrays.stream(ConnectionMethod.values())
                    .flatMap(method -> java.util.Arrays.stream(CapabilitySurface.values())
                            .filter(surface -> !capabilities.supports(method, surface))
                            .map(surface -> method.serializedName() + '/' + surface.name()))
                    .toList();
            EngineDiagnostic diagnostic = new EngineDiagnostic(
                    EngineDiagnostic.Severity.ERROR,
                    "ENGINE_CAPABILITY_INCOMPLETE",
                    definition.descriptor().engineId()
                            + " product capability hooks are incomplete",
                    Map.of(
                            "cells", String.join(",", missingCells),
                            "providers", providerSummary(definition)));
            return EngineAdapterProvision.unavailable(capabilities, diagnostic);
        }
        return EngineAdapterProvision.available(
                new ValidatedAdapter(definition.descriptor(), capabilities));
    }

    private static CapabilityMatrix validatedCapabilities(EngineDefinition definition) {
        EnumMap<ConnectionMethod, Map<CapabilitySurface, CapabilityState>> cells =
                new EnumMap<>(ConnectionMethod.class);
        for (ConnectionMethod method : ConnectionMethod.values()) {
            EnumMap<CapabilitySurface, CapabilityState> surfaces =
                    new EnumMap<>(CapabilitySurface.class);
            for (CapabilitySurface surface : CapabilitySurface.values()) {
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

    /**
     * 中文：方法行为声明由公共方法域给出；AUTO 先解析一次，NONE 在四面均是成功透传/空生成/保留。
     * English: Method behavior comes from the common method domain; AUTO resolves once and NONE
     * succeeds as passthrough, empty materialization, and preserved baked content.
     */
    private static boolean behaviorDeclared(ConnectionMethod method) {
        return switch (Objects.requireNonNull(method, "method")) {
            case AUTO, NONE -> true;
            default -> !MethodSlotDomain.of(method).slots().isEmpty();
        };
    }

    private static String providerSummary(EngineDefinition definition) {
        return java.util.Arrays.stream(CapabilitySurface.values())
                .map(surface -> surface.name() + '=' + providerRegistered(definition, surface))
                .collect(java.util.stream.Collectors.joining(","));
    }

    private record ValidatedAdapter(
            EngineDescriptor descriptor,
            CapabilityMatrix capabilities)
            implements EngineAdapter {
        private ValidatedAdapter {
            Objects.requireNonNull(descriptor, "descriptor");
            Objects.requireNonNull(capabilities, "capabilities");
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
                    Objects.requireNonNull(query, "query"),
                    Objects.requireNonNull(nativeContext, "nativeContext"));
        }

        @Override
        public NativeMethodMapping mapping(ConnectionMethod method) {
            return NativeMethodMapping.standard(
                    method,
                    value -> descriptor.formatId() + ':' + value.serializedName());
        }
    }

}
