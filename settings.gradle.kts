pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

rootProject.name = "dive-telemetry"
include(":lib")
include(":app")
include(":cli")
