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
