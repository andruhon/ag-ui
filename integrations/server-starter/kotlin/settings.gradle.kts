rootProject.name = "ag-ui-kotlin-server-starter"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        // The AG-UI Kotlin SDK (:kotlin-core, :kotlin-encoder) is consumed from the
        // local Maven cache. Publish it first with, from sdks/community/kotlin/library/:
        //   ./gradlew :kotlin-core:publishToMavenLocal :kotlin-encoder:publishToMavenLocal
        mavenLocal()
        mavenCentral()
    }
}

include(":ktor")
include(":spring")
