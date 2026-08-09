package com.kltyton.autoseamblend.neoforge.bootstrap;

import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.neoforge.frontend.uilib.entry.UilibWorkbenchEntry;
import com.kltyton.autoseamblend.selection.compiled.SelectorGenerationLifecycle;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** 中文：仅在完整的游戏注册表可用时刷新标签选择器。 / English: Refreshes tag selectors only when a complete play registry is available. */
public final class NeoForgeClientLifecycle {
    private NeoForgeClientLifecycle() {}

    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        RuleRuntime.bindPlayRegistries(
                event.getPlayer().level().registryAccess());
        UilibWorkbenchEntry.showHintOnce(
                event.getPlayer());
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SelectorGenerationLifecycle.unbindPlayRegistries();
    }
}
