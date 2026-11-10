package des.c5inco.mesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import des.c5inco.mesh.data.AppConfiguration
import des.c5inco.mesh.data.Notifications
import des.c5inco.mesh.mcp.McpServerState
import des.c5inco.mesh.testing.MeshTestTags
import des.c5inco.mesh.ui.components.DimensionInputField
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import model.SavedColor
import model.findColor
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.PopupContainer
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.colorPalette
import org.jetbrains.jewel.window.DecoratedWindowScope
import org.jetbrains.jewel.window.TitleBar
import java.io.File

private val isMac = System.getProperty("os.name").lowercase().contains("mac")

@Composable
private fun hoverColor(): Color =
    JewelTheme.colorPalette.grayOrNull(3) ?: requireNotNull(IntUiDarkTheme.colors.grayOrNull(3))

@Composable
private fun pressedColor(): Color =
    JewelTheme.colorPalette.grayOrNull(4) ?: requireNotNull(IntUiDarkTheme.colors.grayOrNull(4))

@Composable
private fun idleColor(): Color =
    JewelTheme.colorPalette.grayOrNull(7) ?: requireNotNull(IntUiDarkTheme.colors.grayOrNull(7))

/** The green of a live agent connection, matching the canvas's own point handle. */
private val ConnectedGreen = Color(0xFF62B543)

/**
 * The window's title bar: the document and any failed save at the start, the agent and the export at
 * the end. Everything here is app chrome rather than a document property, so it stays visible
 * whatever the inspector is scrolled to.
 */
@Composable
fun DecoratedWindowScope.MeshToolbar(
    documentName: String,
    documentUntitled: Boolean,
    documentFile: File?,
    saveFailure: AppConfiguration.SaveFailure?,
    meshPoints: List<List<Pair<Offset, Long>>>,
    availableColors: List<SavedColor>,
    exportScale: Int,
    canvasSize: Pair<Int, Int>,
    mcpServerState: McpServerState?,
    onNewDocument: () -> Unit,
    onOpenDocument: () -> Unit,
    onSaveDocument: () -> Unit,
    onSaveDocumentAs: () -> Unit,
    onRetrySave: () -> Unit,
    onExportScaleChange: (Int) -> Unit,
    onExport: () -> Unit,
    onCopyCode: () -> Unit,
    onMcpServerEnabledChange: (Boolean) -> Unit,
    onMcpServerPortChange: (Int) -> Unit,
) {
    TitleBar {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.align(Alignment.Start).padding(start = 4.dp),
        ) {
            DocumentWidget(
                name = documentName,
                untitled = documentUntitled,
                file = documentFile,
                points = meshPoints,
                colors = availableColors,
                saveFailed = saveFailure != null,
                onNew = onNewDocument,
                onOpen = onOpenDocument,
                onSave = onSaveDocument,
                onSaveAs = onSaveDocumentAs,
            )
            saveFailure?.let { failure ->
                SaveFailureWidget(failure = failure, onRetry = onRetrySave, onSaveAs = onSaveDocumentAs)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.align(Alignment.End).padding(end = 8.dp),
        ) {
            AgentWidget(
                state = mcpServerState,
                onEnabledChange = onMcpServerEnabledChange,
                onPortChange = onMcpServerPortChange,
            )
            Divider(orientation = Orientation.Vertical, thickness = 1.dp, modifier = Modifier.height(20.dp))
            ExportWidget(
                scale = exportScale,
                canvasSize = canvasSize,
                onScaleChange = onExportScaleChange,
                onExport = onExport,
                onCopyCode = onCopyCode,
            )
        }
    }
}

/**
 * The document: a live swatch of its own gradient, its name, and a menu of the file actions. An
 * untitled document is set in italics, and its swatch carries a badge while a save is failing.
 */
