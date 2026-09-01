package des.c5inco.mesh.testing

object MeshTestTags {
    const val APP = "mesh.app"
    const val GRADIENT_CANVAS = "mesh.gradient-canvas"
    const val SHOW_POINTS = "mesh.show-points"
    const val CONSTRAIN_EDGE_POINTS = "mesh.constrain-edge-points"
    const val ROWS_INPUT = "mesh.rows-input"
    const val COLS_INPUT = "mesh.cols-input"
    const val EXPORT_CODE = "mesh.export-code"
    const val DISTRIBUTE_EVENLY = "mesh.distribute-evenly"
    const val ADD_CUSTOM_COLOR = "mesh.add-custom-color"
    const val CUSTOM_COLOR_INPUT = "mesh.custom-color-input"
    const val EXPORT_IMAGE = "mesh.export-image"
    const val EXPORT_SCALE = "mesh.export-scale"
    const val BLUR_SLIDER = "mesh.blur-slider"
    const val CANVAS_WIDTH_INPUT = "mesh.canvas-width-input"
    const val CANVAS_HEIGHT_INPUT = "mesh.canvas-height-input"
    const val CANVAS_WIDTH_MODE = "mesh.canvas-width-mode"
    const val CANVAS_HEIGHT_MODE = "mesh.canvas-height-mode"

    fun pointColor(row: Int, col: Int): String = "mesh.point-color-$row-$col"

    fun pointCursor(row: Int, col: Int): String = "mesh.point-cursor-$row-$col"

    fun pointOffsetX(row: Int, col: Int): String = "mesh.point-offset-x-$row-$col"

    fun pointOffsetY(row: Int, col: Int): String = "mesh.point-offset-y-$row-$col"
}
