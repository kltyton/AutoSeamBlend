package com.kltyton.autoseamblend.frontend.uilib.component;

import com.daqem.uilib.client.gui.component.AbstractComponent;
import com.daqem.uilib.client.gui.texture.Texture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 中文：UILib 9.0.0 的 renderBase 已按组件局部坐标平移 pose；本基类在 render 内把 pose 平移
 * 回父原点，使既有画布组件保留以 getX()/getY() 绝对坐标绘制的实现。
 *
 * English: UILib 9.0.0's renderBase translates the pose into component-local space; this base
 * translates back to the parent origin inside render() so existing canvas widgets keep drawing
 * with their getX()/getY() absolute-coordinate implementation.
 */
public abstract class AbstractCanvasComponent<T extends AbstractCanvasComponent<T>>
        extends AbstractComponent<T> {
    protected boolean active = true;

    protected AbstractCanvasComponent(
            int x,
            int y,
            int width,
            int height) {
        super(
                new Texture(
                        ResourceLocation.withDefaultNamespace(
                                "textures/gui/widgets.png"),
                        0,
                        0,
                        1,
                        1),
                x,
                y,
                width,
                height);
    }

    @Override
    public final void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float delta) {
        graphics.pose().pushPose();
        graphics.pose().translate(
                -getX(),
                -getY(),
                0);
        extractWidgetRenderState(
                graphics,
                mouseX,
                mouseY,
                delta);
        graphics.pose().popPose();
    }

    protected void extractWidgetRenderState(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float delta) {
    }
}
