package com.kltyton.autoseamblend.frontend.uilib.component.paint;

import com.daqem.uilib.api.widget.IWidget;
import com.kltyton.autoseamblend.authoring.workbench.PaintTool;
import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDraftFields;
import com.kltyton.autoseamblend.frontend.controller.UilibWorkbenchController;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.PaintPixel;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.PaintStrokeEnded;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.PaintStrokeStarted;
import com.kltyton.autoseamblend.frontend.model.PaintViewModel;
import com.kltyton.autoseamblend.frontend.model.WorkbenchViewLease;
import com.kltyton.autoseamblend.frontend.paint.TexturePaintDocument;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchTheme;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * 中文：Fabric 与 NeoForge 共用的静态 RGBA 面画布。
 *
 * English: Shared Fabric and NeoForge static RGBA face canvas.
 */
public class TextureCanvasWidget
        extends AbstractWidget
        implements IWidget {
    private static final int LEFT_BUTTON = 0;

    private final PaintSession session;
    private int scale;
    private int lastStrokeX = Integer.MIN_VALUE;
    private int lastStrokeY = Integer.MIN_VALUE;
    private boolean persistentStrokeChanged;
    private boolean strokeActive;

    public TextureCanvasWidget(
            int width,
            int height,
            TexturePaintDocument document,
            Runnable changed,
            Runnable colorPicked) {
        this(
                width,
                height,
                new DocumentPaintSession(
                        document,
                        changed,
                        colorPicked));
    }

    public <T extends WorkbenchDraftFields> TextureCanvasWidget(
            int width,
            int height,
            UilibWorkbenchController<T> controller,
            PaintViewModel paint,
            WorkbenchViewLease lease) {
        this(
                width,
                height,
                new ControllerPaintSession<>(
                        controller,
                        paint,
                        lease));
    }

    private TextureCanvasWidget(
            int width,
            int height,
            PaintSession session) {
        super(
                0,
                0,
                width,
                height,
                Component.translatable(
                        "gui.autoseamblend.paint.canvas.narration"));
        this.session = Objects.requireNonNull(
                session,
                "session");
        scale = Math.max(
                1,
                Math.min(
                        12,
                        Math.min(
                                Math.max(
                                        1,
                                        (width - 16)
                                                / session.width()),
                                Math.max(
                                        1,
                                        (height - 16)
                                                / session.height()))));
        active = session.initiallyActive();
    }

    @Override
    protected void extractWidgetRenderState(
            @NotNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        graphics.fill(
                getX(),
                getY(),
                getX() + getWidth(),
                getY() + getHeight(),
                UilibWorkbenchTheme.SURFACE_INPUT);
        int originX = originX();
        int originY = originY();
        int firstX = Math.max(
                0,
                Math.floorDiv(
                        getX() - originX,
                        scale));
        int firstY = Math.max(
                0,
                Math.floorDiv(
                        getY() - originY,
                        scale));
        int lastX = Math.min(
                session.width() - 1,
                Math.floorDiv(
                        getX() + getWidth() - 1
                                - originX,
                        scale));
        int lastY = Math.min(
                session.height() - 1,
                Math.floorDiv(
                        getY() + getHeight() - 1
                                - originY,
                        scale));
        graphics.enableScissor(
                getX(),
                getY(),
                getX() + getWidth(),
                getY() + getHeight());
        graphics.fill(
                originX + firstX * scale,
                originY + firstY * scale,
                originX + (lastX + 1) * scale,
                originY + (lastY + 1) * scale,
                UilibWorkbenchTheme.SURFACE_PANEL);
        for (int y = firstY;
                y <= lastY;
                y++) {
            for (int x = firstX;
                    x <= lastX;
                    x++) {
                int left = originX + x * scale;
                int top = originY + y * scale;
                if (((x + y) & 1) == 0) {
                    graphics.fill(
                            left,
                            top,
                            left + scale,
                            top + scale,
                            UilibWorkbenchTheme.SURFACE_RAISED);
                }
            }
            drawColorRuns(
                    graphics,
                    originX,
                    originY,
                    y,
                    firstX,
                    lastX);
        }
        if (scale >= 6) {
            drawGrid(
                    graphics,
                    originX,
                    originY,
                    firstX,
                    firstY,
                    lastX,
                    lastY);
        }
        graphics.disableScissor();
        int border = isFocused()
                ? UilibWorkbenchTheme.FOCUS_RING
                : UilibWorkbenchTheme.BORDER_DEFAULT;
        graphics.horizontalLine(
                getX(),
                getX() + getWidth() - 1,
                getY(),
                border);
        graphics.verticalLine(
                getX(),
                getY(),
                getY() + getHeight() - 1,
                border);
        graphics.horizontalLine(
                getX(),
                getX() + getWidth() - 1,
                getY() + getHeight() - 1,
                border);
        graphics.verticalLine(
                getX() + getWidth() - 1,
                getY(),
                getY() + getHeight() - 1,
                border);
    }

    /**
     * 中文：把同一扫描行中连续且同色的不透明像素合并为一次填充提交。
     *
     * English: Coalesces consecutive opaque pixels of the same color on one
     * scanline into a single fill submission.
     */
    private void drawColorRuns(
            GuiGraphicsExtractor graphics,
            int originX,
            int originY,
            int y,
            int firstX,
            int lastX) {
        int runStart = firstX;
        int runColor = session.colorAt(firstX, y);
        for (int x = firstX + 1;
                x <= lastX + 1;
                x++) {
            int color = x <= lastX
                    ? session.colorAt(x, y)
                    : 0;
            if (color == runColor) {
                continue;
            }
            if ((runColor >>> 24) != 0) {
                graphics.fill(
                        originX + runStart * scale,
                        originY + y * scale,
                        originX + x * scale,
                        originY + (y + 1) * scale,
                        runColor);
            }
            runStart = x;
            runColor = color;
        }
    }

    /**
     * 中文：网格按可见行列绘制，不在每个像素内部重复提交相邻边界。
     *
     * English: Draws the grid by visible rows and columns instead of
     * resubmitting shared edges inside every pixel.
     */
    private void drawGrid(
            GuiGraphicsExtractor graphics,
            int originX,
            int originY,
            int firstX,
            int firstY,
            int lastX,
            int lastY) {
        int left = originX + firstX * scale;
        int right = originX + (lastX + 1) * scale - 1;
        int top = originY + firstY * scale;
        int bottom = originY + (lastY + 1) * scale - 1;
        for (int y = firstY;
                y <= lastY + 1;
                y++) {
            graphics.horizontalLine(
                    left,
                    right,
                    originY + y * scale,
                    UilibWorkbenchTheme.BORDER_SUBTLE);
        }
        for (int x = firstX;
                x <= lastX + 1;
                x++) {
            graphics.verticalLine(
                    originX + x * scale,
                    top,
                    bottom,
                    UilibWorkbenchTheme.BORDER_SUBTLE);
        }
    }

    @Override
    protected boolean isValidClickButton(
            MouseButtonInfo button) {
        return button.button() == LEFT_BUTTON;
    }

    @Override
    public void onClick(
            MouseButtonEvent event,
            boolean doubleClick) {
        if (event.button() != LEFT_BUTTON
                || !active
                || !session.beginStroke()) {
            return;
        }
        strokeActive = true;
        lastStrokeX = Integer.MIN_VALUE;
        lastStrokeY = Integer.MIN_VALUE;
        persistentStrokeChanged = false;
        paint(event.x(), event.y());
    }

    @Override
    public void onRelease(
            MouseButtonEvent event) {
        if (event.button() != LEFT_BUTTON
                || !strokeActive) {
            return;
        }
        session.endStroke(persistentStrokeChanged);
        strokeActive = false;
        lastStrokeX = Integer.MIN_VALUE;
        lastStrokeY = Integer.MIN_VALUE;
        persistentStrokeChanged = false;
    }

    @Override
    protected void onDrag(
            MouseButtonEvent event,
            double deltaX,
            double deltaY) {
        if (strokeActive
                && active
                && session.canContinueStroke()
                && event.button() == LEFT_BUTTON
                && (session.tool() == PaintTool.BRUSH
                        || session.tool()
                                == PaintTool.ERASER)) {
            paint(event.x(), event.y());
        }
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double horizontal,
            double vertical) {
        if (!session.current()
                || !isMouseOver(mouseX, mouseY)
                || vertical == 0.0D) {
            return false;
        }
        scale = Math.max(
                1,
                Math.min(
                        24,
                        scale
                                + (vertical > 0.0D
                        ? 1
                        : -1)));
        return true;
    }

    private void paint(
            double mouseX,
            double mouseY) {
        if (!session.current()) {
            cancelStroke();
            return;
        }
        int pixelX =
                (int) Math.floor(
                        (mouseX - originX())
                                / scale);
        int pixelY =
                (int) Math.floor(
                        (mouseY - originY())
                                / scale);
        if (pixelX < 0
                || pixelY < 0
                || pixelX >= session.width()
                || pixelY >= session.height()) {
            lastStrokeX = Integer.MIN_VALUE;
            lastStrokeY = Integer.MIN_VALUE;
            return;
        }
        if (pixelX == lastStrokeX
                && pixelY == lastStrokeY) {
            return;
        }
        if (lastStrokeX != Integer.MIN_VALUE
                && (session.tool() == PaintTool.BRUSH
                        || session.tool()
                                == PaintTool.ERASER)) {
            interpolateStroke(
                    lastStrokeX,
                    lastStrokeY,
                    pixelX,
                    pixelY);
        } else {
            applyPixel(pixelX, pixelY);
        }
        if (strokeActive) {
            lastStrokeX = pixelX;
            lastStrokeY = pixelY;
        }
    }

    /**
     * 中文：用整数线段补齐相邻鼠标采样之间的像素，避免快速拖动留下断点。
     *
     * English: Fills pixels between adjacent pointer samples with an integer
     * line so a fast brush or eraser drag cannot leave gaps.
     */
    private void interpolateStroke(
            int startX,
            int startY,
            int endX,
            int endY) {
        int x = startX;
        int y = startY;
        int dx = Math.abs(endX - startX);
        int stepX = startX < endX ? 1 : -1;
        int dy = -Math.abs(endY - startY);
        int stepY = startY < endY ? 1 : -1;
        int error = dx + dy;
        while (x != endX || y != endY) {
            if (!session.current()) {
                cancelStroke();
                return;
            }
            int doubled = error * 2;
            if (doubled >= dy) {
                error += dy;
                x += stepX;
            }
            if (doubled <= dx) {
                error += dx;
                y += stepY;
            }
            applyPixel(x, y);
            if (!strokeActive) {
                return;
            }
        }
    }

    private void applyPixel(
            int pixelX,
            int pixelY) {
        PixelResult result = session.apply(
                pixelX,
                pixelY);
        if (result == PixelResult.CHANGED) {
            persistentStrokeChanged = true;
        } else if (result == PixelResult.REJECTED) {
            cancelStroke();
        }
    }

    private void cancelStroke() {
        strokeActive = false;
        lastStrokeX = Integer.MIN_VALUE;
        lastStrokeY = Integer.MIN_VALUE;
    }

    private int originX() {
        return getX()
                + (getWidth()
                                - session.width()
                                        * scale)
                        / 2;
    }

    private int originY() {
        return getY()
                + (getHeight()
                                - session.height()
                                        * scale)
                        / 2;
    }

    @Override
    protected void updateWidgetNarration(
            @NotNull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    private enum PixelResult {
        CHANGED,
        UNCHANGED,
        REJECTED
    }

    private interface PaintSession {
        int width();

        int height();

        int colorAt(int x, int y);

        PaintTool tool();

        boolean initiallyActive();

        boolean current();

        boolean beginStroke();

        boolean canContinueStroke();

        PixelResult apply(int x, int y);

        void endStroke(boolean changed);
    }

    private static final class DocumentPaintSession
            implements PaintSession {
        private final TexturePaintDocument document;
        private final Runnable changed;
        private final Runnable colorPicked;

        private DocumentPaintSession(
                TexturePaintDocument document,
                Runnable changed,
                Runnable colorPicked) {
            this.document = Objects.requireNonNull(
                    document,
                    "document");
            this.changed = Objects.requireNonNull(
                    changed,
                    "changed");
            this.colorPicked = Objects.requireNonNull(
                    colorPicked,
                    "colorPicked");
        }

        @Override
        public int width() {
            return document.width();
        }

        @Override
        public int height() {
            return document.height();
        }

        @Override
        public int colorAt(
                int x,
                int y) {
            return document.colorAt(x, y);
        }

        @Override
        public PaintTool tool() {
            return document.tool();
        }

        @Override
        public boolean initiallyActive() {
            return true;
        }

        @Override
        public boolean current() {
            return true;
        }

        @Override
        public boolean beginStroke() {
            document.beginStroke();
            return true;
        }

        @Override
        public boolean canContinueStroke() {
            return true;
        }

        @Override
        public PixelResult apply(
                int x,
                int y) {
            if (document.apply(x, y)) {
                return PixelResult.CHANGED;
            }
            if (document.tool() == PaintTool.PICKER) {
                colorPicked.run();
            }
            return PixelResult.UNCHANGED;
        }

        @Override
        public void endStroke(boolean changedPixels) {
            document.endStroke();
            if (changedPixels) {
                changed.run();
            }
        }
    }

    private static final class ControllerPaintSession<
                    T extends WorkbenchDraftFields>
            implements PaintSession {
        private final UilibWorkbenchController<T> controller;
        private final PaintViewModel paint;
        private final WorkbenchViewLease lease;
        private final int width;
        private final int height;

        private ControllerPaintSession(
                UilibWorkbenchController<T> controller,
                PaintViewModel paint,
                WorkbenchViewLease lease) {
            this.controller = Objects.requireNonNull(
                    controller,
                    "controller");
            this.paint = Objects.requireNonNull(
                    paint,
                    "paint");
            this.lease = Objects.requireNonNull(
                    lease,
                    "lease");
            this.width = paint.width();
            this.height = paint.height();
        }

        /** 中文：租约有效且目标尺寸一致时返回当前不可变绘画快照，跨目标/尺寸变化安全拒绝。 / English: Returns the live immutable paint snapshot only while the lease is valid and the target dimensions match; cross-target or resized states are rejected. */
        private PaintViewModel currentPaint() {
            if (!lease.accepts(
                    controller.layoutGeneration(),
                    controller.view().mode())) {
                return null;
            }
            PaintViewModel live = controller.view()
                    .paint()
                    .orElse(null);
            if (live == null
                    || live.width() != width
                    || live.height() != height) {
                return null;
            }
            return live;
        }

        @Override
        public int width() {
            PaintViewModel live = currentPaint();
            return live != null
                    ? live.width()
                    : paint.width();
        }

        @Override
        public int height() {
            PaintViewModel live = currentPaint();
            return live != null
                    ? live.height()
                    : paint.height();
        }

        @Override
        public int colorAt(
                int x,
                int y) {
            PaintViewModel live = currentPaint();
            return live != null
                    ? live.colorAt(x, y)
                    : paint.colorAt(x, y);
        }

        @Override
        public PaintTool tool() {
            PaintViewModel live = currentPaint();
            return live != null
                    ? live.tool()
                    : paint.tool();
        }

        @Override
        public boolean initiallyActive() {
            return canContinueStroke();
        }

        @Override
        public boolean current() {
            return currentPaint() != null;
        }

        @Override
        public boolean beginStroke() {
            return canContinueStroke()
                    && controller.dispatch(
                            new PaintStrokeStarted());
        }

        @Override
        public boolean canContinueStroke() {
            PaintViewModel live = currentPaint();
            return live != null
                    && controller.view().canSubmit()
                    && live.editable();
        }

        @Override
        public PixelResult apply(
                int x,
                int y) {
            if (!current()
                    || !controller.dispatch(
                            new PaintPixel(x, y))) {
                return PixelResult.REJECTED;
            }
            return PixelResult.CHANGED;
        }

        @Override
        public void endStroke(boolean changedPixels) {
            if (current()) {
                controller.dispatch(
                        new PaintStrokeEnded());
            }
        }
    }
}
