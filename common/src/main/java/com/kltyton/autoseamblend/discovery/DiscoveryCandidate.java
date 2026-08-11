package com.kltyton.autoseamblend.discovery;

import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet.ResourcePackMode;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 中文：仅存储在发现快照中的同 ID 隐式自连接候选。 / English: One implicit same-ID self-connection candidate stored only in a discovery snapshot. */
public record DiscoveryCandidate(
        String targetId,
        String connectionGroup,
        ConnectionMethod method,
        List<FaceFacts> faces,
        Set<ResourcePackMode> excludedModes) {
    public DiscoveryCandidate {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(connectionGroup, "connectionGroup");
        method = Objects.requireNonNull(method, "method");
        if (method != ConnectionMethod.AUTO) {
            throw new IllegalArgumentException("implicit discovery candidates must use auto");
        }
        faces = List.copyOf(Objects.requireNonNull(faces, "faces"));
        if (faces.isEmpty()) throw new IllegalArgumentException("candidate must have at least one face");
        excludedModes = Set.copyOf(Objects.requireNonNull(excludedModes, "excludedModes"));
    }

    public static DiscoveryCandidate implicitSelf(
            String targetId,
            List<FaceFacts> faces,
            Set<ResourcePackMode> excludedModes) {
        return new DiscoveryCandidate(
                targetId,
                "block:" + targetId,
                ConnectionMethod.AUTO,
                faces,
                excludedModes);
    }

    public boolean availableFor(ResourcePackMode mode) {
        return !excludedModes.contains(Objects.requireNonNull(mode, "mode"));
    }
}
