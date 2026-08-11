package com.kltyton.autoseamblend.engine.registry;

import com.kltyton.autoseamblend.engine.EngineAdapter;
import com.kltyton.autoseamblend.engine.EngineDescriptor;
import com.kltyton.autoseamblend.engine.capability.CapabilityMatrix;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：共享 Discover→Validate 构造器；Select 继续由 {@link EngineRegistrySnapshot} 执行。
 * English: Shared Discover-to-Validate builder; Select remains in {@link EngineRegistrySnapshot}.
 */
public final class EngineRegistryBuilder {
    private EngineRegistryBuilder() {}

    public static EngineRegistrySnapshot build(
            EngineDefinitionCatalog catalog,
            EngineDiscovery discovery,
            EngineAdapterProvider adapterProvider) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(discovery, "discovery");
        Objects.requireNonNull(adapterProvider, "adapterProvider");
        ArrayList<EngineDiagnostic> diagnostics = new ArrayList<>();
        ArrayList<EngineRegistration> registrations = new ArrayList<>();
        for (EngineDefinition definition : catalog.definitions()) {
            registrations.add(validate(definition, discovery, adapterProvider, diagnostics));
        }
        return new EngineRegistrySnapshot(registrations, diagnostics);
    }

    private static EngineRegistration validate(
            EngineDefinition definition,
            EngineDiscovery discovery,
            EngineAdapterProvider adapterProvider,
            List<EngineDiagnostic> diagnostics) {
        EngineDescriptor descriptor = definition.descriptor();
        Optional<String> version = discovery.installedVersion(descriptor.modId());
        if (version.isEmpty()) {
            return unavailable(
                    descriptor,
                    version,
                    CapabilityMatrix.none(),
                    EngineStatus.State.NOT_INSTALLED,
                    diagnostic(
                            EngineDiagnostic.Severity.INFO,
                            "ENGINE_NOT_INSTALLED",
                            descriptor.engineId() + " was not discovered",
                            Map.of(
                                    "modId", descriptor.modId(),
                                    "expected", descriptor.expectedVersion())),
                    diagnostics);
        }
        if (!definition.acceptsVersion(version.orElseThrow())) {
            return unavailable(
                    descriptor,
                    version,
                    CapabilityMatrix.none(),
                    EngineStatus.State.INVALID_VERSION,
                    diagnostic(
                            EngineDiagnostic.Severity.ERROR,
                            "ENGINE_VERSION_MISMATCH",
                            descriptor.engineId() + " version is not supported",
                            Map.of(
                                    "actual", version.orElseThrow(),
                                    "expected", String.join(",", definition.acceptedVersions()))),
                    diagnostics);
        }
        List<String> missingHooks = definition.hooks().stream()
                .filter(hook -> !discovery.hookPresent(hook))
                .toList();
        if (!missingHooks.isEmpty()) {
            return unavailable(
                    descriptor,
                    version,
                    CapabilityMatrix.none(),
                    EngineStatus.State.INVALID_HOOKS,
                    diagnostic(
                            EngineDiagnostic.Severity.ERROR,
                            "ENGINE_HOOK_MISSING",
                            descriptor.engineId() + " hook contract is unavailable",
                            Map.of(
                                    "version", version.orElseThrow(),
                                    "hooks", String.join(",", missingHooks))),
                    diagnostics);
        }

        EngineAdapterProvision provision;
        try {
            provision = Objects.requireNonNull(
                    adapterProvider.provide(definition),
                    "adapter provision");
        } catch (LinkageError | RuntimeException failure) {
            String failureClass = failure.getClass().getName();
            return unavailable(
                    descriptor,
                    version,
                    CapabilityMatrix.none(),
                    EngineStatus.State.INCOMPLETE,
                    diagnostic(
                            EngineDiagnostic.Severity.ERROR,
                            "ENGINE_ADAPTER_LINK_FAILED",
                            "candidate=" + descriptor.engineId()
                                    + ", version=" + version.orElseThrow()
                                    + ", expectedVersion=" + descriptor.expectedVersion()
                                    + ", hook=" + descriptor.hookContract()
                                    + ", failureClass=" + failureClass,
                            Map.of(
                                    "candidate", descriptor.engineId(),
                                    "version", version.orElseThrow(),
                                    "expectedVersion", descriptor.expectedVersion(),
                                    "hook", descriptor.hookContract(),
                                    "failureClass", failureClass)),
                    diagnostics);
        }

        CapabilityMatrix capabilities = provision.capabilities();
        Optional<EngineAdapter> adapter = provision.adapter();
        if (adapter.isEmpty() || !capabilities.isComplete()) {
            EngineDiagnostic diagnostic = provision.failure()
                    .map(value -> contextualize(value, descriptor, version.orElseThrow()))
                    .orElseGet(() -> diagnostic(
                            EngineDiagnostic.Severity.ERROR,
                            "ENGINE_CAPABILITY_INCOMPLETE",
                            descriptor.engineId() + " product capability hooks are incomplete",
                            Map.of(
                                    "version", version.orElseThrow(),
                                    "capabilities", "13x4")));
            return unavailable(
                    descriptor,
                    version,
                    capabilities,
                    EngineStatus.State.INCOMPLETE,
                    diagnostic,
                    diagnostics);
        }
        EngineAdapter selected = adapter.orElseThrow();
        if (!descriptor.equals(selected.descriptor())) {
            return unavailable(
                    descriptor,
                    version,
                    capabilities,
                    EngineStatus.State.INCOMPLETE,
                    diagnostic(
                            EngineDiagnostic.Severity.ERROR,
                            "ENGINE_CAPABILITY_INCOMPLETE",
                            descriptor.engineId() + " provider returned a different descriptor",
                            Map.of(
                                    "expected", descriptor.engineId(),
                                    "actual", selected.descriptor().engineId())),
                    diagnostics);
        }
        EngineDiagnostic ready = diagnostic(
                EngineDiagnostic.Severity.INFO,
                "ENGINE_ADAPTER_READY",
                descriptor.engineId() + " validated all 13x4 capability cells",
                Map.of("version", version.orElseThrow(), "capabilities", "13x4"));
        diagnostics.add(ready);
        return new EngineRegistration(
                descriptor,
                version,
                capabilities,
                new EngineStatus(EngineStatus.State.READY, List.of(ready)),
                Optional.of(selected));
    }

    private static EngineRegistration unavailable(
            EngineDescriptor descriptor,
            Optional<String> version,
            CapabilityMatrix capabilities,
            EngineStatus.State state,
            EngineDiagnostic diagnostic,
            List<EngineDiagnostic> diagnostics) {
        diagnostics.add(diagnostic);
        return new EngineRegistration(
                descriptor,
                version,
                capabilities,
                new EngineStatus(state, List.of(diagnostic)),
                Optional.empty());
    }

    private static EngineDiagnostic diagnostic(
            EngineDiagnostic.Severity severity,
            String code,
            String message,
            Map<String, String> details) {
        return new EngineDiagnostic(severity, code, message, details);
    }

    private static EngineDiagnostic contextualize(
            EngineDiagnostic source,
            EngineDescriptor descriptor,
            String version) {
        LinkedHashMap<String, String> details = new LinkedHashMap<>(source.details());
        details.putIfAbsent("candidate", descriptor.engineId());
        details.putIfAbsent("version", version);
        details.putIfAbsent("expectedVersion", descriptor.expectedVersion());
        details.putIfAbsent("hook", descriptor.hookContract());
        return new EngineDiagnostic(source.severity(), source.code(), source.message(), details);
    }
}
