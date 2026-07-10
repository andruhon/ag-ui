package com.agui.example.server.spring

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

/**
 * AG-UI Spring Boot 3 (servlet MVC) server starter, written in Kotlin.
 *
 * Shaped with Beacon (nova/beacon: Kotlin + Spring Boot + Koog) in mind so its
 * team can lift the pattern. Like the Ktor and Python starters, the "server" is
 * just: core types + [com.agui.encoder.EventEncoder] + a web framework.
 */
@SpringBootApplication
class Application {
    /**
     * The agent seam as a bean. Swap [EchoAgent] here for a real agent (e.g. one
     * that adapts a Koog `AIAgent`) without touching the controller.
     */
    @Bean
    fun agent(): Agent = EchoAgent
}

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
