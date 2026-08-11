package com.kltyton.autoseamblend.engine.registry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kltyton.autoseamblend.engine.EngineAdapter;
import com.kltyton.autoseamblend.engine.EngineDescriptor;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.capability.CapabilityMatrix;
import com.kltyton.autoseamblend.engine.plan.NativeMethodMapping;
import com.kltyton.autoseamblend.engine.query.ConnectionQuery;
import com.kltyton.autoseamblend.engine.query.EngineQueryContext;
import com.kltyton.autoseamblend.engine.query.QueryObservation;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EngineRegistryRuntimeStateTest {
    @Test
    void freezesReadyAdaptersAndFamiliesAtConstruction() {
        EngineAdapter adapter = new StubAdapter();
        EngineRegistration registration = new EngineRegistration(
                adapter.descriptor(),
                Optional.of("1.0.0"),
                CapabilityMatrix.complete(),
                new EngineStatus(EngineStatus.State.READY, List.of()),
                Optional.of(adapter));
        EngineRegistrySnapshot registry =
                new EngineRegistrySnapshot(List.of(registration), List.of());
        EngineSelection selection = new EngineSelection(
                EngineStatus.State.SELECTED,
                Optional.of(adapter),
                "test",
                List.of());

        EngineRegistryRuntimeState runtime =
                new EngineRegistryRuntimeState(registry, selection);

        assertSame(runtime.readyAdapters(), runtime.readyAdapters());
        assertSame(runtime.readyEngineIds(), runtime.readyEngineIds());
        assertSame(EngineFamily.MCPATCHER, runtime.family("stub"));
        assertFalse(runtime.engineRequired());
        assertThrows(IllegalArgumentException.class, () -> runtime.family("missing"));
    }

    private static final class StubAdapter implements EngineAdapter {
        private static final EngineDescriptor DESCRIPTOR = new EngineDescriptor(
                "stub",
                EngineFamily.MCPATCHER,
                "mcpatcher",
                "stub",
                "1.0.0",
                "test");

        @Override
        public EngineDescriptor descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public CapabilityMatrix capabilities() {
            return CapabilityMatrix.complete();
        }

        @Override
        public QueryObservation observe(
                ConnectionQuery query,
                EngineQueryContext nativeContext) {
            throw new UnsupportedOperationException();
        }

        @Override
        public NativeMethodMapping mapping(ConnectionMethod method) {
            throw new UnsupportedOperationException();
        }
    }
}
