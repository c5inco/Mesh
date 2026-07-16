@file:OptIn(InternalSpectreApi::class)

package des.c5inco.mesh.testing

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import des.c5inco.mesh.App
import des.c5inco.mesh.data.AppConfiguration
import des.c5inco.mesh.data.AppDataRepository
import dev.sebastiano.spectre.core.ComposeAutomator
import dev.sebastiano.spectre.core.InternalSpectreApi
import dev.sebastiano.spectre.core.RobotDriver
import dev.sebastiano.spectre.core.WindowTracker
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.ui.ComponentStyling
import java.awt.GraphicsEnvironment
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MeshAppFixture(
    private val title: String = "Mesh",
    private val startupTimeout: Duration = 15.seconds,
) {
    private val applicationStarted = CountDownLatch(1)
    @Volatile private var exitFn: (() -> Unit)? = null
    private lateinit var thread: Thread
    private lateinit var _automator: ComposeAutomator
    private val tempHome = createTempHome()
    private var savedUserHome: String? = null

    val automator: ComposeAutomator
        get() = _automator

    fun requireDisplay() {
        check(!GraphicsEnvironment.isHeadless()) {
            "MeshAppFixture cannot run in a headless environment (java.awt.headless=true)"
        }
    }

    fun start() {
        requireDisplay()
        savedUserHome = System.getProperty("user.home")
        System.setProperty("user.home", tempHome.absolutePath)
        System.setProperty("skiko.renderApi", "SOFTWARE_COMPAT")

        thread = Thread(
            {
                application {
                    val configuration = AppConfiguration(
                        repository = AppDataRepository(),
                    )
                    val windowState = rememberWindowState(
                        width = 1024.dp,
                        height = 768.dp,
                    )

                    exitFn = ::exitApplication
                    applicationStarted.countDown()

                    val themeDefinition = JewelTheme.darkThemeDefinition()

                    Window(
                        state = windowState,
                        onCloseRequest = ::exitApplication,
                        title = title,
                    ) {
                        IntUiTheme(
                            theme = themeDefinition,
                            styling = ComponentStyling.default(),
                        ) {
                            App(configuration = configuration)
                        }
                    }
                }
            },
            "mesh-spectre-fixture",
        ).apply { isDaemon = true }
        thread.start()

        check(applicationStarted.await(startupTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
            "Mesh app did not enter application{} within $startupTimeout"
        }

        val bootstrapTracker = WindowTracker()
        val deadline = System.nanoTime() + startupTimeout.inWholeNanoseconds
        while (System.nanoTime() < deadline) {
            bootstrapTracker.refresh()
            val tracked = bootstrapTracker.trackedWindows.value.firstOrNull {
                runCatching { (it.window as? java.awt.Frame)?.title }.getOrNull() == title
            }
            if (tracked != null) {
                _automator = ComposeAutomator.inProcess(
                    robotDriver = RobotDriver.synthetic(tracked.window),
                )
                _automator.refreshWindows()
                return
            }
            Thread.sleep(WINDOW_POLL.inWholeMilliseconds)
        }
        error("Main window with title '$title' did not appear within $startupTimeout")
    }

    fun stop() {
        exitFn?.invoke()
        if (::thread.isInitialized) {
            thread.join(SHUTDOWN_TIMEOUT.inWholeMilliseconds)
        }
        savedUserHome?.let { System.setProperty("user.home", it) }
            ?: System.clearProperty("user.home")
        savedUserHome = null
        tempHome.deleteRecursively()
    }

    private companion object {
        val WINDOW_POLL: Duration = 100.milliseconds
        val SHUTDOWN_TIMEOUT: Duration = 5.seconds

        fun createTempHome(): File =
            Files.createTempDirectory("mesh-spectre-").toFile()
    }
}
