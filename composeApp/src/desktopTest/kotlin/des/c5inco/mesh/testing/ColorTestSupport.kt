package des.c5inco.mesh.testing

import dev.sebastiano.spectre.core.AutomatorNode
import dev.sebastiano.spectre.core.ComposeAutomator

suspend fun ComposeAutomator.addCustomColor(hex: String) {
    performSemanticsClick(waitForTestTag(MeshTestTags.ADD_CUSTOM_COLOR))
    waitForVisualIdle()

    val colorInput = waitForTestTag(MeshTestTags.CUSTOM_COLOR_INPUT)
    clearAndTypeText(colorInput, hex)
    pressEnter()
    waitForVisualIdle()
}

suspend fun ComposeAutomator.openPointColorDropdown(row: Int, col: Int) {
    performSemanticsClick(waitForTestTag(MeshTestTags.pointColor(row, col)))
    eventually(description = "point color dropdown menu") {
        refreshWindows()
        findByText("7766EE").firstOrNull()
    }
    waitForVisualIdle()
}

suspend fun ComposeAutomator.selectColorInOpenDropdown(hex: String): AutomatorNode {
    val colorOption = eventually(description = "color option '$hex' in dropdown menu") {
        refreshWindows()
        findOneByText(hex)
    }
    performSemanticsClick(colorOption)
    waitForVisualIdle()
    return colorOption
}

suspend fun ComposeAutomator.assertColorSelectedInOpenDropdown(hex: String) {
    eventually(description = "color option '$hex' to be selected") {
        refreshWindows()
        findOneByText(hex)?.takeIf { it.isSelected }
    }
}