@Composable
private fun DocumentWidget(
    name: String,
    untitled: Boolean,
    file: File?,
    points: List<List<Pair<Offset, Long>>>,
    colors: List<SavedColor>,
    saveFailed: Boolean,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val barColor = JewelTheme.globalColors.panelBackground
    val errorColor = JewelTheme.globalColors.text.error

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .toolbarButton(open = menuOpen) { menuOpen = true }
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag(MeshTestTags.DOCUMENT),
        ) {
            Box {
                MeshSwatch(points = points, colors = colors)
                if (saveFailed) {
                    Spacer(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 3.dp, y = (-3).dp)
                            .size(7.dp)
                            .drawBehind {
                                // The ring hides the swatch corner behind the badge, like the design's notch.
                                drawCircle(barColor, radius = size.minDimension / 2 + 2.dp.toPx())
                                drawCircle(errorColor)
                            }
                    )
                }
            }
            Text(
                text = name,
                fontStyle = if (untitled) FontStyle.Italic else FontStyle.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                key = AllIconsKeys.General.ChevronDown,
                iconClass = AllIconsKeys::class.java,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
            )
        }

        if (menuOpen) {
            PopupMenu(
                onDismissRequest = { menuOpen = false; true },
                horizontalAlignment = Alignment.Start,
            ) {
                    selectableItem(
                        selected = false,
                        keybinding = shortcut("N"),
                        onClick = { menuOpen = false; onNew() },
                    ) { Text("New") }
                    selectableItem(
                        selected = false,
                        keybinding = shortcut("O"),
                        onClick = { menuOpen = false; onOpen() },
                    ) { Text("Open…") }
                    separator()
                    selectableItem(
                        selected = false,
                        keybinding = shortcut("S"),
                        onClick = { menuOpen = false; onSave() },
                    ) { Text("Save") }
                    selectableItem(
                        selected = false,
                        keybinding = shortcut("S", shift = true),
                        onClick = { menuOpen = false; onSaveAs() },
                    ) { Text("Save As…") }
                    if (file != null) {
                        separator()
                        selectableItem(
                            selected = false,
                            onClick = { menuOpen = false; reveal(file) },
                        ) { Text(if (isMac) "Show in Finder" else "Show in File Manager") }
                    }
            }
        }
    }
}

/** A failed save, named in red with a retry, and a popover that explains it and offers Save As. */
@Composable
private fun SaveFailureWidget(
    failure: AppConfiguration.SaveFailure,
    onRetry: () -> Unit,
    onSaveAs: () -> Unit,
) {
    var popupOpen by remember { mutableStateOf(false) }
    val error = JewelTheme.globalColors.text.error

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .toolbarButton(open = popupOpen) { popupOpen = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag(MeshTestTags.SAVE_FAILURE),
            ) {
                Icon(
                    key = AllIconsKeys.General.Error,
                    iconClass = AllIconsKeys::class.java,
                    contentDescription = null,
                    tint = error,
                    modifier = Modifier.size(14.dp),
                )
                Text("Couldn't save", color = error, maxLines = 1)
            }

            if (popupOpen) {
                PopupContainer(onDismissRequest = { popupOpen = false }, horizontalAlignment = Alignment.Start) {
                    Column(Modifier.width(320.dp).padding(12.dp)) {
                        Text("Couldn't save ${failure.file?.name ?: "this document"}", color = error)
                        Spacer(Modifier.height(6.dp))
                        failure.file?.let {
                            Text(it.absolutePath, fontSize = 12.sp, color = JewelTheme.globalColors.text.info)
                        }
                        Text(failure.message, fontSize = 12.sp, color = JewelTheme.globalColors.text.info)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Your latest edits are only in this window. Mesh keeps trying every second.",
                            fontSize = 12.sp,
                            color = JewelTheme.globalColors.text.info,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Link("Save As…", onClick = { popupOpen = false; onSaveAs() })
                            Link("Retry", onClick = { popupOpen = false; onRetry() })
                        }
                    }
                }
            }
        }
        Link("Retry", onClick = onRetry, modifier = Modifier.testTag(MeshTestTags.SAVE_RETRY))
    }
}

/**
 * Who can edit the mesh. The mark is the canvas's own point handle: filled when an agent is
 * connected, hollow while the server waits for one, red when the connection is blocked.
 */
