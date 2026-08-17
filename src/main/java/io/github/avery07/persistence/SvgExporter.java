package io.github.avery07.persistence;

import io.github.avery07.document.Document;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Layer;
import io.github.avery07.model.Sheet;
import io.github.avery07.model.Style;
import io.github.avery07.model.element.Circle;
import io.github.avery07.model.element.EditablePolygon;
import io.github.avery07.model.element.Element;
import io.github.avery07.model.element.FreehandStroke;
import io.github.avery07.model.element.SymbolInstance;

import java.util.List;
import java.util.Locale;

/**
 * Renders the workspace to an SVG string (spec §7.9) — a vector reference others can open in a
 * browser or editor. Everything is resolved to world coordinates (SVG's y-down axis matches
 * ours). The local→world transform is computed here directly so this stays free of JavaFX.
 */
public final class SvgExporter {

    private static final double PAD = 24;

    private SvgExporter() {
    }

    public static String export(Document doc) {
        Bounds b = new Bounds();
        StringBuilder body = new StringBuilder();

        for (Sheet s : doc.workspace().sheets()) {
            List<Vec2> corners = List.of(
                    world(s, s.left(), s.top()), world(s, s.right(), s.top()),
                    world(s, s.right(), s.bottom()), world(s, s.left(), s.bottom()));
            for (Vec2 c : corners) {
                b.record(c);
            }
            body.append("  <polygon points=\"").append(pointList(corners))
                    .append("\" fill=\"#ffffff\" stroke=\"#c2c6cd\" stroke-width=\"1\"/>\n");
            Vec2 tl = corners.get(0);
            body.append(text(tl.x(), tl.y() - 6, s.name(), "#6b7280"));

            for (Layer layer : s.layers()) {
                if (layer.isVisible()) {
                    for (Element e : layer.elements()) {
                        element(body, b, s, e);
                    }
                }
            }
        }

        double w = Math.max(1, b.maxX - b.minX) + 2 * PAD;
        double h = Math.max(1, b.maxY - b.minY) + 2 * PAD;
        double x = b.minX - PAD;
        double y = b.minY - PAD;
        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"")
                .append(f(x)).append(' ').append(f(y)).append(' ').append(f(w)).append(' ').append(f(h))
                .append("\" width=\"").append(f(w)).append("\" height=\"").append(f(h)).append("\">\n");
        svg.append("  <rect x=\"").append(f(x)).append("\" y=\"").append(f(y))
                .append("\" width=\"").append(f(w)).append("\" height=\"").append(f(h))
                .append("\" fill=\"#ebecef\"/>\n");
        svg.append(body).append("</svg>\n");
        return svg.toString();
    }

    private static void element(StringBuilder out, Bounds b, Sheet s, Element e) {
        Style style = e.style();
        switch (e) {
            case EditablePolygon p -> out.append(polygon(worldPoints(s, p.vertices(), b),
                    fill(style), style.stroke(), style.strokeWidth(), 1));
            case Circle c -> {
                Vec2 ctr = world(s, c.center().x(), c.center().y());
                double r = c.radius() * s.scale();
                b.record(new Vec2(ctr.x() - r, ctr.y() - r));
                b.record(new Vec2(ctr.x() + r, ctr.y() + r));
                out.append(String.format(Locale.ROOT,
                        "  <circle cx=\"%s\" cy=\"%s\" r=\"%s\" fill=\"%s\" stroke=\"%s\" stroke-width=\"%s\"/>%n",
                        f(ctr.x()), f(ctr.y()), f(r), fill(style), style.stroke(), f(style.strokeWidth())));
            }
            case FreehandStroke fr -> out.append(polyline(worldPoints(s, fr.points(), b),
                    style.stroke(), style.strokeWidth()));
            case SymbolInstance sym -> symbol(out, b, s, sym);
        }
    }

    private static void symbol(StringBuilder out, Bounds b, Sheet s, SymbolInstance sym) {
        String color = sym.style().stroke();
        Vec2 a = world(s, sym.anchors().get(0).x(), sym.anchors().get(0).y());
        switch (sym.type().pattern()) {
            case MARKER -> {
                double r = SymbolInstance.MARKER_RADIUS * s.scale();
                b.record(new Vec2(a.x() - r, a.y() - r));
                b.record(new Vec2(a.x() + r, a.y() + r));
                out.append(String.format(Locale.ROOT,
                        "  <circle cx=\"%s\" cy=\"%s\" r=\"%s\" fill=\"%s\" fill-opacity=\"0.9\" stroke=\"#ffffff\"/>%n",
                        f(a.x()), f(a.y()), f(r), color));
                out.append(text(a.x() + r + 4, a.y() + 4, sym.name(), color));
            }
            case PARAMETRIC -> {
                out.append(filledPolygon(worldPoints(s, conePoints(sym), b), color, 0.18));
                out.append(text(a.x() + 4, a.y() - 4, sym.name(), color));
            }
            case PATH -> {
                out.append(polyline(worldPoints(s, sym.anchors(), b), color, 2));
                out.append(text(a.x() + 4, a.y() - 4, sym.name(), color));
            }
            case REGION -> {
                out.append(filledPolygon(worldPoints(s, sym.anchors(), b), color, 0.15));
                out.append(text(a.x() + 4, a.y() - 4, sym.name(), color));
            }
        }
    }

    private static List<Vec2> conePoints(SymbolInstance sym) {
        Vec2 a = sym.anchors().get(0);
        double facing = Math.toRadians(sym.param("facing"));
        double half = Math.toRadians(sym.param("fov")) / 2;
        double range = sym.param("range");
        List<Vec2> pts = new java.util.ArrayList<>();
        pts.add(a);
        int segments = 24;
        for (int i = 0; i <= segments; i++) {
            double t = facing - half + (2 * half) * i / segments;
            pts.add(new Vec2(a.x() + range * Math.cos(t), a.y() + range * Math.sin(t)));
        }
        return pts;
    }

    private static List<Vec2> worldPoints(Sheet s, List<Vec2> local, Bounds b) {
        List<Vec2> out = new java.util.ArrayList<>(local.size());
        for (Vec2 v : local) {
            Vec2 w = world(s, v.x(), v.y());
            b.record(w);
            out.add(w);
        }
        return out;
    }

    /** Sheet-local → world, matching SheetGeometry (translate·rotate·scale·-frameCentre). */
    private static Vec2 world(Sheet s, double lx, double ly) {
        double px = (lx - s.frameCenterX()) * s.scale();
        double py = (ly - s.frameCenterY()) * s.scale();
        double c = Math.cos(s.rotation());
        double sn = Math.sin(s.rotation());
        return new Vec2(s.center().x() + px * c - py * sn, s.center().y() + px * sn + py * c);
    }

    private static String polygon(List<Vec2> pts, String fill, String stroke, double width, double def) {
        return String.format(Locale.ROOT,
                "  <polygon points=\"%s\" fill=\"%s\" stroke=\"%s\" stroke-width=\"%s\"/>%n",
                pointList(pts), fill, stroke, f(width));
    }

    private static String filledPolygon(List<Vec2> pts, String color, double opacity) {
        return String.format(Locale.ROOT,
                "  <polygon points=\"%s\" fill=\"%s\" fill-opacity=\"%s\" stroke=\"%s\" stroke-width=\"1.5\"/>%n",
                pointList(pts), color, f(opacity), color);
    }

    private static String polyline(List<Vec2> pts, String stroke, double width) {
        return String.format(Locale.ROOT,
                "  <polyline points=\"%s\" fill=\"none\" stroke=\"%s\" stroke-width=\"%s\"/>%n",
                pointList(pts), stroke, f(width));
    }

    private static String text(double x, double y, String s, String color) {
        return String.format(Locale.ROOT,
                "  <text x=\"%s\" y=\"%s\" font-family=\"sans-serif\" font-size=\"12\" fill=\"%s\">%s</text>%n",
                f(x), f(y), color, escape(s));
    }

    private static String pointList(List<Vec2> pts) {
        StringBuilder sb = new StringBuilder();
        for (Vec2 v : pts) {
            sb.append(f(v.x())).append(',').append(f(v.y())).append(' ');
        }
        return sb.toString().trim();
    }

    private static String fill(Style style) {
        return style.fill() == null ? "none" : style.fill();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static final class Bounds {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

        void record(Vec2 v) {
            minX = Math.min(minX, v.x());
            minY = Math.min(minY, v.y());
            maxX = Math.max(maxX, v.x());
            maxY = Math.max(maxY, v.y());
        }
    }
}
