plugins {
    kotlin("multiplatform")
}

group = "io.onema"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
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
            api("com.squareup.okio:okio:3.9.1")
            api("io.arrow-kt:arrow-core:2.2.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("com.squareup.okio:okio-fakefilesystem:3.9.1")
        }
    }
}
