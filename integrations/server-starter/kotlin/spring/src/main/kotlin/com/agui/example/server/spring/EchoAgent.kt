package com.agui.example.server.spring

import com.agui.core.types.BaseEvent
import com.agui.core.types.CustomEvent
import com.agui.core.types.Role
import com.agui.core.types.RunAgentInput
import com.agui.core.types.RunFinishedEvent
import com.agui.core.types.RunStartedEvent
import com.agui.core.types.TextMessageContentEvent
import com.agui.core.types.TextMessageEndEvent
import com.agui.core.types.TextMessageStartEvent
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * The agent seam.
 *
 * This is the single place a real agent replaces the echo body. In Beacon, a Koog
 * `AIAgent` streams `StreamFrame`s; the translation Koog `StreamFrame` -> AG-UI
 * [BaseEvent] plugs in here, producing the same ordered sequence of events. The
 * servlet model needs no coroutine bridging — a plain `Sequence<BaseEvent>` is
 * written straight to the response `OutputStream`.
 *
 * Note: [RunAgentInput.threadId] is required (non-nullable) in kotlin-core, so it
 * can be echoed straight back into the lifecycle events.
 */
fun interface Agent {
    fun run(input: RunAgentInput): Sequence<BaseEvent>
}

/**
 * Echo agent transliterated from the Python example server, plus one CUSTOM event
 * to exercise the path Beacon / unimarket-ui depend on (`AGENT_SELECTED`). Events
 * are constructed directly from `com.agui.core.types.*` with named args — no
 * EventFactory.
 */
object EchoAgent : Agent {
    override fun run(input: RunAgentInput): Sequence<BaseEvent> = sequence {
        yield(RunStartedEvent(threadId = input.threadId, runId = input.runId))

        // CUSTOM path: unimarket-ui and Beacon route on AGENT_SELECTED / ROUTING_PHRASE.
        yield(
            CustomEvent(
                name = "AGENT_SELECTED",
                value = buildJsonObject {
                    put("agentId", "echo")
                    put("routingPhrase", "hello world")
                },
            ),
        )

        val messageId = UUID.randomUUID().toString()
        yield(TextMessageStartEvent(messageId = messageId, role = Role.ASSISTANT))
        yield(TextMessageContentEvent(messageId = messageId, delta = "Hello world!"))
        yield(TextMessageEndEvent(messageId = messageId))

        yield(RunFinishedEvent(threadId = input.threadId, runId = input.runId))
    }
}
