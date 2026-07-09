package com.agui.server.ktor

import com.agui.core.types.AgUiJson
import com.agui.core.types.RunAgentInput
import com.agui.server.AgUiAgent
import com.agui.server.SseEncoder
import com.agui.server.verify.verifyEvents
import io.ktor.http.ContentType
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.flow.collect

/**
 * Registers an AG-UI agent endpoint on this [Route].
 *
 * The endpoint accepts a POST whose body is a JSON [RunAgentInput], runs [agent],
 * verifies the event ordering, and streams each event to the client as canonical
 * Server-Sent Events (`data: <json>\n\n`).
 *
 * We use [respondBytesWriter] rather than the `ktor-server-sse` `sse { }` DSL
 * because that DSL is GET-oriented, whereas AG-UI drives the run from a POST body.
 *
 * @param path the route pattern to bind (defaults to `/agents/{agentId}/run`).
 * @param agent the agent whose event flow is streamed to the client.
 */
fun Route.aguiEndpoint(path: String = "/agents/{agentId}/run", agent: AgUiAgent) {
    post(path) {
        val input = AgUiJson.decodeFromString<RunAgentInput>(call.receiveText())
        call.respondBytesWriter(contentType = ContentType.parse(SseEncoder.CONTENT_TYPE)) {
            agent.run(input).verifyEvents().collect { event ->
                writeStringUtf8(SseEncoder.encode(event))
                flush()
            }
        }
    }
}
