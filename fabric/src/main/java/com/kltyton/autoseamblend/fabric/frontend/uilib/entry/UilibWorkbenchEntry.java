package com.kltyton.autoseamblend.fabric.frontend.uilib.entry;

import com.kltyton.autoseamblend.fabric.frontend.uilib.controller.FabricWorkbenchController;
import com.kltyton.autoseamblend.foundation.Constants;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * 中文：Fabric UILib 工作台可发现的键盘与加入消息入口。打开入口是按键
 * （默认 O，与 NeoForge 一致）与 /autoseamblend 命令。
 *
 * English: Discoverable keyboard and join-message entry points for the Fabric
 * UILib workbench. The open entries are a keybinding (default O, matching
 * NeoForge) and the /autoseamblend command.
 */
public final class UilibWorkbenchEntry {
    private static final String CATEGORY =
            "key.categories.autoseamblend";
    private static final KeyMapping OPEN_WORKBENCH =
            new KeyMapping(
                    "key.autoseamblend.open_workbench",
                    InputConstants.KEY_O,
                    CATEGORY);
    private static boolean hintShown;

    private UilibWorkbenchEntry() {}

    public static void registerKeyMapping() {
        KeyBindingHelper.registerKeyBinding(OPEN_WORKBENCH);
        ClientTickEvents.END_CLIENT_TICK.register(
                UilibWorkbenchEntry::onClientTick);
    }

    private static void onClientTick(
            Minecraft minecraft) {
        if (minecraft.screen != null
                || !OPEN_WORKBENCH.consumeClick()) {
            return;
        }
        try {
            FabricWorkbenchController.open();
        } catch (RuntimeException exception) {
            Constants.LOG.warn(
                    "UILib workbench key entry failed",
                    exception);
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(
                        Component.translatable(
                                "command.autoseamblend.workbench_failed",
                                exception.getClass()
                                        .getSimpleName()));
            }
        }
    }

    public static void showHintOnce(
            LocalPlayer player) {
        if (hintShown) {
            return;
        }
        hintShown = true;
        player.sendSystemMessage(
                Component.translatable(
                        "message.autoseamblend.open_workbench",
                        OPEN_WORKBENCH.getTranslatedKeyMessage(),
                        "/" + Constants.COMMAND_ROOT));
    }
}
