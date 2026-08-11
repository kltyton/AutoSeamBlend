package com.kltyton.autoseamblend.frontend.model;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 中文：目标库的一行不可变方块视图数据。
 *
 * English: Immutable block-row view data for the target library.
 */
public record TargetRowView(
        String entryKey,
        String entryId,
        Optional<String> receiverBlockId,
        Component displayName,
        ItemStack icon,
        EngineFamily family,
        ConnectionMethod method,
        boolean compatibility,
        boolean managed,
        boolean configured,
        boolean previewEnabled,
        boolean paintEnabled,
        boolean propertiesEnabled,
        boolean editable) {
    public TargetRowView {
        if (entryKey == null
                || entryKey.isBlank()
                || entryId == null
                || entryId.isEmpty()) {
            throw new IllegalArgumentException(
                    "target-row key must be nonblank and display id nonempty");
        }
        receiverBlockId = Objects.requireNonNull(
                receiverBlockId,
                "receiverBlockId");
        receiverBlockId.ifPresent(blockId -> {
            if (blockId.isBlank()) {
                throw new IllegalArgumentException(
                        "receiver block id must not be blank");
            }
        });
        Objects.requireNonNull(
                displayName,
                "displayName");
        icon = Objects.requireNonNull(
                        icon,
                        "icon")
                .copy();
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(method, "method");
    }

    /**
     * 中文：兼容现有属性控件的只读访问器；无接收方时返回空串，绝不回退到显示 id。
     *
     * English: Read-only compatibility accessor for existing property widgets.
     * It returns an empty string without a receiver and never falls back to the
     * display id.
     */
    public String blockId() {
        return receiverBlockId.orElse("");
    }

    /**
     * 中文：防止 ItemStack 的可变组件逃逸出不可变行模型。
     *
     * English: Prevents mutable ItemStack components from escaping the
     * immutable row model.
     */
    @Override
    public ItemStack icon() {
        return icon.copy();
    }
}
