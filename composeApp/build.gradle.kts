@file:Suppress("UnstableApiUsage")

import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.process.ExecOperations
import org.gradle.kotlin.dsl.support.serviceOf
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.compose.desktop.application.tasks.AbstractNotarizationTask
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
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
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
                    releaseProperty("compose.desktop.mac.sign")?.toBooleanStrictOrNull()?.let {
                        sign.set(it)
                    }
                    releaseProperty("compose.desktop.mac.signing.identity")?.let {
                        identity.set(it)
                    }
                }
            }
        }
    }
}

tasks.withType<AbstractNotarizationTask>().configureEach {
    group = "compose desktop"
    description = "Notarize the packaged DMG with a local notarytool keychain profile."

    val execOperations = serviceOf<ExecOperations>()
    val packageTaskName = when (name) {
        "notarizeReleaseDmg" -> "packageReleaseDmg"
        else -> "packageDmg"
    }

    dependsOn(packageTaskName)
    actions.clear()

    doFirst {
        require(!macNotarizationKeychainProfile.isNullOrEmpty()) {
            """
            Missing notarytool keychain profile for $name.
            Set compose.desktop.mac.notarization.keychainProfile in local.properties or ~/.gradle/gradle.properties.
            See docs/RELEASING.md.
            """.trimIndent()
        }
    }

    doLast {
        val dmg = tasks.named<AbstractJPackageTask>(packageTaskName).get().packagedDmg()
        val submitCommand = mutableListOf(
            "xcrun",
            "notarytool",
            "submit",
            dmg.absolutePath,
            "--keychain-profile",
            macNotarizationKeychainProfile!!,
            "--wait",
            "--timeout",
            "30m",
        )
        macNotarizationKeychainPath?.let { keychainPath ->
            submitCommand += listOf("--keychain", keychainPath)
        }

        execOperations.exec {
            commandLine(submitCommand)
        }
        execOperations.exec {
            commandLine("xcrun", "stapler", "staple", dmg.absolutePath)
        }
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

tasks.register("spectreTest") {
    description = "Runs live Compose Desktop UI tests with Spectre."
    group = "verification"
    dependsOn("desktopTest")
}
