package com.agui.example.server.spring

import com.agui.core.types.AgUiJson
import com.agui.core.types.BaseEvent
import com.agui.core.types.CustomEvent
import com.agui.core.types.RunAgentInput
import com.agui.core.types.RunFinishedEvent
import com.agui.core.types.RunStartedEvent
import com.agui.core.types.TextMessageContentEvent
import com.agui.core.types.TextMessageEndEvent
import com.agui.core.types.TextMessageStartEvent
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Drives the real embedded servlet container (RANDOM_PORT) with the JDK HTTP
 * client so the StreamingResponseBody path is exercised end-to-end, then decodes
 * the raw SSE body with AgUiJson.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgUiControllerTest {

    @LocalServerPort
    private var port: Int = 0

    private val http: HttpClient = HttpClient.newHttpClient()

    private fun parseFrames(body: String): List<BaseEvent> =
        body.split("\n\n")
            .filter { it.isNotBlank() }
            .map { frame ->
                assertTrue(frame.startsWith("data: "), "frame must start with 'data: ': <$frame>")
                AgUiJson.decodeFromString<BaseEvent>(frame.removePrefix("data: "))
            }

    private fun post(path: String, body: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return http.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun echoStreamProducesCanonicalSseSequenceWithCustomEvent() {
        val input = RunAgentInput(threadId = "t1", runId = "r1")
        val response = post("/agents/my-agent/run", AgUiJson.encodeToString(input))

        assertEquals(200, response.statusCode())
        val contentType = response.headers().firstValue("Content-Type").orElse("")
        assertTrue(contentType.startsWith("text/event-stream"), "got Content-Type=$contentType")

        val body = response.body()
        assertTrue(body.endsWith("\n\n"), "SSE body must end with a blank line")

        val events = parseFrames(body)
        assertEquals(6, events.size, "expected the 6-event echo lifecycle (incl. CUSTOM)")
        assertTrue(events[0] is RunStartedEvent)

        val custom = events[1]
        assertTrue(custom is CustomEvent, "second event must be the CUSTOM AGENT_SELECTED")
        assertEquals("AGENT_SELECTED", (custom as CustomEvent).name)

        assertTrue(events[2] is TextMessageStartEvent)
        assertTrue(events[3] is TextMessageContentEvent)
        assertEquals("Hello world!", (events[3] as TextMessageContentEvent).delta)
        assertTrue(events[4] is TextMessageEndEvent)
        assertTrue(events[5] is RunFinishedEvent)
    }

    @Test
    fun malformedBodyReturns4xx() {
        val response = post("/agents/my-agent/run", "{ this is not valid json")
        assertTrue(response.statusCode() in 400..499, "expected 4xx, got ${response.statusCode()}")
    }
}
