plugins {
    kotlin("jvm")
    // Needed for reified serializer<T>() resolution at our AgUiJson call sites.
    kotlin("plugin.serialization")
    application
}

val ktorVersion = "3.2.4"
val coroutinesVersion = "1.10.2"
val serializationVersion = "1.8.1"

dependencies {
    // AG-UI SDK from mavenLocal. Base coordinates: Gradle module metadata resolves
    // the JVM variant of these KMP modules for a plain kotlin("jvm") consumer.
    implementation("com.ag-ui.community:kotlin-core:0.4.1")
    implementation("com.ag-ui.community:kotlin-encoder:0.4.1")

    // kotlin-core exposes kotlinx-serialization-json only as `implementation`, so a
    // consumer that calls AgUiJson (decode/encode) must depend on it directly.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("ch.qos.logback:logback-classic:1.5.13")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}

application {
    mainClass.set("com.agui.example.server.ktor.MainKt")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
