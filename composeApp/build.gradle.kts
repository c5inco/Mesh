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
import java.util.Properties
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
        }
        desktopTest.dependencies {
            implementation(libs.spectre.core)
            implementation(libs.spectre.testing)
            implementation(libs.junit.jupiter)
            runtimeOnly(libs.junit.platform.launcher)
            implementation(libs.kotlinx.coroutines.swing)
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
            // From suggestRuntimeModules. Without them the packaged app fails at launch (sun/misc/Unsafe).
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
        val credentials = listOf("--keychain-profile", macNotarizationKeychainProfile!!) +
            macNotarizationKeychainPath?.let { listOf("--keychain", it) }.orEmpty()

        fun run(vararg command: String, ignoreExitValue: Boolean = false): String {
            val output = ByteArrayOutputStream()
            val result = execOperations.exec {
                commandLine(*command)
                standardOutput = output
                errorOutput = output
                isIgnoreExitValue = true
            }
            val text = output.toString()
            logger.lifecycle(text.trimEnd())
            if (!ignoreExitValue && result.exitValue != 0) {
                throw GradleException("`${command.joinToString(" ")}` failed with exit code ${result.exitValue}.")
            }
            return text
        }

        // notarytool's exit code alone does not prove acceptance, so require the JSON status.
        val submitOutput = ByteArrayOutputStream()
        val submitResult = execOperations.exec {
            commandLine(
                listOf("xcrun", "notarytool", "submit", dmg.absolutePath) + credentials +
                    listOf("--wait", "--timeout", "30m", "--output-format", "json"),
            )
            standardOutput = submitOutput
            isIgnoreExitValue = true
        }
        val submission = runCatching { JsonSlurper().parseText(submitOutput.toString()) as Map<*, *> }.getOrNull()
        val submissionId = submission?.get("id") as String?
        val status = submission?.get("status") as String?
        logger.lifecycle("Notarization submission ${submissionId ?: "<none>"}: ${status ?: "<no status>"}")
        if (status != "Accepted") {
            if (submissionId != null) {
                run("xcrun", "notarytool", "log", submissionId, *credentials.toTypedArray(), ignoreExitValue = true)
            }
            throw GradleException(
                "Apple notarization did not accept ${dmg.name} (status=$status, id=$submissionId, " +
                    "notarytool exit=${submitResult.exitValue}). Output: ${submitOutput.toString().trim()}",
            )
        }

        run("xcrun", "stapler", "staple", dmg.absolutePath)
        run("xcrun", "stapler", "validate", dmg.absolutePath)

        val mountPoint = temporaryDir.resolve("dmg-mount").apply { deleteRecursively() }
        // The DMG has a license agreement, so hdiutil cancels the attach unless it is accepted on stdin.
        execOperations.exec {
            commandLine("hdiutil", "attach", dmg.absolutePath, "-readonly", "-nobrowse", "-mountpoint", mountPoint.absolutePath)
            standardInput = "Y\n".byteInputStream()
            standardOutput = ByteArrayOutputStream()
        }
        try {
            val app = mountPoint.resolve("$baseName.app").absolutePath
            run("codesign", "--verify", "--deep", "--strict", "--verbose=2", app)
            val assessment = run("spctl", "--assess", "--type", "execute", "--verbose=4", app)
            if (!assessment.contains("source=Notarized Developer ID")) {
                throw GradleException("Gatekeeper did not assess $app as Notarized Developer ID.")
            }
        } finally {
            run("hdiutil", "detach", mountPoint.absolutePath, ignoreExitValue = true)
        }
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
    description = "Rename the packaged DMG to mesh-<version>-mac-<arch>.dmg"

    val fromFile = tasks.named<AbstractJPackageTask>("packageDmg").map { task ->
        task.destinationDir.asFile.get().toPath().listDirectoryEntries("$baseName*.dmg").single()
    }

    dependsOn("packageDmg")
    // Copy the stapled DMG when notarization runs in the same build.
    mustRunAfter(tasks.withType<AbstractNotarizationTask>())
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
}

tasks.named("check") {
    dependsOn(desktopUnitTest)
}

tasks.register("spectreTest") {
    description = "Runs live Compose Desktop UI tests with Spectre."
    group = "verification"
    dependsOn("desktopTest")
}
