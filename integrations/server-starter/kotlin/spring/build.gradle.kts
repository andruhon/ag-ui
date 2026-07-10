plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    // Needed for reified serializer<T>() resolution at our AgUiJson call sites.
    kotlin("plugin.serialization")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

val serializationVersion = "1.8.1"

// Spring Boot's BOM pins kotlinx-serialization to 1.6.3, but kotlin-core is
// compiled against 1.8.1. Without this override the older serialization-core is
// forced onto the classpath and decoding throws AbstractMethodError
// (GeneratedSerializer.typeParametersSerializers). Align the BOM to 1.8.1.
extra["kotlin-serialization.version"] = serializationVersion

dependencies {
    // AG-UI SDK from mavenLocal. Base coordinates: Gradle module metadata resolves
    // the JVM variant of these KMP modules for a plain kotlin("jvm") consumer.
    implementation("com.ag-ui.community:kotlin-core:0.4.1")
    implementation("com.ag-ui.community:kotlin-encoder:0.4.1")

    // We serialize the wire with AgUiJson (kotlinx-serialization), NOT Spring's
    // Jackson. kotlin-core exposes serialization-json only as `implementation`, so
    // depend on it directly.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.test {
    useJUnitPlatform()
}
