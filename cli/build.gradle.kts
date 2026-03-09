plugins {
    kotlin("multiplatform")
}

val appVersion: String by project

group = "io.onema"
version = appVersion

repositories {
    mavenCentral()
}

val generatedSourcesDir = layout.buildDirectory.dir("generated/source/version/commonMain")

val generateVersionFile by tasks.register("generateVersionFile") {
    val file = generatedSourcesDir.get().file("io/onema/divetelemetry/cli/AppVersion.kt")
    outputs.file(file)
    doLast {
        file.asFile.parentFile.mkdirs()
        file.asFile.writeText(
            """
            package io.onema.divetelemetry.cli

            internal object AppVersion {
                const val VERSION = "$appVersion"
            }
            """.trimIndent() + "\n"
        )
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateVersionFile)
}

kotlin {
    jvm {
        mainRun {
            mainClass = "io.onema.divetelemetry.cli.MainKt"
        }
    }

    macosArm64 {
        binaries {
            executable {
                entryPoint = "io.onema.divetelemetry.cli.main"
            }
        }
    }

    macosX64 {
        binaries {
            executable {
                entryPoint = "io.onema.divetelemetry.cli.main"
            }
        }
    }

    linuxX64 {
        binaries {
            executable {
                entryPoint = "io.onema.divetelemetry.cli.main"
            }
        }
    }

    mingwX64 {
        binaries {
            executable {
                entryPoint = "io.onema.divetelemetry.cli.main"
            }
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generatedSourcesDir)
            dependencies {
                implementation(project(":lib"))
                implementation("com.github.ajalt.clikt:clikt:5.1.0")
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
