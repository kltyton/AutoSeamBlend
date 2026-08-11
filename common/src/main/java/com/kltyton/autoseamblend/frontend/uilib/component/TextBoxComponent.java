package com.kltyton.autoseamblend.frontend.uilib.component;

import com.daqem.uilib.client.gui.component.AbstractComponent;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * 中文：1.20.1 编辑框适配层：包装原版 EditBox，提供新版 UILib TextBoxComponent 的
 * setHint/setMaxLength/setResponder/getValue/setValue/setEditable 表面，并把原版键盘/
 * 鼠标焦点输入能力完整暴露给工作台 Screen。
 *
 * English: 1.20.1 edit-box adapter wrapping vanilla EditBox and exposing the newer UILib
 * TextBoxComponent surface (setHint/setMaxLength/setResponder/getValue/setValue/setEditable)
 * while retaining vanilla keyboard/mouse focus input.
 */
public final class TextBoxComponent
        extends AbstractComponent<TextBoxComponent> {
    private final EditBox editBox;

    public TextBoxComponent(
            int x,
            int y,
            int width,
            int height,
            String initialValue) {
        super(
                null,
                x,
                y,
                width,
                height);
        this.editBox = new EditBox(
                Minecraft.getInstance().font,
                x,
                y,
                width,
                height,
                Component.empty());
        this.editBox.setValue(initialValue);
        this.editBox.setMaxLength(128);
    }

    public void setHint(Component hint) {
        editBox.setHint(hint);
    }

    public void setMaxLength(int maxLength) {
        editBox.setMaxLength(maxLength);
    }

    public void setResponder(Consumer<String> responder) {
        editBox.setResponder(responder);
    }

    public void setValue(String value) {
        editBox.setValue(value);
    }

    public String getValue() {
        return editBox.getValue();
    }

    public void setEditable(boolean editable) {
        editBox.setEditable(editable);
    }

    public boolean isFocused() {
        return editBox.isFocused();
    }

    public void setFocused(boolean focused) {
        editBox.setFocused(focused);
    }

    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button) {
        editBox.setX(getTotalX());
        editBox.setY(getTotalY());
        editBox.setWidth(getWidth());
        return editBox.mouseClicked(
                mouseX,
                mouseY,
                button);
    }

    public boolean charTyped(
            char codePoint,
            int modifiers) {
        return editBox.charTyped(
                codePoint,
                modifiers);
    }

    public boolean keyPressed(
            int keyCode,
            int scanCode,
            int modifiers) {
        return editBox.keyPressed(
                keyCode,
                scanCode,
                modifiers);
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float delta) {
        editBox.setX(getTotalX());
        editBox.setY(getTotalY());
        editBox.setWidth(getWidth());
        // 中文：UILib 0.3.6 的 renderBase 已按本组件坐标平移 pose，而原版
        // EditBox 使用屏幕绝对坐标。先抵消 UILib 平移，避免输入框显示位置与
        // mouseClicked 的绝对命中区域分离。
        // English: UILib 0.3.6 renderBase has already translated the pose by this
        // component, while vanilla EditBox draws in absolute screen coordinates.
        // Cancel that translation so rendering and absolute mouse hit-testing agree.
        graphics.pose().pushPose();
        graphics.pose().translate(
                -getTotalX(),
                -getTotalY(),
                0.0F);
        try {
            editBox.render(
                    graphics,
                    mouseX,
                    mouseY,
                    delta);
        } finally {
            graphics.pose().popPose();
        }
    }
}
