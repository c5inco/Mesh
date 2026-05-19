@file:Suppress("UnstableApiUsage")

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
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

kotlin {
    jvm("desktop")

    jvmToolchain {
        vendor = JvmVendorSpec.JETBRAINS
        languageVersion = JavaLanguageVersion.of(25)
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

compose.desktop {
    application {
        mainClass = "des.c5inco.mesh.MainKt"

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
            }
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
