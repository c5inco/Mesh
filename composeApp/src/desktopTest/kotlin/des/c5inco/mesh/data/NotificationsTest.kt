package des.c5inco.mesh.data

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class NotificationsTest {
    @Test
    fun `a message sent before anyone collects it is still delivered`() = runBlocking {
        Notifications.send("sent before collection")

        assertEquals(
            "sent before collection",
            withTimeout(5.seconds) { Notifications.notificationFlow.first { it == "sent before collection" } },
        )
    }

    @Test
    fun `one collector reading a message doesn't take it from another`() = runBlocking {
        // Like the canvas and anything else listening at once: neither may consume the other's message.
        val canvas = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5.seconds) { Notifications.notificationFlow.first { it == "for everyone" } }
        }
        val other = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5.seconds) { Notifications.notificationFlow.first { it == "for everyone" } }
        }

        Notifications.send("for everyone")

        assertEquals("for everyone", canvas.await())
        assertEquals("for everyone", other.await())
    }
}
