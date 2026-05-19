package des.c5inco.mesh.testing

object MeshTestTags {
    const val APP = "mesh.app"
    const val GRADIENT_CANVAS = "mesh.gradient-canvas"
    const val SHOW_POINTS = "mesh.show-points"
    const val ROWS_INPUT = "mesh.rows-input"
    const val COLS_INPUT = "mesh.cols-input"
    const val EXPORT_CODE = "mesh.export-code"
    const val ADD_CUSTOM_COLOR = "mesh.add-custom-color"
    const val CUSTOM_COLOR_INPUT = "mesh.custom-color-input"

    fun pointColor(row: Int, col: Int): String = "mesh.point-color-$row-$col"
}
