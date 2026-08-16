package io.github.avery07.view;

import io.github.avery07.geometry.Vec2;
import javafx.scene.transform.Affine;

/**
 * Maps between world coordinates and screen (canvas) coordinates via a pan + zoom
 * transform: {@code screen = world * zoom + pan}. Pan is stored in screen pixels, zoom is a
 * unitless scale.
 */
public final class Viewport {

    private static final double MIN_ZOOM = 0.05;
    private static final double MAX_ZOOM = 40.0;

    private double panX = 0;
    private double panY = 0;
    private double zoom = 1.0;

    public double zoom() {
        return zoom;
    }

    public double panX() {
        return panX;
    }

    public double panY() {
        return panY;
    }

    /** Pan by a delta expressed in screen pixels. */
    public void panBy(double dxScreen, double dyScreen) {
        panX += dxScreen;
        panY += dyScreen;
    }

    /** Zoom by a factor while keeping the given screen point fixed (zoom-to-cursor). */
    public void zoomAt(double factor, double screenX, double screenY) {
        double newZoom = clamp(zoom * factor, MIN_ZOOM, MAX_ZOOM);
        double applied = newZoom / zoom;
        // Keep the world point currently under (screenX, screenY) stationary.
        panX = screenX - (screenX - panX) * applied;
        panY = screenY - (screenY - panY) * applied;
        zoom = newZoom;
    }

    public Vec2 toScreen(Vec2 world) {
        return new Vec2(world.x() * zoom + panX, world.y() * zoom + panY);
    }

    public Vec2 toWorld(Vec2 screen) {
        return new Vec2((screen.x() - panX) / zoom, (screen.y() - panY) / zoom);
    }

    /** The transform to install on a {@code GraphicsContext} before drawing world content. */
    public Affine toAffine() {
        Affine a = new Affine();
        a.appendTranslation(panX, panY);
        a.appendScale(zoom, zoom);
        return a;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
