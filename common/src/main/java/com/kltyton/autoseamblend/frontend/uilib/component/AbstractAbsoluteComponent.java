package com.kltyton.autoseamblend.frontend.uilib.component;

import com.daqem.uilib.client.gui.component.AbstractComponent;
import com.daqem.uilib.client.gui.texture.Texture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 中文：UILib 9.0.0 的 renderBase 会按组件局部坐标逐级平移 pose；本基类在 render 内按
 * getTotalX()/getTotalY() 把 pose 平移回屏幕原点，使既有组件保留以绝对坐标绘制的实现，
 * 并且嵌套在滚动面板/滚动内容中时不再重复叠加祖先平移。
 *
 * English: UILib 9.0.0's renderBase translates the pose into component-local space at every
 * level; this base translates back to the screen origin by getTotalX()/getTotalY() inside
 * render() so existing components keep drawing with their absolute-coordinate implementation
 * and never double-apply ancestor translations when nested inside scroll panels/content.
 */
public abstract class AbstractAbsoluteComponent<T extends AbstractAbsoluteComponent<T>>
        extends AbstractComponent<T> {
    protected boolean active = true;

    protected AbstractAbsoluteComponent(
            int x,
            int y,
            int width,
            int height) {
        super(
                new Texture(
                        new ResourceLocation(
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
                -getTotalX(),
                -getTotalY(),
                0);
        extractRenderState(
                graphics,
                mouseX,
                mouseY,
                delta);
        graphics.pose().popPose();
    }

    protected void extractRenderState(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float delta) {
    }
}
