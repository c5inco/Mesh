package des.c5inco.mesh.testing

import dev.sebastiano.spectre.core.AutomatorNode
import dev.sebastiano.spectre.core.ComposeAutomator
import kotlin.time.Duration.Companion.seconds

suspend fun ComposeAutomator.addCustomColor(hex: String) {
    performSemanticsClick(waitForSidePanelTag(MeshTestTags.ADD_CUSTOM_COLOR))
    waitForVisualIdle()

    // The input refills its last valid color whenever its focus changes. In GUI runs focus sometimes
    // moves while the robot is typing, after a popup from the previous step closes, so retype until
    // the input holds the hex.
    var typed = false
    for (attempt in 1..3) {
        val colorInput = eventually(description = "focused custom color input") {
            refreshWindows()
            findOneByTestTag(MeshTestTags.CUSTOM_COLOR_INPUT)?.takeIf { it.isFocused }
        }
        clearAndTypeText(colorInput, hex)
        typed = runCatching {
            eventually(description = "custom color input to read $hex", timeout = 2.seconds) {
                refreshWindows()
                findOneByTestTag(MeshTestTags.CUSTOM_COLOR_INPUT)?.takeIf { it.editableText == hex }
            }
        }.isSuccess
        if (typed) break
    }
    check(typed) { "Could not type $hex into the custom color input" }
    pressEnter()
    waitForVisualIdle()
}

suspend fun ComposeAutomator.openPointColorDropdown(row: Int, col: Int) {
    performSemanticsClick(waitForSidePanelTag(MeshTestTags.pointColor(row, col)))
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
