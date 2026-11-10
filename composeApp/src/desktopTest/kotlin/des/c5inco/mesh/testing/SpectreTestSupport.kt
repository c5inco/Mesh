package des.c5inco.mesh.testing

import androidx.compose.runtime.tooling.ComposeToolingApi
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import dev.sebastiano.spectre.core.AutomatorNode
import dev.sebastiano.spectre.core.ComposeAutomator
import java.awt.Window
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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

/**
 * Pages through the side panel from the top until [find] finds its node, so it reaches any item from any
 * scroll position. It jumps with the list's scroll-to-item semantics: mouse wheel scrolling is animated
 * and far too slow to cross a large grid.
 */
private suspend fun ComposeAutomator.scrollSidePanelUntil(find: ComposeAutomator.() -> AutomatorNode?): AutomatorNode? {
    var page = scrollSidePanelTo(0) ?: return null
    while (true) {
        refreshWindows()
        find()?.let {
            waitForIdle()
            return it
        }
        if (page.atEnd) return null
        // A point editor has one tag, so a page's tags roughly count its items. A jump only counts when the
        // new page still shares a tag with this one, so no item between the two pages is skipped.
        var step = (page.tags.size - 1).coerceAtLeast(1)
        while (true) {
            val next = scrollSidePanelTo(minOf(page.index + step, page.itemCount - 1)) ?: return null
            if (step == 1 || next.tags.any { it in page.tags }) {
                page = next
                break
            }
            step /= 2
        }
    }
}

/** The side panel scrolled to [index]: the test tags it composes, and whether it can scroll further. */
private class SidePanelPage(val index: Int, val itemCount: Int, val atEnd: Boolean, val tags: Set<String>)

private suspend fun scrollSidePanelTo(index: Int): SidePanelPage? {
    withSidePanelList { it.config[SemanticsActions.ScrollToIndex].action?.invoke(index) } ?: return null
    // The action queues a UI-thread task that scrolls the list and lays it out at once, so the next task
    // reads the new page. Waiting for idle here instead costs a full frame per page, and a large grid's
    // frames are slow.
    return withSidePanelList { list ->
        val range = list.config[SemanticsProperties.VerticalScrollAxisRange]
        SidePanelPage(
            index = index,
            itemCount = list.config[SemanticsProperties.CollectionInfo].rowCount,
            atEnd = range.value() >= range.maxValue(),
            tags = list.visibleTestTags(),
        )
    }
}

/** Runs [block] on the UI thread with the side panel's lazy list, or returns null when no window shows it. */
@OptIn(ComposeToolingApi::class)
private suspend fun <T> withSidePanelList(block: (SemanticsNode) -> T): T? = withContext(Dispatchers.Main) {
    Window.getWindows()
        .filter { it.isShowing }
        .filterIsInstance<ComposeWindow>()
        .flatMap { it.semanticsOwners }
        .firstNotNullOfOrNull { owner ->
            owner.unmergedRootSemanticsNode
                .findFirst { it.config.getOrNull(SemanticsProperties.TestTag) == MeshTestTags.SIDE_PANEL }
                ?.findFirst { SemanticsActions.ScrollToIndex in it.config }
        }
        ?.let(block)
}

private fun SemanticsNode.findFirst(predicate: (SemanticsNode) -> Boolean): SemanticsNode? =
    if (predicate(this)) this else children.firstNotNullOfOrNull { it.findFirst(predicate) }

/**
 * The test tags on screen in this list. The list keeps a focused item composed after it scrolls away, so
 * counting every tag under the list would count that item on every page and hide gaps between pages.
 */
private fun SemanticsNode.visibleTestTags(): Set<String> = buildSet {
    val viewport = boundsInRoot
    fun collect(node: SemanticsNode) {
        node.config.getOrNull(SemanticsProperties.TestTag)
            ?.takeIf { node.boundsInRoot.overlaps(viewport) }
            ?.let(::add)
        node.children.forEach(::collect)
    }
    collect(this@visibleTestTags)
}