@Composable
private fun AgentWidget(
    state: McpServerState?,
    onEnabledChange: (Boolean) -> Unit,
    onPortChange: (Int) -> Unit,
) {
    if (state == null) return
    var popupOpen by remember { mutableStateOf(false) }

    val clients = state.clients
    val failed = state.status as? McpServerState.Status.Failed
    val running = state.status as? McpServerState.Status.Running
    val label = when {
        failed != null -> failed.message
        state.status is McpServerState.Status.Starting -> "Starting…"
        clients.size >= 2 -> "${clients.size} agents"
        clients.size == 1 -> if (state.agentWorking) "${clients.single()} editing" else clients.single()
        running != null -> "Agents on"
        else -> "Agents off"
    }
    val tone = when {
        failed != null -> StatusTone.Blocked
        clients.isNotEmpty() -> StatusTone.Connected
        running != null -> StatusTone.Waiting
        else -> StatusTone.Off
    }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .toolbarButton(open = popupOpen) { popupOpen = true }
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag(MeshTestTags.AGENT_STATUS),
        ) {
            StatusDot(tone)
            Text(
                text = label,
                color = if (tone == StatusTone.Blocked) JewelTheme.globalColors.text.error else Color.Unspecified,
                maxLines = 1,
            )
        }

        if (popupOpen) {
            PopupContainer(onDismissRequest = { popupOpen = false }, horizontalAlignment = Alignment.End) {
                Column(Modifier.width(320.dp).padding(12.dp)) {
                    CheckboxRow(
                        text = "Let agents edit this mesh",
                        checked = state.enabled,
                        onCheckedChange = onEnabledChange,
                        modifier = Modifier.testTag(MeshTestTags.MCP_SERVER_ENABLED),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = agentStatusLine(state),
                        fontSize = 12.sp,
                        color = if (tone == StatusTone.Blocked) {
                            JewelTheme.globalColors.text.error
                        } else {
                            JewelTheme.globalColors.text.info
                        },
                        modifier = Modifier.testTag(MeshTestTags.MCP_SERVER_STATUS),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Port", fontSize = 12.sp, color = JewelTheme.globalColors.text.info)
                    Spacer(Modifier.height(4.dp))
                    DimensionInputField(
                        value = state.port,
                        min = 1024,
                        max = 65535,
                        enabled = true,
                        paramName = "Port",
                        onUpdate = onPortChange,
                        modifier = Modifier.width(140.dp).testTag(MeshTestTags.MCP_SERVER_PORT),
                    )
                    if (running != null) {
                        Spacer(Modifier.height(12.dp))
                        Text("Connect an agent", fontSize = 12.sp, color = JewelTheme.globalColors.text.info)
                        Spacer(Modifier.height(4.dp))
                        CopyRow(text = running.url, label = "Copy")
                        Spacer(Modifier.height(6.dp))
                        CopyRow(text = "claude mcp add --transport http mesh ${running.url}", label = "Copy")
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Agents can read the mesh, move and recolor points, add colors, undo, and render " +
                                "PNGs. Their edits land in your undo history.",
                            fontSize = 12.sp,
                            color = JewelTheme.globalColors.text.info,
                        )
                    }
                }
            }
        }
    }
}

private fun agentStatusLine(state: McpServerState): String = when (val status = state.status) {
    is McpServerState.Status.Failed -> status.message
    is McpServerState.Status.Starting -> "Starting…"
    is McpServerState.Status.Running -> when {
        state.clients.size >= 2 -> "${state.clients.size} agents are connected: ${state.clients.joinToString(" and ")}"
        state.clients.size == 1 -> "${state.clients.single()} is connected"
        else -> "Waiting for an agent to connect"
    }
    McpServerState.Status.Off -> "Off. Agents can't read or change this mesh."
}

/** The export, built like a run configuration: it names the file it will make, then makes it. */
@Composable
private fun ExportWidget(
    scale: Int,
    canvasSize: Pair<Int, Int>,
    onScaleChange: (Int) -> Unit,
    onExport: () -> Unit,
    onCopyCode: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    val border = JewelTheme.globalColors.borders.normal

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .border(1.dp, border, shape)
            .height(28.dp),
    ) {
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxHeight()
                    .toolbarButton(
                        open = menuOpen,
                        shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp),
                    ) { menuOpen = true }
                    .padding(horizontal = 8.dp)
                    .testTag(MeshTestTags.EXPORT_SCALE_MENU),
            ) {
                Text("@${scale}x")
                Text(pixelSize(canvasSize, scale), color = JewelTheme.globalColors.text.info)
                Icon(
                    key = AllIconsKeys.General.ChevronDown,
                    iconClass = AllIconsKeys::class.java,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
            }

            if (menuOpen) {
                PopupMenu(
                    onDismissRequest = { menuOpen = false; true },
                    horizontalAlignment = Alignment.End,
                ) {
                        repeat(3) { index ->
                            val option = index + 1
                            selectableItem(
                                selected = scale == option,
                                onClick = { menuOpen = false; onScaleChange(option) },
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("PNG @${option}x")
                                    Text(
                                        pixelSize(canvasSize, option),
                                        color = JewelTheme.globalColors.text.info,
                                    )
                                }
                            }
                        }
                        separator()
                        selectableItem(selected = false, onClick = { menuOpen = false; onCopyCode() }) {
                            Text("Copy points as Kotlin")
                        }
                }
            }
        }

        Spacer(Modifier.width(1.dp).fillMaxHeight().background(border))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxHeight()
                .toolbarButton(shape = RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)) { onExport() }
                .padding(horizontal = 10.dp)
                .testTag(MeshTestTags.EXPORT),
        ) {
            ExportGlyph()
            Text("Export")
        }
    }
}

