package com.agui.example.server.ktor

import com.agui.core.types.BaseEvent
import com.agui.core.types.Role
import com.agui.core.types.RunAgentInput
import com.agui.core.types.RunFinishedEvent
import com.agui.core.types.RunStartedEvent
import com.agui.core.types.TextMessageContentEvent
import com.agui.core.types.TextMessageEndEvent
import com.agui.core.types.TextMessageStartEvent
import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

/**
 * Echo agent transliterated directly from the Python example server: it emits the
 * canonical run/message lifecycle. Events are constructed straight from
 * `com.agui.core.types.*` with named args (Kotlin data classes ≈ pydantic/TS
 * constructors) — no EventFactory. This function is the seam where a real
 * streaming agent would replace the echo body.
 */
fun echoAgent(input: RunAgentInput): Flow<BaseEvent> = flow {
    emit(RunStartedEvent(threadId = input.threadId, runId = input.runId))

    val messageId = UUID.randomUUID().toString()
    emit(TextMessageStartEvent(messageId = messageId, role = Role.ASSISTANT))
    emit(TextMessageContentEvent(messageId = messageId, delta = "Hello world!"))
    emit(TextMessageEndEvent(messageId = messageId))

    emit(RunFinishedEvent(threadId = input.threadId, runId = input.runId))
}

fun Application.module() {
    routing {
        get("/health") { call.respondText("ok") }
        aguiEndpoint(agent = ::echoAgent)
    }
}

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8000
    embeddedServer(CIO, port = port, host = "0.0.0.0", module = Application::module).start(wait = true)
}
