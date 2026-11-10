@file:OptIn(InternalSpectreApi::class)

package des.c5inco.mesh.testing

import androidx.compose.runtime.remember
import androidx.compose.ui.window.application
import des.c5inco.mesh.MeshWindow
import des.c5inco.mesh.data.AppConfiguration
import des.c5inco.mesh.data.AppDataRepository
import dev.sebastiano.spectre.core.ComposeAutomator
import dev.sebastiano.spectre.core.InternalSpectreApi
import dev.sebastiano.spectre.core.RobotDriver
import dev.sebastiano.spectre.core.WindowTracker
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MeshAppFixture(
    /** The window's title at launch: a new home directory opens an untitled document. */
    private val title: String = "Untitled",
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

    /** The app's state, for setting up scenarios faster than driving the UI would. */
    lateinit var configuration: AppConfiguration
        private set

    /** The main window. */
    lateinit var window: Frame
        private set

    /** The main window's title, which names the open document. */
    val windowTitle: String
        get() = window.title

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
                    val configuration = remember {
                        AppConfiguration(
                            repository = AppDataRepository(),
                        ).also { this@MeshAppFixture.configuration = it }
                    }

                    exitFn = ::exitApplication
                    applicationStarted.countDown()

                    MeshWindow(
                        configuration = configuration,
                        onExit = ::exitApplication,
                    )
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
                runCatching { (it.window as? Frame)?.title }.getOrNull() == title
            }
            if (tracked != null) {
                window = tracked.window as Frame
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
