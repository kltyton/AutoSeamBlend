package com.kltyton.autoseamblend.neoforge.frontend.uilib.entry;

import com.kltyton.autoseamblend.foundation.Constants;
import com.kltyton.autoseamblend.neoforge.frontend.uilib.controller.UilibWorkbenchController;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** 中文：NeoForge UILib 工作台可发现的键盘和加入消息入口。 / English: Discoverable keyboard and join-message entry points for the NeoForge UILib workbench. */
public final class UilibWorkbenchEntry {
    private static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(
                    Identifier.fromNamespaceAndPath(
                            Constants.MOD_ID,
                            "workbench"));
    private static final KeyMapping OPEN_WORKBENCH =
            new KeyMapping(
                    "key.autoseamblend.open_workbench",
                    InputConstants.KEY_O,
                    CATEGORY);
    private static boolean hintShown;

    private UilibWorkbenchEntry() {}

    public static void register(
            RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_WORKBENCH);
    }

    public static void onClientTick(
            ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            return;
        }
        if (OPEN_WORKBENCH.consumeClick()) {
            open(minecraft);
            return;
        }
    }

    private static void open(Minecraft minecraft) {
        try {
            UilibWorkbenchController.open();
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
