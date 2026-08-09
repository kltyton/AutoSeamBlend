package com.kltyton.autoseamblend.reload.rule;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** 中文：四种原生格式共享的不可变扩展解析结果。 / English: Immutable shared extension parse result for all four native formats. */
public record ParsedRuleDocument(
        EngineFamily family,
        String entryId,
        String documentPath,
        String resourceId,
        ConnectionMethod requestedMethod,
        boolean compatibility,
        List<String> targetBlockIds,
        Optional<String> jsonSource) {
    public ParsedRuleDocument {
        Objects.requireNonNull(family, "family");
        if (entryId == null || entryId.isEmpty()) {
            throw new IllegalArgumentException(
                    "rule document entry id must not be empty");
        }
        if (documentPath == null || documentPath.isBlank()) {
            throw new IllegalArgumentException(
                    "rule document path must not be blank");
        }
        if (resourceId == null
                || ResourceLocation.tryParse(resourceId) == null) {
            throw new IllegalArgumentException(
                    "invalid rule document resource id");
        }
        Objects.requireNonNull(requestedMethod, "requestedMethod");
        targetBlockIds = List.copyOf(
                Objects.requireNonNull(targetBlockIds, "targetBlockIds"));
        if (targetBlockIds.stream().anyMatch(
                target -> target == null
                        || ResourceLocation.tryParse(target) == null)) {
            throw new IllegalArgumentException(
                    "invalid rule document target block id");
        }
        jsonSource = Objects.requireNonNull(jsonSource, "jsonSource");
    }

    /** 中文：返回防御性重建的 JSON 对象。 / English: Returns a defensively reconstructed JSON object. */
    public Optional<JsonObject> jsonObject() {
        return jsonSource.map(source ->
                JsonParser.parseString(source).getAsJsonObject());
    }
}
