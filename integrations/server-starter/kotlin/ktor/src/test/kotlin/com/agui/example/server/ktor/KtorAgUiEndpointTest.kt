package com.agui.example.server.ktor

import com.agui.core.types.AgUiJson
import com.agui.core.types.BaseEvent
import com.agui.core.types.RunAgentInput
import com.agui.core.types.RunFinishedEvent
import com.agui.core.types.RunStartedEvent
import com.agui.core.types.TextMessageContentEvent
import com.agui.core.types.TextMessageEndEvent
import com.agui.core.types.TextMessageStartEvent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorAgUiEndpointTest {

    /** Split an SSE body into decoded events, asserting canonical `data: {json}\n\n` framing. */
    private fun parseFrames(body: String): List<BaseEvent> =
        body.split("\n\n")
            .filter { it.isNotBlank() }
            .map { frame ->
                assertTrue(frame.startsWith("data: "), "frame must start with 'data: ': <$frame>")
                AgUiJson.decodeFromString<BaseEvent>(frame.removePrefix("data: "))
            }

    @Test
    fun echoStreamProducesCanonicalSseSequence() = testApplication {
        application { module() }

        val input = RunAgentInput(threadId = "t1", runId = "r1")
        val response = client.post("/agents/my-agent/run") {
            contentType(ContentType.Application.Json)
            setBody(AgUiJson.encodeToString(input))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Text.EventStream, response.headers["Content-Type"]?.let { ContentType.parse(it) })

        val body = response.bodyAsText()
        assertTrue(body.endsWith("\n\n"), "SSE body must end with a blank line")

        val events = parseFrames(body)
        assertEquals(5, events.size, "expected the 5-event echo lifecycle")
        assertTrue(events[0] is RunStartedEvent)
        assertTrue(events[1] is TextMessageStartEvent)
        assertTrue(events[2] is TextMessageContentEvent)
        assertEquals("Hello world!", (events[2] as TextMessageContentEvent).delta)
        assertTrue(events[3] is TextMessageEndEvent)
        assertTrue(events[4] is RunFinishedEvent)

        val started = events[0] as RunStartedEvent
        assertEquals("t1", started.threadId)
        assertEquals("r1", started.runId)
    }

    @Test
    fun malformedBodyReturns4xx() = testApplication {
        application { module() }

        val response = client.post("/agents/my-agent/run") {
            contentType(ContentType.Application.Json)
            setBody("{ this is not valid json")
        }

        assertTrue(response.status.value in 400..499, "expected 4xx, got ${response.status}")
    }
}
