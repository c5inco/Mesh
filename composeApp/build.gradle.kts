@file:Suppress("UnstableApiUsage")

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import kotlin.io.path.listDirectoryEntries

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    //alias(libs.plugins.ksp)
    //alias(libs.plugins.room)
}

repositories {
    maven("https://packages.jetbrains.team/maven/p/kpm/public/")
    mavenCentral()
    google()
}

version = "1.0.0"
val baseName = "Mesh"

kotlin {
    jvm("desktop")

    jvmToolchain {
        vendor = JvmVendorSpec.JETBRAINS
        languageVersion = JavaLanguageVersion.of(17)
    }
    
    sourceSets {
        val desktopMain by getting
        
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(kotlinDocumentStore.leveldb)
            implementation(libs.kotlinx.serialization.json)
            //implementation(libs.room.runtime)
            //implementation(libs.room.ktx)
        }
        desktopMain.dependencies {
            // See https://github.com/JetBrains/Jewel/releases for the release notes
            implementation("org.jetbrains.jewel:jewel-int-ui-standalone-243:0.27.0")

            implementation(compose.desktop.currentOs) {
                exclude(group = "org.jetbrains.compose.material")
            }
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.kotlinpoet)
        }
    }
}


compose.desktop {
    application {
        mainClass = "des.c5inco.mesh.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)

            packageVersion = version.toString()
            packageName = baseName
            description = baseName
            vendor = "Chris Sinco"
            licenseFile = rootProject.file("LICENSE")

            macOS {
                dockName = baseName
                iconFile = rootProject.file("artwork/icon.icns")
                bundleID = "des.c5inco.mesh"
            }
        }
    }
}

val currentArch: String = when (val osArch = System.getProperty("os.arch")) {
    "x86_64", "amd64" -> "x64"
    "aarch64" -> "arm64"
    else -> error("Unsupported OS arch: $osArch")
}

/**
 * TODO: workaround for https://youtrack.jetbrains.com/issue/CMP-4976.
 */
val renameDmg by tasks.registering(Copy::class) {
    group = "distribution"
    description = "Rename the DMG file"

    val packageReleaseDmg = tasks.named<AbstractJPackageTask>("packageReleaseDmg")
    // build/compose/binaries/main-release/dmg/*.dmg
    val fromFile = packageReleaseDmg.map { task ->
        task.destinationDir.asFile.get().toPath().listDirectoryEntries("$baseName*.dmg").single()
    }

    from(fromFile)
    into(fromFile.map { it.parent })
    rename {
        "mesh-$currentArch-$version.dmg"
    }
}

tasks.assemble {
    dependsOn(renameDmg)
}