package io.github.avery07.view;

import io.github.avery07.geometry.Rect;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Sheet;
import io.github.avery07.view.render.WorkspaceRenderer;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Layout and hit-testing for the small numbered layer tabs shown next to a sheet's name label.
 * Each tab selects that sheet's active layer — the only layer shown — so switching feels like
 * flipping to a separate page. Positions are in screen space and computed just once here, so the
 * renderer (drawing) and the view (click handling) always agree on where each tab is.
 */
public final class LayerTabs {

    public static final double W = 22;
    public static final double H = 16;
    private static final double GAP = 2;
    private static final double PAD = 8;       // gap from the name to the first tab
    private static final double CONNECT = 1;   // overlap into the frame so the active tab connects

    private static final Text MEASURER = new Text();

    static {
        MEASURER.setFont(WorkspaceRenderer.LABEL_FONT);
    }

    /** One tab: the layer index it selects and its screen-space rectangle. */
    public record Tab(int index, Rect screen) {
    }

    private LayerTabs() {
    }

    /** Screen-space tab rectangles for a sheet, one per layer, left to right after the name. */
    public static List<Tab> tabs(Sheet s, Viewport vp) {
        Vec2 tl = vp.toScreen(SheetGeometry.localToWorld(s, s.left(), s.top()));
        MEASURER.setText(s.name());
        double x0 = tl.x() + WorkspaceRenderer.LABEL_DX + MEASURER.getLayoutBounds().getWidth() + PAD;
        double y = tl.y() + CONNECT - H; // bottom sits on (just into) the frame edge, like a tab
        int n = s.layers().size();
        List<Tab> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double x = x0 + i * (W + GAP);
            out.add(new Tab(i, new Rect(x, y, x + W, y + H)));
        }
        return out;
    }

    /** The layer index whose tab is under the screen point, or {@code -1} if none. */
    public static int hit(Sheet s, Viewport vp, double sx, double sy) {
        for (Tab t : tabs(s, vp)) {
            Rect r = t.screen();
            if (sx >= r.minX() && sx <= r.maxX() && sy >= r.minY() && sy <= r.maxY()) {
                return t.index();
            }
        }
        return -1;
    }
}
