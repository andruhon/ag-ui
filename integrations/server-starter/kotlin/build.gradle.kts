// Root build for the AG-UI Kotlin server-starter integrations.
//
// Two flavours of the same recipe (core types + EventEncoder + a web framework):
//   :ktor   — pure-Kotlin Ktor CIO server
//   :spring — Spring Boot 3 servlet MVC server (shaped with Beacon in mind)
//
// Both consume com.ag-ui.community:kotlin-core + :kotlin-encoder from mavenLocal().
// Plugin versions are declared here (apply false) and applied per-subproject.

plugins {
    kotlin("jvm") version "2.1.20" apply false
    kotlin("plugin.serialization") version "2.1.20" apply false
    kotlin("plugin.spring") version "2.1.20" apply false
    id("org.springframework.boot") version "3.3.5" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
}

allprojects {
    group = "com.ag-ui.community.examples"
    version = "0.4.1"
}
