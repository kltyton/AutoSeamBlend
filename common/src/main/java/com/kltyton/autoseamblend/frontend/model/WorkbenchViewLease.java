package com.kltyton.autoseamblend.frontend.model;

import com.kltyton.autoseamblend.authoring.workbench.WorkbenchMode;
import java.util.Objects;

/**
 * 中文：把一个 UILib 控件绑定到工作台布局代次和模式；控件树重建才递增代次，
 * 绘画像素发布不使当前画布租约失效。
 *
 * English: Binds a UILib widget to one workbench layout generation and mode.
 * The generation advances only when the widget tree is rebuilt, so pixel
 * publications never invalidate the active canvas lease.
 */
public record WorkbenchViewLease(
        long layoutGeneration,
        WorkbenchMode mode) {
    public WorkbenchViewLease {
        if (layoutGeneration < 0) {
            throw new IllegalArgumentException(
                    "layout generation must be nonnegative");
        }
        Objects.requireNonNull(mode, "mode");
    }

    /**
     * 中文：只允许仍处于同一布局代次和模式的控件处理输入。
     *
     * English: Allows input only while the widget remains on the same layout
     * generation and mode.
     */
    public boolean accepts(
            long currentLayoutGeneration,
            WorkbenchMode currentMode) {
        return layoutGeneration == currentLayoutGeneration
                && mode == currentMode;
    }
}
