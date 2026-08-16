# LevelSketcher — Architecture

A desktop application for **drafting and concepting 2D game levels** (see the design
specification for product intent). This document captures the technical architecture and
the implementation roadmap.

## Tech stack

- **Java 21** + **JavaFX 21** (matches the JDK; JavaFX pulled in via Maven).
- Build/run via Maven and the `javafx-maven-plugin` (`mvn javafx:run`).
- **Jackson** for JSON persistence (polymorphic element hierarchy via `@JsonTypeInfo`).
- Image/JSON/SVG export uses built-in `SwingFXUtils` + `ImageIO`.
- UI built in Java code (no FXML) for tight integration with the custom canvas.

## Rendering approach

**Immediate-mode `Canvas`** (redraw the document via `GraphicsContext`), *not* the JavaFX
scene graph. Freehand strokes have many points, vertex editing needs full control, and
pan/zoom over many elements is smoother when we own the render loop. Cost: we implement
hit-testing and selection handles ourselves (owned by the `geometry` package).

Layout: a `StackPane` with two stacked canvases —

- **content canvas** — sheets + elements; redrawn on model change (render-on-demand via a
  dirty flag, no continuous animation loop).
- **overlay canvas** — selection handles, marquee, in-progress tool previews, grid;
  redraws on interaction without touching content.

A single **viewport transform** (`Affine` = pan × zoom) maps world↔screen. Coordinate
chain: `screen ← viewport ← sheet ← element(local)`. Elements are stored in sheet-local
coordinates; sheets are positioned in world space.

## Package structure

```
io.github.avery07
├── app/        Application entry point, main window, wiring
├── model/      Pure domain (NO JavaFX imports)
│   ├── Project, Workspace(canvas), Sheet, Layer, Group
│   ├── element/  Element, EditablePolygon, Circle, FreehandStroke,
│   │             SymbolInstance, TextElement, Arrow, ImageElement
│   └── symbol/   SymbolType, PlacementPattern, ParameterSchema
├── document/   Document (open project + selection + dirty state + change events)
├── command/    Command, UndoManager, concrete edit commands
├── geometry/   Vec2, Transform helpers, hit-testing, bounds
├── view/       CanvasView (input routing + inline rename editor), Viewport,
│   │           SheetGeometry, SheetHandles, SheetManipulator (transform math)
│   └── render/  WorkspaceRenderer (background, sheets, grid, selection + handles)
├── tool/       Tool interface, ToolManager, one class per tool
├── ui/         Toolbar, Inspector, LayerPanel, SymbolLibraryPanel, MenuBar
└── io/         Jackson serialization, save/load, image + JSON/SVG export
```

**Invariant:** `model/` never imports JavaFX — keeps the domain testable and
serialization clean.

## Key decisions

- **Model change propagation:** `Document` fires change events; `CanvasView` marks dirty
  and re-renders; `Inspector` refreshes. Lightweight listener/event bus, not per-field
  JavaFX bindings.
- **Undo/redo:** foundational from day one. Every mutation goes through a `Command` routed
  by `UndoManager`. (Not in the spec, but non-negotiable for an editor.)
- **Tools:** `Tool` interface (`onPress/onDrag/onRelease/onKey`, world coords). `ToolManager`
  tracks the active tool. Select, Rectangle, Circle, Polygon, Freehand, Text, Arrow,
  VertexEdit, and a generic `SymbolPlacementTool` driven by the symbol's placement pattern.
- **Symbols:** v1 ships **built-in** types (spawn, sight cone, patrol path, danger zone…)
  defined in code against the schema-driven `SymbolType` model. The GUI type editor is v2,
  but the data model supports it now so we don't rewrite later.
- **Sheet resize:** default **fixed content, frame clips/reveals**; scale-content optional
  later.
- **Image storage:** v1 embeds image data as base64 in the project file (self-contained,
  no broken references). Relative-path option deferred.

## Roadmap

| Phase | Deliverable |
|---|---|
| **0 — Foundation** | JavaFX build runs; empty window; package skeleton; `Document`/`UndoManager`/`Viewport` scaffolding |
| **1 — Canvas & sheets** | Pan/zoom canvas; create/move/resize/delete sheets; render-on-demand |
| **2 — Shapes** | Rectangle, circle, n-gon, freehand; select/move tool; delete |
| **3 — Editing** | Vertex move, edge move, edge subdivide; circle handles; transforms (rotate/scale/duplicate) |
| **4 — Inspector & layers** | Inspector panel (live param edits); layers within sheets (visibility/order) |
| **5 — Symbols** | Built-in symbol library; 4 placement patterns; symbol instances + params |
| **6 — Annotation & images** | Text, arrows, image import/transform |
| **7 — Grouping & locking** | Group/ungroup, lock/unlock |
| **8 — Persistence & export** | Native JSON save/load, dirty indicator; PNG + JSON/SVG export |
