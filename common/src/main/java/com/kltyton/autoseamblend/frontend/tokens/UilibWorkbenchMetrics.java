package com.kltyton.autoseamblend.frontend.tokens;

/**
 * 中文：UILib 工作台共享的 4px 网格、外壳尺寸和响应式断点令牌。
 *
 * English: Shared 4px-grid, shell-size, and responsive-breakpoint tokens for
 * the UILib workbench.
 */
public final class UilibWorkbenchMetrics {
    public static final int GRID = 4;
    public static final int SCREEN_MARGIN = GRID * 2;
    public static final int PANEL_GAP = GRID * 2;
    public static final int HEADER_HEIGHT = GRID * 5;
    public static final int FOOTER_HEIGHT = GRID * 7;
    public static final int STATUS_BAR_HEIGHT = GRID * 3;
    public static final int CONTROL_HEIGHT = GRID * 5;
    public static final int NARROW_WIDTH = 360;

    private UilibWorkbenchMetrics() {}
}
