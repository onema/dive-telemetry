import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
}

val appVersion: String by project

group = "io.onema"
version = appVersion

repositories {
    mavenCentral()
    google()
}

val generatedSourcesDir = layout.buildDirectory.dir("generated/source/version")

sourceSets.main {
    kotlin.srcDir(generatedSourcesDir)
}

val generateVersionFile by tasks.register("generateVersionFile") {
    val file = generatedSourcesDir.get().file("io/onema/divetelemetry/app/AppVersion.kt")
    outputs.file(file)
    doLast {
        file.asFile.parentFile.mkdirs()
        file.asFile.writeText("""
            package io.onema.divetelemetry.app

            object AppVersion {
                const val version = "$appVersion"
            }
        """.trimIndent())
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(generateVersionFile)
}

dependencies {
    implementation(project(":lib"))
    implementation(compose.desktop.currentOs)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
}

compose.desktop.application {
    mainClass = "io.onema.divetelemetry.app.MainKt"
    nativeDistributions {
        targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
        packageName = "Dive Telemetry "
        packageVersion = appVersion
        description = "Convert dive computer exports into Telemetry  format"
        vendor = "onema.io"
        macOS {
            bundleID = "io.onema.divetelemetry"
            appCategory = "public.app-category.utilities"
            iconFile.set(project.file("src/main/resources/icon.icns"))
        }
        linux {
            shortcut = true
            iconFile.set(project.file("src/main/resources/icon.png"))
        }
        windows {
            shortcut = true
            menu = true
            perUserInstall = true
            iconFile.set(project.file("src/main/resources/icon.ico"))
        }
    }
}
