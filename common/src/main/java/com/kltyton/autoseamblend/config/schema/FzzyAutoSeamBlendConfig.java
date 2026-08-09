package com.kltyton.autoseamblend.config.schema;

import com.kltyton.autoseamblend.foundation.Constants;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import me.fzzyhmstrs.fzzy_config.api.FileType;
import me.fzzyhmstrs.fzzy_config.api.RegisterType;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.entry.Entry;
import me.fzzyhmstrs.fzzy_config.validation.collection.ValidatedStringMap;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedChoice;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedString;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 中文：版本共用的玩家配置 schema；common 运行时负责注册、加载和快照冻结，Loader 入口仅决定启动时机。
 *
 * English: Version-shared player configuration schema. The common runtime owns registration,
 * loading, and snapshot freezing; loader entry points choose only bootstrap timing.
 */
public final class FzzyAutoSeamBlendConfig extends Config {
    public static final List<String> METHODS = List.of(
            "auto", "runtime_blend", "ctm", "ctm_compact", "horizontal", "vertical",
            "horizontal_vertical", "vertical_horizontal", "top", "overlay", "overlay_ctm",
            "fixed", "none");
    public static final List<String> RESOURCE_PACK_MODES =
            List.of("compatibility", "non-compatibility");
    private static volatile FzzyAutoSeamBlendConfig active;

    public ValidatedBoolean automaticDiscovery = new ValidatedBoolean(true);

    public ValidatedStringMap<Map<String, List<String>>> targets = new ValidatedStringMap<>(
            emptySelectorMap(),
            localizedChoice("auto", METHODS, "method"),
            targetModeEntry());

    public ValidatedStringMap<Map<String, List<String>>> excludedTargets =
            new ValidatedStringMap<>(
                    emptySelectorMap(),
                    localizedChoice("auto", METHODS, "method"),
                    targetModeEntry());

    public FzzyAutoSeamBlendConfig() {
        super(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, Constants.MOD_ID));
    }

    public static FzzyAutoSeamBlendConfig registerAndLoad() {
        FzzyAutoSeamBlendConfig loaded = ConfigApiJava.registerAndLoadConfig(
                FzzyAutoSeamBlendConfig::new, RegisterType.CLIENT);
        active = loaded;
        return loaded;
    }

    public static FzzyAutoSeamBlendConfig active() {
        FzzyAutoSeamBlendConfig value = active;
        if (value == null) {
            throw new IllegalStateException("Fzzy config has not been registered");
        }
        return value;
    }

    @Override
    public FileType fileType() {
        return FileType.JSON5;
    }

    private static Map<String, Map<String, List<String>>> emptySelectorMap() {
        LinkedHashMap<String, Map<String, List<String>>> targets = new LinkedHashMap<>();
        for (String method : METHODS) {
            targets.put(method, targetModes(List.of(), List.of()));
        }
        return targets;
    }

    private static Map<String, List<String>> targetModes(
            List<String> compatibility,
            List<String> nonCompatibility) {
        LinkedHashMap<String, List<String>> modes = new LinkedHashMap<>();
        modes.put("compatibility", List.copyOf(compatibility));
        modes.put("non-compatibility", List.copyOf(nonCompatibility));
        return modes;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Entry<Map<String, List<String>>, ?> targetModeEntry() {
        return (Entry) new ValidatedStringMap<List<String>>(
                targetModes(List.of(), List.of()),
                localizedChoice("compatibility", RESOURCE_PACK_MODES, "bucket"),
                stringListEntry());
    }

    private static Entry<String, ?> localizedChoice(
            String defaultValue,
            List<String> values,
            String group) {
        return new ValidatedChoice<>(
                defaultValue,
                values,
                new ValidatedString(),
                (value, ignored) -> Component.translatable(
                        "config.autoseamblend." + group + "." + value),
                (value, ignored) -> Component.empty());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Entry<List<String>, ?> stringListEntry() {
        return (Entry) new ValidatedString().toList(List.of());
    }
}
