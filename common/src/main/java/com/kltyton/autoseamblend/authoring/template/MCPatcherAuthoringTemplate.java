package com.kltyton.autoseamblend.authoring.template;

import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringFile;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringRule;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.selection.method.MethodSlotDomain;
import java.util.ArrayList;
import java.util.List;

/** 中文：创建原生 MCPatcher properties 文档，但不创建任何纹理文件。 / English: Creates a native MCPatcher properties document without creating any texture files. */
final class MCPatcherAuthoringTemplate {
    private MCPatcherAuthoringTemplate() {}

    static List<ManagedAuthoringFile> create(
            ManagedAuthoringRule rule) {
        StringBuilder source = new StringBuilder();
        property(source, "id", rule.targetBlockId());
        if (rule.requestedMethod() == ConnectionMethod.AUTO) {
            property(source, "method", "auto");
            if (!rule.compatibility()) {
                property(source, "compatibility", "false");
            }
            return document(rule, source);
        }
        property(source, "matchBlocks", rule.targetBlockId());
        if (rule.pane()) {
            property(source, "matchTiles", rule.sourceTextureId());
            property(source, "faces", "sides");
        }
        property(source, "connect", "block");
        property(source, "connectBlocks", rule.targetBlockId());
        property(
                source,
                "method",
                rule.requestedMethod().serializedName());
        source.append(
                "# AutoSeamBlend generated slots / AutoSeamBlend 生成槽位\n");
        property(source, "tiles", tiles(rule));
        if (isOverlay(rule.resolvedMethod())) {
            property(source, "layer", "cutout");
            property(source, "tintBlock", rule.targetBlockId());
        }
        property(
                source,
                "compatibility",
                Boolean.toString(rule.compatibility()));
        return document(rule, source);
    }

    /** 中文：AUTO 创作文档只保存用户意图；匹配面、连接对象与槽位由同次重载事实构造内存执行视图。 / English: AUTO authoring stores only user intent; matching faces, connection donors, and slots come from the same reload's in-memory execution view. */
    private static List<ManagedAuthoringFile> document(
            ManagedAuthoringRule rule,
            StringBuilder source) {
        String path = "assets/" + rule.targetNamespace()
                + "/optifine/ctm/autoseamblend/"
                + rule.targetPath() + ".properties";
        return List.of(
                ManagedAuthoringFile.utf8(
                        path,
                        source.toString()));
    }

    private static String tiles(ManagedAuthoringRule rule) {
        if (rule.resolvedMethod() == ConnectionMethod.NONE) {
            return "<skip>";
        }
        int count = MethodSlotDomain.of(
                        rule.resolvedMethod())
                .slots()
                .size();
        ArrayList<String> ids = new ArrayList<>(count);
        String prefix =
                "autoseamblend:generated/continuity/"
                + rule.resolvedMethod().serializedName()
                + '/'
                + rule.textureNamespace()
                + '/'
                + rule.texturePath()
                + '/';
        for (int slot = 0; slot < count; slot++) {
            ids.add(prefix + slot);
        }
        return String.join(" ", ids);
    }

    private static boolean isOverlay(
            ConnectionMethod method) {
        return method == ConnectionMethod.RUNTIME_BLEND
                || method == ConnectionMethod.OVERLAY
                || method == ConnectionMethod.OVERLAY_CTM;
    }

    private static void property(
            StringBuilder output,
            String key,
            String value) {
        output.append(key)
                .append('=')
                .append(value)
                .append('\n');
    }
}
