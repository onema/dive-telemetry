plugins {
    kotlin("multiplatform")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
}

group = "io.onema"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    config.from(rootProject.file("detekt.yml"))
    reports {
        sarif.required.set(true)
    }
}

kotlin {
    // JVM target (for IntelliJ debugging + Compose Desktop app)
    jvm()

    // macOS targets
    macosArm64()
    macosX64()

    // Linux and Windows targets
    linuxX64()
    mingwX64()

    sourceSets {
        commonMain.dependencies {
            api("com.squareup.okio:okio:3.17.0")
            api("io.arrow-kt:arrow-core:2.2.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("com.squareup.okio:okio-fakefilesystem:3.17.0")
        }
    }
}
