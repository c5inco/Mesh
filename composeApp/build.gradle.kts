@file:Suppress("UnstableApiUsage")

import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.process.ExecOperations
import org.gradle.kotlin.dsl.support.serviceOf
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.compose.desktop.application.tasks.AbstractNotarizationTask
import groovy.json.JsonSlurper
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import kotlin.io.path.listDirectoryEntries

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

repositories {
    mavenCentral()
    google()
}

version = "1.0.0"
val baseName = "Mesh"

val desktopJvmVersion = JavaLanguageVersion.of(25)
val desktopJvmVendor = JvmVendorSpec.JETBRAINS

kotlin {
    jvm("desktop")

    jvmToolchain {
        vendor = desktopJvmVendor
        languageVersion = desktopJvmVersion
    }

    sourceSets {
        val desktopMain by getting
        val desktopTest by getting

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
        }
        desktopMain.dependencies {
            implementation(libs.jewel.int.ui.standalone)

            implementation(compose.desktop.currentOs) {
                exclude(group = "org.jetbrains.compose.material")
            }
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.kotlinpoet)
            implementation(libs.mcp.kotlin.sdk.server)
            implementation(libs.ktor.server.cio)
        }
        desktopTest.dependencies {
            implementation(libs.spectre.core)
            implementation(libs.spectre.testing)
            implementation(libs.junit.jupiter)
            runtimeOnly(libs.junit.platform.launcher)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.mcp.kotlin.sdk.client)
            implementation(libs.ktor.client.cio)
        }
    }
}

