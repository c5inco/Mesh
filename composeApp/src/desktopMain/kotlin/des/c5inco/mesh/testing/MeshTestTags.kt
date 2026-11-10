package des.c5inco.mesh.testing

object MeshTestTags {
    const val APP = "mesh.app"
    const val SIDE_PANEL = "mesh.side-panel"
    const val GRADIENT_CANVAS = "mesh.gradient-canvas"
    const val SHOW_POINTS = "mesh.show-points"
    const val ROWS_INPUT = "mesh.rows-input"
    const val COLS_INPUT = "mesh.cols-input"
    const val ADD_CUSTOM_COLOR = "mesh.add-custom-color"
    const val CUSTOM_COLOR_INPUT = "mesh.custom-color-input"
    const val DELETE_COLOR_CONFIRM = "mesh.delete-color-confirm"
    const val DELETE_COLOR_CANCEL = "mesh.delete-color-cancel"
    const val MCP_SERVER_ENABLED = "mesh.mcp-server-enabled"
    const val MCP_SERVER_STATUS = "mesh.mcp-server-status"
    const val MCP_SERVER_PORT = "mesh.mcp-server-port"
    const val DOCUMENT = "mesh.document"
    const val DOCUMENT_PROMPT = "mesh.document-prompt"
    const val SAVE_FAILURE = "mesh.save-failure"
    const val SAVE_RETRY = "mesh.save-retry"
    const val AGENT_STATUS = "mesh.agent-status"
    const val EXPORT = "mesh.export"
    const val EXPORT_SCALE_MENU = "mesh.export-scale-menu"

    const val POINT_COLOR_PREFIX = "mesh.point-color-"

    fun pointColor(row: Int, col: Int): String = "$POINT_COLOR_PREFIX$row-$col"

    fun customColor(hex: String): String = "mesh.custom-color-$hex"
}
