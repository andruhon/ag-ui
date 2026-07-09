package com.agui.server.ktor

import com.agui.client.sse.SseParser
import com.agui.core.types.*
import com.agui.server.AgUiAgent
import com.agui.server.EventFactory
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AguiEndpointTest {

    /** Emits a valid RUN_STARTED -> TEXT_MESSAGE(hello) -> RUN_FINISHED sequence. */
    private fun echoAgent() = AgUiAgent { input ->
        flowOf(
            EventFactory.runStarted(input.threadId, input.runId),
            EventFactory.textMessageStart("m1"),
            EventFactory.textMessageContent("m1", "hello"),
            EventFactory.textMessageEnd("m1"),
            EventFactory.runFinished(input.threadId, input.runId)
        )
    }

    @Test
    fun echoRoundTripIsClientWireCompatible() = testApplication {
        application {
            routing {
                aguiEndpoint(agent = echoAgent())
            }
        }

        val requestBody = AgUiJson.encodeToString(
            RunAgentInput(threadId = "thread-1", runId = "run-1")
        )

        val response = client.post("/agents/agent-x/run") {
            setBody(requestBody)
        }

        val sseBody = response.bodyAsText()

        // Extract the JSON payload from each `data:` line, exactly as an SSE client
        // does (strip the "data:" prefix and the single leading space), then feed
        // those payloads through the CLIENT SDK's SseParser to prove wire compat.
        val dataPayloads = sseBody.lineSequence()
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .toList()

        val events = SseParser().parseFlow(dataPayloads.asFlow()).toList()

        assertEquals(5, events.size, "expected 5 decoded events, body was: $sseBody")
        assertEquals(EventType.RUN_STARTED, events[0].eventType)
        assertEquals(EventType.TEXT_MESSAGE_START, events[1].eventType)

        val content = events[2] as TextMessageContentEvent
        assertEquals("m1", content.messageId)
        assertEquals("hello", content.delta)

        assertEquals(EventType.TEXT_MESSAGE_END, events[3].eventType)

        val finished = events[4] as RunFinishedEvent
        assertEquals(EventType.RUN_FINISHED, finished.eventType)
        assertEquals("thread-1", finished.threadId)
        assertEquals("run-1", finished.runId)
    }

    /** Emits RUN_STARTED then throws mid-stream (A1). */
    private fun failingAgent() = AgUiAgent { input ->
        flow {
            emit(EventFactory.runStarted(input.threadId, input.runId))
            throw RuntimeException("mid-stream boom")
        }
    }

    @Test
    fun midStreamFailureEndsWithParseableRunError() = testApplication {
        application {
            routing {
                aguiEndpoint(agent = failingAgent())
            }
        }

        val requestBody = AgUiJson.encodeToString(
            RunAgentInput(threadId = "thread-1", runId = "run-1")
        )

        val response = client.post("/agents/agent-x/run") {
            setBody(requestBody)
        }

        val sseBody = response.bodyAsText()

        val dataPayloads = sseBody.lineSequence()
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .toList()

        val events = SseParser().parseFlow(dataPayloads.asFlow()).toList()

        // The stream must be well-formed and terminate with RUN_ERROR, not truncate.
        assertTrue(events.isNotEmpty(), "expected at least one event, body was: $sseBody")
        assertEquals(EventType.RUN_STARTED, events.first().eventType)

        val error = events.last() as RunErrorEvent
        assertEquals(EventType.RUN_ERROR, error.eventType)
        assertEquals("mid-stream boom", error.message)
    }
}