dependencies {
    add("kspDesktop", libs.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

fun localReleaseProperty(name: String): String? =
    localProperties.getProperty(name)?.trim()?.takeIf { value -> value.isNotEmpty() }

fun releaseProperty(name: String): String? =
    localReleaseProperty(name)
        ?: providers.gradleProperty(name).orNull?.trim()?.takeIf { value -> value.isNotEmpty() }

val macSign = releaseProperty("compose.desktop.mac.sign")?.toBooleanStrictOrNull()
val macSigningIdentity = releaseProperty("compose.desktop.mac.signing.identity")
val macNotarizationKeychainProfile = releaseProperty("compose.desktop.mac.notarization.keychainProfile")
val macNotarizationKeychainPath = releaseProperty("compose.desktop.mac.notarization.keychainPath")

fun AbstractJPackageTask.packagedDmg(): java.io.File =
    destinationDir.asFile.get().toPath().listDirectoryEntries("$baseName*.dmg").single().toFile()

val desktopJavaHome = extensions.getByType<JavaToolchainService>()
    .launcherFor {
        languageVersion.set(desktopJvmVersion)
        vendor.set(desktopJvmVendor)
    }
    .map { launcher -> launcher.metadata.installationPath.asFile.absolutePath }

compose.desktop {
    application {
        mainClass = "des.c5inco.mesh.MainKt"
        javaHome = desktopJavaHome.get()

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            // The JDK modules the app's jars use, from suggestRuntimeModules. Rerun it after dependency
            // upgrades: jlink otherwise bundles only the modules Compose detects on its own.
            modules("java.compiler", "java.instrument", "java.management", "java.prefs", "jdk.unsupported")

            packageVersion = version.toString()
            packageName = baseName
            description = "Create and edit smooth mesh gradients"
            vendor = "Chris Sinco"
            licenseFile = rootProject.file("LICENSE")

            macOS {
                dockName = baseName
                iconFile = rootProject.file("artwork/icon.icns")
                bundleID = "des.c5inco.mesh"
                appCategory = "public.app-category.graphics-design"

                signing {
                    macSign?.let { sign.set(it) }
                    macSigningIdentity?.let { identity.set(it) }
                }
            }
        }
    }
}

// Fail before packaging: an unsigned or ad hoc signed DMG can never pass notarization.
gradle.taskGraph.whenReady {
    val notarizationTasks = allTasks.filterIsInstance<AbstractNotarizationTask>()
    if (notarizationTasks.isEmpty()) return@whenReady
    val taskNames = notarizationTasks.joinToString { it.name }
    require(macSign == true && macSigningIdentity != null) {
        """
        $taskNames requires a Developer ID signed app.
        Set compose.desktop.mac.sign=true and compose.desktop.mac.signing.identity in ~/.gradle/gradle.properties or local.properties.
        See docs/RELEASING.md.
        """.trimIndent()
    }
    require(macNotarizationKeychainProfile != null) {
        """
        Missing notarytool keychain profile for $taskNames.
        Set compose.desktop.mac.notarization.keychainProfile in local.properties or ~/.gradle/gradle.properties.
        See docs/RELEASING.md.
        """.trimIndent()
    }
}

tasks.withType<AbstractNotarizationTask>().configureEach {
    group = "compose desktop"
    description = "Notarize, staple, and verify the packaged DMG with a local notarytool keychain profile."

    val execOperations = serviceOf<ExecOperations>()
    val packageTaskName = when (name) {
        "notarizeReleaseDmg" -> "packageReleaseDmg"
        else -> "packageDmg"
    }

    dependsOn(packageTaskName)
    actions.clear()

    doLast {
        val dmg = tasks.named<AbstractJPackageTask>(packageTaskName).get().packagedDmg()
        val identity = macSigningIdentity!!.let { if (it.startsWith("Developer ID Application:")) it else "Developer ID Application: $it" }
        val credentials = listOf("--keychain-profile", macNotarizationKeychainProfile!!) +
            macNotarizationKeychainPath?.let { listOf("--keychain", it) }.orEmpty()
        val mountPoint = temporaryDir.resolve("dmg-mount")

        // Logs a command's output and returns its exit code, stdout, and stderr.
        fun run(vararg command: String, check: Boolean = true): Triple<Int, String, String> {
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            val exitCode = execOperations.exec {
                commandLine(*command)
                standardOutput = stdout
                errorOutput = stderr
                isIgnoreExitValue = true
            }.exitValue
            listOf(stdout, stderr).map { it.toString().trimEnd() }.filter { it.isNotEmpty() }.forEach { logger.lifecycle(it) }
            if (check && exitCode != 0) {
                throw GradleException("`${command.joinToString(" ")}` failed with exit code $exitCode.")
            }
            return Triple(exitCode, stdout.toString(), stderr.toString())
        }

        fun requireNotarizedDeveloperId(vararg assessCommand: String) {
            val (_, stdout, stderr) = run(*assessCommand)
            if (!(stdout + stderr).contains("source=Notarized Developer ID")) {
                throw GradleException("Gatekeeper did not assess ${assessCommand.last()} as Notarized Developer ID.")
            }
        }

        // Detaches every mount of this DMG (such as one left by the manual commands in docs/RELEASING.md)
        // and anything mounted at the task's mount point, then removes the empty mount point.
        // Never delete recursively here: a mount contains the DMG's Applications -> /Applications symlink.
        fun detachMounts() {
            val plist = ByteArrayOutputStream()
            execOperations.exec {
                commandLine("hdiutil", "info", "-plist")
                standardOutput = plist
            }
            // Read the XML plist directly: it can contain <data> values that `plutil -convert json` rejects.
            fun Element.children() = (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>()
            fun Element.value(): Any? = when (tagName) {
                "dict" -> children().chunked(2).associate { (key, value) -> key.textContent to value.value() }
                "array" -> children().map { it.value() }
                "string" -> textContent
                else -> null
            }
            val documentBuilder = DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            }.newDocumentBuilder()
            val info = documentBuilder.parse(plist.toByteArray().inputStream()).documentElement.children().single().value()
            val images = (info as? Map<*, *>)?.get("images") as? List<*>
            images.orEmpty().filterIsInstance<Map<*, *>>().forEach { image ->
                val entities = (image["system-entities"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
                val mountPoints = entities.mapNotNull { (it["mount-point"] as? String)?.let { path -> File(path).canonicalFile } }
                val isThisDmg = (image["image-path"] as? String)?.let { File(it).canonicalFile } == dmg.canonicalFile
                val devices = entities.mapNotNull { it["dev-entry"] as? String }
                // The whole-disk entry (/dev/diskN) ejects every partition of the image.
                val device = devices.firstOrNull { Regex("/dev/disk\\d+").matches(it) } ?: devices.firstOrNull()
                if ((isThisDmg || mountPoint.canonicalFile in mountPoints) && device != null) {
                    // Only this build's DMG or the task's own mount point gets here, so forcing the eject is safe.
                    if (run("hdiutil", "detach", device, check = false).first != 0 &&
                        run("hdiutil", "detach", "-force", device, check = false).first != 0
                    ) {
                        throw GradleException("Could not eject $device (mounted at ${mountPoints.joinToString()}). Eject it and rerun $name.")
                    }
                }
            }
            if (mountPoint.exists() && !mountPoint.delete()) {
                throw GradleException("$mountPoint is not empty. Remove it and rerun $name.")
            }
        }

        try {
            // Before signing, so a stale mount cannot make codesign or the later attach fail.
            detachMounts()
            // Sign the DMG itself so the stapled DMG can be assessed by Gatekeeper without a network lookup.
            run("codesign", "--force", "--timestamp", "--sign", identity, dmg.absolutePath)

            // notarytool's exit code alone does not prove acceptance, so require the JSON status.
            val (submitExit, submitStdout, submitStderr) = run(
                *(listOf("xcrun", "notarytool", "submit", dmg.absolutePath) + credentials +
                    listOf("--wait", "--timeout", "30m", "--output-format", "json")).toTypedArray(),
                check = false,
            )
            val submission = runCatching {
                JsonSlurper().parseText(submitStdout.substring(submitStdout.indexOf('{'), submitStdout.lastIndexOf('}') + 1)) as Map<*, *>
            }.getOrNull()
            val submissionId = submission?.get("id") as? String
            val status = submission?.get("status") as? String
            logger.lifecycle("Notarization submission ${submissionId ?: "<none>"}: ${status ?: "<no status>"}")
            if (status != "Accepted") {
                if (submissionId != null) {
                    run("xcrun", "notarytool", "log", submissionId, *credentials.toTypedArray(), check = false)
                }
                throw GradleException(
                    "Apple notarization did not accept ${dmg.name} (status=$status, id=$submissionId, notarytool exit=$submitExit). " +
                        "stdout: ${submitStdout.trim()} stderr: ${submitStderr.trim()}",
                )
            }

            run("xcrun", "stapler", "staple", dmg.absolutePath)
            run("xcrun", "stapler", "validate", dmg.absolutePath)
            requireNotarizedDeveloperId(
                "spctl", "--assess", "--type", "open", "--context", "context:primary-signature", "--verbose=4", dmg.absolutePath,
            )

            try {
                // The DMG has a license agreement, so hdiutil cancels the attach unless it is accepted on stdin.
                execOperations.exec {
                    commandLine("hdiutil", "attach", dmg.absolutePath, "-readonly", "-nobrowse", "-mountpoint", mountPoint.absolutePath)
                    standardInput = "Y\n".byteInputStream()
                    standardOutput = ByteArrayOutputStream()
                }
                val app = mountPoint.resolve("$baseName.app").absolutePath
                run("codesign", "--verify", "--deep", "--strict", "--verbose=2", app)
                requireNotarizedDeveloperId("spctl", "--assess", "--type", "execute", "--verbose=4", app)
            } catch (failure: Throwable) {
                // Also covers a failed attach that still left a mount; keep the verification error as the cause.
                runCatching { detachMounts() }.exceptionOrNull()?.let(failure::addSuppressed)
                throw failure
            }
        } catch (failure: Throwable) {
            // Keep a stapled but unverified DMG out of the release path, and fail loudly if that is impossible.
            val unverified = dmg.resolveSibling("${dmg.name}.unverified")
            try {
                Files.move(dmg.toPath(), unverified.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (moveFailure: Exception) {
                failure.addSuppressed(moveFailure)
                throw GradleException(
                    "$dmg failed notarization or verification and could not be moved off the release path. " +
                        "Delete it and do not distribute it.",
                    failure,
                )
            }
            logger.error("Moved ${dmg.name} to ${unverified.name}; do not distribute it.")
            throw failure
        }
        // The DMG is verified at this point; a leftover mount is ejected by the next run, so only warn.
        runCatching { detachMounts() }.onFailure { logger.warn("Could not clean up $mountPoint: ${it.message}") }
        logger.lifecycle("Notarized, stapled, and verified ${dmg.absolutePath}")
    }
}

val currentArch: String = when (val osArch = System.getProperty("os.arch")) {
    "x86_64", "amd64" -> "x64"
    "aarch64" -> "arm64"
    else -> error("Unsupported OS arch: $osArch")
}

val renameDmg by tasks.registering(Copy::class) {
    group = "distribution"
    description = "Copy the notarized DMG to mesh-<version>-mac-<arch>.dmg"

    val fromFile = tasks.named<AbstractJPackageTask>("packageDmg").map { task ->
        task.destinationDir.asFile.get().toPath().listDirectoryEntries("$baseName*.dmg").single()
    }

    // The release-named copy must always be the notarized, stapled DMG.
    dependsOn("notarizeDmg")
    from(fromFile)
    into(fromFile.map { it.parent })
    rename {
        "mesh-$version-mac-$currentArch.dmg"
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "false")
    systemProperty("skiko.renderApi", "SOFTWARE_COMPAT")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        systemProperty("apple.awt.UIElement", "true")
    }
}

tasks.named<Test>("desktopTest") {
    useJUnitPlatform {
        includeTags("spectre")
    }
    forkEvery = 1
    maxParallelForks = 1
}

val desktopUnitTest by tasks.registering(Test::class) {
    description = "Runs desktop unit tests without the live Spectre UI tests."
    group = "verification"

    val desktopTest = tasks.named<Test>("desktopTest").get()
    testClassesDirs = desktopTest.testClassesDirs
    classpath = desktopTest.classpath
    useJUnitPlatform {
        excludeTags("spectre")
    }
    // Unit tests need the Swing event thread (Dispatchers.Main). On Linux without an X display it can
    // only start headless; tests that need a display, such as the clipboard, then skip themselves.
    val noDisplay = System.getProperty("os.name").startsWith("Linux") && System.getenv("DISPLAY").isNullOrEmpty()
    systemProperty("java.awt.headless", noDisplay.toString())
}

tasks.named("check") {
    dependsOn(desktopUnitTest)
}

tasks.register("spectreTest") {
    description = "Runs live Compose Desktop UI tests with Spectre."
    group = "verification"
    dependsOn("desktopTest")
}
