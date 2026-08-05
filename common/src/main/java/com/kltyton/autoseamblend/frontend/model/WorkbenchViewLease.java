package com.kltyton.autoseamblend.frontend.model;

import com.kltyton.autoseamblend.authoring.workbench.WorkbenchMode;
import java.util.Objects;

/**
 * 中文：把一个 UILib 控件绑定到不可变工作台发布版本和模式。
 *
 * English: Binds a UILib widget to one immutable workbench publication and
 * mode.
 */
public record WorkbenchViewLease(
        long publicationVersion,
        WorkbenchMode mode) {
    public WorkbenchViewLease {
        if (publicationVersion < 0) {
            throw new IllegalArgumentException(
                    "publication version must be nonnegative");
        }
        Objects.requireNonNull(mode, "mode");
    }

    /**
     * 中文：只允许仍处于同一发布版本和模式的控件处理输入。
     *
     * English: Allows input only while the widget remains on the same
     * publication version and mode.
     */
    public boolean accepts(
            long currentPublicationVersion,
            WorkbenchMode currentMode) {
        return publicationVersion == currentPublicationVersion
                && mode == currentMode;
    }
}
