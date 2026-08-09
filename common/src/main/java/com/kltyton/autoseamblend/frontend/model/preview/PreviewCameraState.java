package com.kltyton.autoseamblend.frontend.model.preview;

import com.kltyton.autoseamblend.frontend.model.PreviewViewModel;

/**
 * 中文：预览画布共享的相机交互状态；Loader 控件只消费快照，不重复维护旋转、平移和缩放边界。
 * English: Shared preview-canvas camera interaction state; Loader widgets consume snapshots
 * instead of maintaining duplicate rotation, pan, and zoom bounds.
 */
public final class PreviewCameraState {
    private static final float DEFAULT_YAW = -35.0F;
    private static final float DEFAULT_PITCH = 24.0F;
    private static final float DEFAULT_ZOOM = 1.0F;
    private static final float MIN_PITCH = -70.0F;
    private static final float MAX_PITCH = 70.0F;
    private static final float MIN_ZOOM = 0.65F;
    private static final float MAX_ZOOM = 1.75F;

    private float yaw = DEFAULT_YAW;
    private float pitch = DEFAULT_PITCH;
    private float panX;
    private float panY;
    private float zoom = DEFAULT_ZOOM;
    private long revision;

    /** 中文：按 NeoForge 已验收的灵敏度旋转相机。 / English: Rotates with the accepted NeoForge sensitivity. */
    public void rotate(double deltaX, double deltaY) {
        yaw += (float) deltaX * 0.7F;
        pitch = clamp(
                pitch + (float) deltaY * 0.55F,
                MIN_PITCH,
                MAX_PITCH);
        revision = Math.addExact(revision, 1);
    }

    public void pan(double deltaX, double deltaY) {
        panX += (float) deltaX;
        panY += (float) deltaY;
        revision = Math.addExact(revision, 1);
    }

    /** 中文：滚轮使用稳定的加法缩放步长。 / English: Wheel zoom uses a stable additive step. */
    public boolean zoom(double delta) {
        float next = clamp(
                zoom + (float) delta * 0.1F,
                MIN_ZOOM,
                MAX_ZOOM);
        if (next == zoom) {
            return false;
        }
        zoom = next;
        revision = Math.addExact(revision, 1);
        return true;
    }

    public void reset() {
        if (yaw != DEFAULT_YAW
                || pitch != DEFAULT_PITCH
                || panX != 0.0F
                || panY != 0.0F
                || zoom != DEFAULT_ZOOM) {
            revision = Math.addExact(revision, 1);
        }
        yaw = DEFAULT_YAW;
        pitch = DEFAULT_PITCH;
        panX = 0.0F;
        panY = 0.0F;
        zoom = DEFAULT_ZOOM;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public float panX() {
        return panX;
    }

    public float panY() {
        return panY;
    }

    public float zoom() {
        return zoom;
    }

    public long revision() {
        return revision;
    }

    public PreviewViewModel.Camera snapshot() {
        return new PreviewViewModel.Camera(
                yaw,
                pitch,
                zoom,
                panX,
                panY);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
