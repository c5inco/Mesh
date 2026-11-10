package des.c5inco.mesh.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Messages for the user, such as a file that couldn't be opened. Every collector sees every message, and they
 * are buffered, so one sent before the UI is listening, such as during startup, isn't lost.
 */
object Notifications {
    // The replay keeps a message sent before anything collected it, such as one from a document read that
    // happens in AppConfiguration's constructor; the extra buffer holds messages that arrive while a collector
    // is busy. A collector slower than both drops its own oldest message rather than stalling the sender.
    private val messages = MutableSharedFlow<String>(
        replay = 16,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val notificationFlow = messages.asSharedFlow()

    fun send(message: String) {
        messages.tryEmit(message)
    }
}
