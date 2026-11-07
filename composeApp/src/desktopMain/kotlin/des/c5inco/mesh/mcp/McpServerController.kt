package des.c5inco.mesh.mcp

import des.c5inco.mesh.data.AppConfiguration
import io.ktor.server.engine.EmbeddedServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.BindException
import java.util.prefs.Preferences

data class McpServerState(
    val enabled: Boolean,
    val port: Int,
    val status: Status,
) {
    sealed interface Status {
        data object Off : Status
        data object Starting : Status
        data class Running(val url: String) : Status
        data class Failed(val message: String) : Status
    }
}

/**
 * Starts and stops [MeshMcpServer] as the user turns it on or changes its port, and remembers
 * both settings across launches. The server is off until the user enables it.
 */
class McpServerController(
    private val configuration: AppConfiguration,
    private val preferences: Preferences = Preferences.userNodeForPackage(McpServerController::class.java),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val serverMutex = Mutex()
    private var server: EmbeddedServer<*, *>? = null

    private val _state = MutableStateFlow(
        McpServerState(
            enabled = preferences.getBoolean(ENABLED_KEY, false),
            port = preferences.getInt(PORT_KEY, MeshMcpServer.DEFAULT_PORT),
            status = McpServerState.Status.Off,
        )
    )
    val state: StateFlow<McpServerState> = _state.asStateFlow()

    init {
        if (_state.value.enabled) restart()
    }

    fun setEnabled(enabled: Boolean) {
        preferences.putBoolean(ENABLED_KEY, enabled)
        _state.update { it.copy(enabled = enabled) }
        restart()
    }

    fun setPort(port: Int) {
        require(port in 1..65535) { "Port must be between 1 and 65535" }
        preferences.putInt(PORT_KEY, port)
        _state.update { it.copy(port = port) }
        if (_state.value.enabled) restart()
    }

    /** Stops the server for good, such as when the app exits. */
    fun close() {
        scope.cancel()
        server?.stop(gracePeriodMillis = 0, timeoutMillis = 500)
    }

    private fun restart() {
        if (_state.value.enabled) {
            _state.update { it.copy(status = McpServerState.Status.Starting) }
        }
        scope.launch {
            serverMutex.withLock {
                server?.stop(gracePeriodMillis = 0, timeoutMillis = 500)
                server = null

                val (enabled, port) = _state.value
                val status = if (!enabled) {
                    McpServerState.Status.Off
                } else {
                    try {
                        server = MeshMcpServer(configuration).start(port)
                        McpServerState.Status.Running("http://127.0.0.1:$port/mcp")
                    } catch (e: Exception) {
                        // Ktor reports a failed bind as a cancellation caused by the BindException.
                        ensureActive()
                        val cause = generateSequence<Throwable>(e) { it.cause }.last()
                        McpServerState.Status.Failed(
                            if (cause is BindException) "Port $port is in use" else cause.message ?: "Couldn't start the server"
                        )
                    }
                }
                // A newer change may have queued another restart; only report the latest settings.
                _state.update { if (it.enabled == enabled && it.port == port) it.copy(status = status) else it }
            }
        }
    }

    private companion object {
        const val ENABLED_KEY = "enabled"
        const val PORT_KEY = "port"
    }
}
