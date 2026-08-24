# LevelSketcher

A desktop sketching tool for game‑level design. Draw shapes and freehand lines, place typed
symbols (POI, zones, sight cones, routes), and organize your work across movable sheets and
layers.

Built with JavaFX on Java 21.

## Download & run

Get the latest build from the [**Releases**](https://github.com/Avery-07/level-sketcher/releases) page.

**No Java required** — download the bundle for your OS, unzip, and launch:

| OS | File | Run |
|----|------|-----|
| Windows | `LevelSketcher-windows.zip` | unzip → `LevelSketcher/LevelSketcher.exe` |
| macOS | `LevelSketcher-macos.zip` | unzip → open `LevelSketcher.app` |
| Linux | `LevelSketcher-linux.zip` | unzip → `LevelSketcher/bin/LevelSketcher` |

> The bundles are **unsigned**, so the OS will warn about an unknown developer the first time:
> - **Windows:** SmartScreen → *More info → Run anyway*.
> - **macOS:** right‑click the app → *Open* → *Open*.

**Already have Java 21?** Download `LevelSketcher-<os>.jar` and run:

```bash
java -jar LevelSketcher-<os>.jar
```

## Build from source

Requires **JDK 21+** and **Maven**.

```bash
mvn javafx:run                     # run directly
mvn clean package                  # build target/LevelSketcher.jar
java -jar target/LevelSketcher.jar # run the built jar
```

## Releasing (maintainer)

Pushing a version tag builds the three OS bundles and publishes a Release automatically:

```bash
git tag v1.0.0
git push origin v1.0.0
```
