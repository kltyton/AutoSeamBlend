package com.kltyton.autoseamblend.frontend.uilib.component.property;

import com.kltyton.autoseamblend.frontend.model.NativePropertiesViewModel.SelectorCandidate;
import com.kltyton.autoseamblend.frontend.uilib.component.AbstractAbsoluteComponent;
import com.kltyton.autoseamblend.frontend.uilib.widget.BlockChipWidget;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 中文：把通用属性候选条目放入 UILib 滚动容器；注册表与 Loader 转换已在候选 DTO 边界完成。
 *
 * English: Places a common property candidate inside a UILib scroll container;
 * registry and Loader conversion are complete before the candidate DTO reaches
 * this component.
 */
public final class NativePropertyBlockChipComponent
        extends AbstractAbsoluteComponent<NativePropertyBlockChipComponent> {
    public NativePropertyBlockChipComponent(
            int width,
            SelectorCandidate candidate,
            boolean removable,
            Runnable action) {
        super(0, 0, width, 32);
        Objects.requireNonNull(candidate, "candidate");
        addChild(new BlockChipWidget(
                width,
                candidate.icon(),
                candidate.displayName(),
                candidate.blockId(),
                removable,
                action));
    }

    @Override
    protected void extractRenderState(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        // 中文：条目控件负责绘制，本组件只提供滚动布局边界。
        // English: The chip owns rendering; this component only supplies the scroll bounds.
    }
}
