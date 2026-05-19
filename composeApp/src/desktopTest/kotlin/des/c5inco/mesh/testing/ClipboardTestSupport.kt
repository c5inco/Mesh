package des.c5inco.mesh.testing

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.delay

fun readClipboardText(): String? {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
        return null
    }
    return runCatching {
        clipboard.getData(DataFlavor.stringFlavor) as? String
    }.getOrNull()
}

suspend fun waitForClipboardText(
    description: String,
    timeout: Duration = 5.seconds,
    pollInterval: Duration = 50.milliseconds,
    predicate: (String) -> Boolean = { it.isNotBlank() },
): String {
    val deadline = TimeSource.Monotonic.markNow() + timeout
    while (deadline.hasNotPassedNow()) {
        readClipboardText()?.takeIf(predicate)?.let { return it }
        delay(pollInterval)
    }
    throw AssertionError("Timeout after $timeout waiting for clipboard: $description")
}