/**
 * The export glyph: an arrow leaving a tray. Drawn here because Jewel's bundled icon set has no export
 * icon, so `AllIconsKeys.General.Export` renders as the missing-icon placeholder.
 */
@Composable
private fun ExportGlyph(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Spacer(
        modifier.size(14.dp).drawBehind {
            val stroke = 1.3.dp.toPx()
            val head = 0.38f * size.height
            drawLine(color, Offset(size.width / 2, size.height * 0.72f), Offset(size.width / 2, size.height * 0.08f), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width / 2 - head, size.height * 0.08f + head), Offset(size.width / 2, size.height * 0.08f), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width / 2 + head, size.height * 0.08f + head), Offset(size.width / 2, size.height * 0.08f), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.14f, size.height * 0.62f), Offset(size.width * 0.14f, size.height * 0.92f), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.14f, size.height * 0.92f), Offset(size.width * 0.86f, size.height * 0.92f), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.86f, size.height * 0.92f), Offset(size.width * 0.86f, size.height * 0.62f), stroke, StrokeCap.Round)
        }
    )
}

private fun pixelSize(canvasSize: Pair<Int, Int>, scale: Int) =
    "${canvasSize.first * scale} × ${canvasSize.second * scale}"

/** The document's gradient, drawn into 16dp: the same mesh the canvas shows. */
@Composable
private fun MeshSwatch(
    points: List<List<Pair<Offset, Long>>>,
    colors: List<SavedColor>,
) {
    val painter = remember(points, colors) {
        val colorPoints = points.map { row -> row.map { (position, id) -> position to colors.findColor(id) } }
        meshGradientPainter(colorPoints)
    }
    val shape = RoundedCornerShape(4.dp)

    Box(Modifier.size(16.dp).clip(shape).border(1.dp, Color.White.copy(alpha = 0.12f), shape)) {
        if (painter != null) {
            Spacer(Modifier.fillMaxWidth().height(16.dp).paint(painter, sizeToIntrinsics = false))
        }
    }
}

/** How an agent connection is doing. */
private enum class StatusTone { Off, Waiting, Connected, Blocked }

@Composable
private fun StatusDot(tone: StatusTone) {
    val idle = idleColor()
    val error = JewelTheme.globalColors.text.error
    Spacer(
        Modifier.size(8.dp).drawBehind {
            val stroke = Stroke(width = 1.5.dp.toPx())
            when (tone) {
                StatusTone.Connected -> drawCircle(ConnectedGreen)
                StatusTone.Blocked -> drawCircle(error)
                StatusTone.Waiting -> drawCircle(ConnectedGreen, style = stroke)
                StatusTone.Off -> drawCircle(idle, style = stroke)
            }
        }
    )
}

/** A read-only value with a button that puts it on the clipboard. */
@Composable
private fun CopyRow(text: String, label: String) {
    val shape = RoundedCornerShape(4.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(JewelTheme.globalColors.panelBackground)
            .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Link(
            text = label,
            onClick = {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
                Notifications.send("📋 Copied to the clipboard!")
            },
        )
    }
}

private fun shortcut(key: String, shift: Boolean = false): Set<String> = when {
    isMac && shift -> setOf("⇧", "⌘", key)
    isMac -> setOf("⌘", key)
    shift -> setOf("Ctrl", "Shift", key)
    else -> setOf("Ctrl", key)
}

private fun reveal(file: File) {
    val desktop = if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop() else null
    if (desktop != null && desktop.isSupported(java.awt.Desktop.Action.BROWSE_FILE_DIR)) {
        desktop.browseFileDirectory(file)
    }
}

/** A clickable toolbar control, with the hover and open states Jewel's own controls use. */
@Composable
private fun Modifier.toolbarButton(
    open: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(6.dp),
    onClick: () -> Unit,
): Modifier {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val background = when {
        open -> pressedColor()
        hovered -> hoverColor()
        else -> Color.Transparent
    }
    return this
        .clip(shape)
        .background(background)
        .clickable(interactionSource = interactions, indication = null, onClick = onClick)
}
