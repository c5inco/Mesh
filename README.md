![Screenshot of desktop app](artwork/screenshot.png)

# Mesh

A desktop editor for creating mesh gradients in Compose, built with [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) and the [Jewel UI standalone toolkit](https://github.com/JetBrains/jewel). Gradients are rendered using [@sinasamaki](https://www.sinasamaki.com/mesh-gradients-in-jetpack-compose/)'s [`Modifier.meshGradient` implementation](https://gist.github.com/sinasamaki/05725557c945c5329fdba4a3494aaecb?ref=sinasamaki.com).

## Features and controls

- **Grid:** Adjust between 2–10 rows and columns, or distribute points evenly.
- **Control points:** Drag points on the canvas or edit `X`/`Y` offsets in the side panel. Double-click the canvas to hide or show points, and toggle edge constraints to lock perimeter points to the canvas bounds.
- **Palette:** Add hex colors to the saved palette, remove unused ones, and assign colors per point.
- **Canvas:** Configure canvas dimensions (fixed size or fill) and background color.
- **Undo/redo:** Undo with ⌘Z (Ctrl+Z) and redo with ⇧⌘Z (Ctrl+Shift+Z or Ctrl+Y). Covers point, grid, blur, and background edits, including ones an agent makes.
- **Export:** Copy Kotlin code (`Pair<Offset, Color>` point lists via KotlinPoet) for `@sinasamaki`'s `meshGradient` modifier, or export a PNG at 1x, 2x, or 3x scale.
- **Persistence:** Grid state and saved colors autosave locally to a Room/SQLite database.
- **Agent access (MCP):** Coding agents can read and edit the open mesh through an MCP server. See [below](#agent-access-mcp).

### Roadmap

- [ ] Save and load configurations as documents
- [ ] Grain overlay
- [ ] Configurable gradient resolution
- [ ] Presets
- [ ] Gradient animation
- [x] Undo/redo

## Agent access (MCP)

Mesh can serve an [MCP](https://modelcontextprotocol.io) server over Streamable HTTP so an agent can edit the gradient you are looking at. It is off by default: turn on **Let agents edit this mesh** in the side panel's MCP server section, where you can also change the port (default `7777`) and copy the URL. The setting is remembered across launches. The server listens only on localhost and rejects requests from other origins.

| Tool | What it does |
| --- | --- |
| `get_mesh` | Returns the grid, canvas settings, saved colors with their IDs, and every point's position and color |
| `update_point` | Moves and/or recolors the point at a row and column |
| `add_color` | Adds a hex color to the custom palette and returns its ID |
| `undo` / `redo` | Undoes or redoes the last edit, whether you or the agent made it |
| `render_png` | Renders the mesh to a PNG so the agent can see the result |

Connect from Claude Code:

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
