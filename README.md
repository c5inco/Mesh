![Screenshot of desktop app](artwork/screenshot.png)

# Mesh

A desktop editor for creating mesh gradients in Compose, built with [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) and the [Jewel UI standalone toolkit](https://github.com/JetBrains/jewel). Gradients are rendered using Compose's official [`MeshGradientPainter`](https://developer.android.com/develop/ui/compose/graphics/draw/mesh-gradient), with bicubic color interpolation and automatically inferred Bezier control points.

## Features and controls

- **Grid:** Adjust between 2–64 rows and columns, or distribute points evenly.
- **Control points:** Drag points on the canvas or edit `X`/`Y` offsets in the side panel. Double-click the canvas to hide or show points, and toggle edge constraints to lock perimeter points to the canvas bounds.
- **Palette:** Use the global preset colors, add custom colors to the document, assign colors per point.
- **Canvas:** Configure canvas dimensions (fixed size or fill), background color, and blur.
- **Undo/redo:** Undo with ⌘Z (Ctrl+Z) and redo with ⇧⌘Z (Ctrl+Shift+Z or Ctrl+Y). Covers point, grid, blur, background, and custom color edits, including ones an agent makes.
- **Export:** Copy Kotlin code for an official `MeshGradientPainter` and a `meshGradientModifier` that includes the current blur and required imports. Apply it to a sized Composable with `Modifier.then(meshGradientModifier)`, or export a PNG at 1x, 2x, or 3x scale. Both live in the export control at the end of the title bar, which names the pixel size it will produce.
- **Documents:** New/Open/Save work with `.mesh` files. A document holds its points, grid, canvas settings, background color, and custom colors by value, so it opens the same on any machine. Saved documents autosave to their `.mesh` file after each edit. Closing, quitting, New, or Open never silently drops edits. Mesh reopens the last document at launch, and on the first launch after upgrading, the mesh saved by earlier versions becomes `~/.mesh/Documents/My Mesh.mesh`.
- **Agent access (MCP):** Coding agents can read and edit the open mesh document through an MCP server. See [below](#agent-access-mcp).

### Roadmap

- [ ] Grain overlay
- [ ] Presets
- [ ] Gradient animation

## Agent access (MCP)

Mesh can serve an [MCP](https://modelcontextprotocol.io) server over streamable HTTP so an agent can edit the open mesh document you are looking at.

| Tool | What it does |
| --- | --- |
| `get_mesh` | Returns the open document's name and file, the grid, canvas settings, saved colors with their IDs, and each point's position and color; at most 256 points per call, so a bigger grid needs a `rowStart`/`rowEnd`/`colStart`/`colEnd` region holding at most 256 points, and a larger region is rejected |
| `set_grid` | Changes the grid size (2–64 rows and columns) and/or blur (0–1) as one undoable edit |
| `update_point` | Moves and/or recolors the point at a row and column |
| `update_points` | Moves and/or recolors up to 4,096 points in a single undoable edit |
| `add_color` | Adds a color to the document's custom palette and returns its ID |
| `undo` / `redo` | Undoes or redoes the last edit, whether you or the agent made it |
| `render_png` | Renders the mesh to a PNG so the agent can see the result |

Example of connecting with Claude Code:

```bash
claude mcp add --transport http mesh http://127.0.0.1:7777/mcp
```

Other clients take the same URL as a remote (HTTP) MCP server.

## Running and packaging

Run from the command line (or use the `.run/desktopApp.run.xml` run configuration in IntelliJ IDEA):

```bash
./gradlew :composeApp:run
```

Build an unsigned macOS DMG for local testing:

```bash
./gradlew :composeApp:packageDmg
```

Build a signed and notarized release DMG (see [docs/RELEASING.md](docs/RELEASING.md) for signing setup):

```bash
./gradlew :composeApp:notarizeDmg
```

## License

[Apache 2.0](LICENSE)
