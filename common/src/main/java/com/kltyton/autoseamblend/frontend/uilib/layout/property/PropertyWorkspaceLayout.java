package com.kltyton.autoseamblend.frontend.uilib.layout.property;

import com.kltyton.autoseamblend.frontend.model.TargetRowView;
import java.util.List;
import java.util.Objects;

/**
 * 中文：统一属性工作区的装配边界；Loader 只提供状态/原生对象适配器。
 *
 * English: Unifies property-workspace assembly; Loaders provide only state and
 * native-object adapters.
 */
public final class PropertyWorkspaceLayout<P, C> {
    private final Composer<P, C> composer;

    public PropertyWorkspaceLayout(Composer<P, C> composer) {
        this.composer = Objects.requireNonNull(composer, "composer");
    }

    public void assemble(
            TargetRowView row,
            P properties,
            List<C> candidates,
            int top,
            int footerTop,
            Runnable back,
            Runnable preview,
            Runnable paint) {
        composer.layout(
                Objects.requireNonNull(row, "row"),
                properties,
                List.copyOf(Objects.requireNonNull(candidates, "candidates")),
                top,
                footerTop,
                Objects.requireNonNull(back, "back"),
                Objects.requireNonNull(preview, "preview"),
                Objects.requireNonNull(paint, "paint"));
    }

    /** 中文：Loader 面板的唯一渲染回调。 / English: Sole rendering callback for a Loader panel. */
    @FunctionalInterface
    public interface Composer<P, C> {
        void layout(
                TargetRowView row,
                P properties,
                List<C> candidates,
                int top,
                int footerTop,
                Runnable back,
                Runnable preview,
                Runnable paint);
    }
}
