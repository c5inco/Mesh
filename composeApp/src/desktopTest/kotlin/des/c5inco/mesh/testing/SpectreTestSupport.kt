package des.c5inco.mesh.testing

import dev.sebastiano.spectre.core.AutomatorNode
import dev.sebastiano.spectre.core.ComposeAutomator
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.delay

suspend fun <T> ComposeAutomator.eventually(
    description: String,
    timeout: Duration = 5.seconds,
    pollInterval: Duration = 50.milliseconds,
    block: suspend ComposeAutomator.() -> T?,
): T {
    val deadline = TimeSource.Monotonic.markNow() + timeout
    var lastError: Throwable? = null
    while (deadline.hasNotPassedNow()) {
        refreshWindows()
        try {
            block()?.let { return it }
        } catch (t: Throwable) {
            lastError = t
        }
        delay(pollInterval)
    }
    throw AssertionError("Timeout after $timeout waiting for: $description", lastError)
}

suspend fun ComposeAutomator.waitForTestTag(
    tag: String,
    timeout: Duration = 10.seconds,
): AutomatorNode =
    eventually(description = "node with testTag '$tag'", timeout = timeout) {
        findOneByTestTag(tag)
    }

suspend fun ComposeAutomator.waitForText(
    text: String,
    timeout: Duration = 5.seconds,
): AutomatorNode =
    eventually(description = "node with text '$text'", timeout = timeout) {
        findOneByText(text)
    }

/**
 * Waits for a node in the side panel, scrolling the panel to reach it. The panel is a lazy list that only
 * composes what's on screen, so a control scrolled out of view doesn't exist until it's scrolled back.
 */
suspend fun ComposeAutomator.waitForSidePanelNode(
    description: String,
    timeout: Duration = 20.seconds,
    find: ComposeAutomator.() -> AutomatorNode?,
): AutomatorNode =
    eventually(description = description, timeout = timeout) {
        find() ?: scrollSidePanelUntil(find)
    }

suspend fun ComposeAutomator.waitForSidePanelTag(tag: String): AutomatorNode =
    waitForSidePanelNode("side panel node with testTag '$tag'") { findOneByTestTag(tag) }

suspend fun ComposeAutomator.waitForSidePanelText(text: String): AutomatorNode =
    waitForSidePanelNode("side panel node with text '$text'") { findOneByText(text) }

private suspend fun ComposeAutomator.scrollSidePanelUntil(find: ComposeAutomator.() -> AutomatorNode?): AutomatorNode? {
    val panel = findOneByTestTag(MeshTestTags.SIDE_PANEL) ?: return null
    // Up first, since most controls sit above the point list; then down through the points.
    for (wheelClicks in listOf(-3, 3)) {
        repeat(MAX_SIDE_PANEL_SCROLLS) {
            scrollWheel(panel, wheelClicks)
            waitForIdle()
            refreshWindows()
            find()?.let { return it }
        }
    }
    return null
}

private const val MAX_SIDE_PANEL_SCROLLS = 15
