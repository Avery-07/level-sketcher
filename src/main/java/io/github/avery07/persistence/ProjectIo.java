package io.github.avery07.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import io.github.avery07.model.symbol.SymbolLibrary;
import io.github.avery07.model.symbol.SymbolType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the native document format (JSON): the whole workspace — sheets, their
 * transform and bounds, layers, and every element (shapes and symbol instances). Kept out of the
 * model so the domain stays annotation-free; a Jackson tree is built and parsed by hand here.
 */
public final class ProjectIo {

    private static final int VERSION = 1;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProjectIo() {
    }

    public static void save(Document doc, Path path) throws IOException {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("version", VERSION);
        ArrayNode sheets = root.putArray("sheets");
        for (Sheet s : doc.workspace().sheets()) {
            sheets.add(sheetNode(s));
        }
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), root);
    }

    /** Replace the document's workspace contents with the file's. Caller resets file/dirty/undo. */
    public static void load(Path path, Document doc) throws IOException {
        JsonNode root = MAPPER.readTree(path.toFile());
        List<Sheet> loaded = new ArrayList<>();
        for (JsonNode sn : root.path("sheets")) {
            loaded.add(readSheet(sn, doc.symbolLibrary()));
        }
        doc.workspace().sheets().clear();
        doc.workspace().sheets().addAll(loaded);
    }

    // ----- write -----

    private static ObjectNode sheetNode(Sheet s) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("name", s.name());
        n.set("center", xy(s.center()));
        n.put("rotation", s.rotation());
        n.put("scale", s.scale());
        ArrayNode bounds = n.putArray("bounds");
        bounds.add(s.left()).add(s.top()).add(s.right()).add(s.bottom());
        n.put("activeLayer", s.layers().indexOf(s.activeLayer()));
        ArrayNode layers = n.putArray("layers");
        for (Layer l : s.layers()) {
            layers.add(layerNode(l));
        }
        return n;
    }

    private static ObjectNode layerNode(Layer l) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("name", l.name());
        n.put("visible", l.isVisible());
        ArrayNode elements = n.putArray("elements");
        for (Element e : l.elements()) {
            elements.add(elementNode(e));
        }
        return n;
    }

    private static ObjectNode elementNode(Element e) {
        ObjectNode n = MAPPER.createObjectNode();
        switch (e) {
            case EditablePolygon p -> {
                n.put("kind", "polygon");
                n.set("vertices", points(p.vertices()));
            }
            case Circle c -> {
                n.put("kind", "circle");
                n.set("center", xy(c.center()));
                n.put("radius", c.radius());
            }
            case FreehandStroke f -> {
                n.put("kind", "freehand");
                n.set("points", points(f.points()));
            }
            case SymbolInstance sym -> {
                n.put("kind", "symbol");
                n.put("type", sym.type().id());
                n.put("name", sym.name());
                n.set("anchors", points(sym.anchors()));
                ObjectNode params = n.putObject("params");
                sym.params().forEach(params::put);
            }
        }
        n.set("style", styleNode(e.style()));
        n.put("locked", e.isLocked());
        return n;
    }

    private static ObjectNode styleNode(Style s) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("stroke", s.stroke());
        if (s.fill() == null) {
            n.putNull("fill");
        } else {
            n.put("fill", s.fill());
        }
        n.put("strokeWidth", s.strokeWidth());
        return n;
    }

    private static ArrayNode points(List<Vec2> pts) {
        ArrayNode a = MAPPER.createArrayNode();
        for (Vec2 v : pts) {
            a.add(xy(v));
        }
        return a;
    }

    private static ArrayNode xy(Vec2 v) {
        return MAPPER.createArrayNode().add(v.x()).add(v.y());
    }

    // ----- read -----

    private static Sheet readSheet(JsonNode n, SymbolLibrary library) {
        double left = n.path("bounds").path(0).asDouble();
        double top = n.path("bounds").path(1).asDouble();
        double right = n.path("bounds").path(2).asDouble();
        double bottom = n.path("bounds").path(3).asDouble();
        Sheet s = new Sheet(n.path("name").asText("Sheet"), readVec(n.get("center")),
                right - left, bottom - top);
        s.setLeft(left);
        s.setTop(top);
        s.setRight(right);
        s.setBottom(bottom);
        s.setRotation(n.path("rotation").asDouble());
        s.setScale(n.path("scale").asDouble(1));
        s.layers().clear();
        for (JsonNode ln : n.path("layers")) {
            s.layers().add(readLayer(ln, library));
        }
        if (s.layers().isEmpty()) {
            s.layers().add(new Layer("Layer 1"));
        }
        int active = Math.max(0, Math.min(n.path("activeLayer").asInt(0), s.layers().size() - 1));
        s.setActiveLayer(s.layers().get(active));
        return s;
    }

    private static Layer readLayer(JsonNode n, SymbolLibrary library) {
        Layer l = new Layer(n.path("name").asText("Layer"));
        l.setVisible(n.path("visible").asBoolean(true));
        for (JsonNode en : n.path("elements")) {
            Element e = readElement(en, library);
            if (e != null) {
                l.addElement(e);
            }
        }
        return l;
    }

    private static Element readElement(JsonNode n, SymbolLibrary library) {
        Element e = switch (n.path("kind").asText()) {
            case "polygon" -> new EditablePolygon(readPoints(n.get("vertices")));
            case "circle" -> new Circle(readVec(n.get("center")), n.path("radius").asDouble());
            case "freehand" -> new FreehandStroke(readPoints(n.get("points")));
            case "symbol" -> readSymbol(n, library);
            default -> null;
        };
        if (e != null) {
            e.setStyle(readStyle(n.get("style")));
            e.setLocked(n.path("locked").asBoolean(false));
        }
        return e;
    }

    private static SymbolInstance readSymbol(JsonNode n, SymbolLibrary library) {
        SymbolType type = library.byId(n.path("type").asText());
        if (type == null) {
            return null; // unknown type (e.g. from a newer file)
        }
        SymbolInstance sym = new SymbolInstance(type, readPoints(n.get("anchors")));
        sym.setName(n.path("name").asText(type.name()));
        JsonNode params = n.path("params");
        params.fieldNames().forEachRemaining(k -> sym.params().put(k, params.path(k).asDouble()));
        return sym;
    }

    private static Style readStyle(JsonNode n) {
        if (n == null) {
            return Style.DEFAULT;
        }
        String fill = n.path("fill").isNull() ? null : n.path("fill").asText(null);
        return new Style(n.path("stroke").asText("#222222"), fill, n.path("strokeWidth").asDouble(2));
    }

    private static List<Vec2> readPoints(JsonNode arr) {
        List<Vec2> pts = new ArrayList<>();
        if (arr != null) {
            for (JsonNode p : arr) {
                pts.add(readVec(p));
            }
        }
        return pts;
    }

    private static Vec2 readVec(JsonNode arr) {
        return new Vec2(arr.path(0).asDouble(), arr.path(1).asDouble());
    }
}
