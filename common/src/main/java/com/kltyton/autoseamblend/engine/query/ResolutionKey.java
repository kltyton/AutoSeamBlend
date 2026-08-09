package com.kltyton.autoseamblend.engine.query;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.selection.query.QueryMethodOwner;
import com.kltyton.autoseamblend.selection.query.QueryPolicyOwner;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** 中文：一个具体解析计划在当前代次内的完整标识。 / English: Complete generation-local identity of one concrete resolution plan. */
public record ResolutionKey(
        long generation,
        String reloadToken,
        String engineId,
        Optional<QueryMethodOwner> methodOwner,
        Optional<QueryPolicyOwner> policyOwner,
        String targetId,
        String connectionGroup,
        Map<String, String> stateIdentity,
        SurfaceFace face,
        String spriteId,
        ConnectionMethod requestedMethod) {
    public ResolutionKey {
        if (generation < 0) throw new IllegalArgumentException("generation must be non-negative");
        requireText(reloadToken, "reloadToken");
        requireText(engineId, "engineId");
        methodOwner = Objects.requireNonNull(methodOwner, "methodOwner");
        policyOwner = Objects.requireNonNull(policyOwner, "policyOwner");
        requireText(targetId, "targetId");
        requireText(connectionGroup, "connectionGroup");
        TreeMap<String, String> sortedState = new TreeMap<>(
                Objects.requireNonNull(stateIdentity, "stateIdentity"));
        sortedState.forEach((name, value) -> {
            requireText(name, "state property name");
            requireText(value, "state property value");
        });
        stateIdentity = Collections.unmodifiableMap(sortedState);
        Objects.requireNonNull(face, "face");
        requireText(spriteId, "spriteId");
        Objects.requireNonNull(requestedMethod, "requestedMethod");
    }

    public ConnectionQuery query() {
        return new ConnectionQuery(targetId, stateIdentity, face, spriteId, requestedMethod);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
