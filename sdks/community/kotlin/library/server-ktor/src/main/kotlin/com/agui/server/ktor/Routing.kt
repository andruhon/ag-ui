package com.agui.server.ktor

import com.agui.core.types.AgUiJson
import com.agui.core.types.EventType
import com.agui.core.types.RunAgentInput
import com.agui.encoder.EventEncoder
import com.agui.server.AgUiAgent
import com.agui.server.EventFactory
import com.agui.server.verify.verifyEvents
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

/**
 * Registers an AG-UI agent endpoint on this [Route].
 *
 * The endpoint accepts a POST whose body is a JSON [RunAgentInput], runs [agent],
 * verifies the event ordering, and streams each event to the client. Content-type
 * negotiation and framing are delegated to a per-request [EventEncoder] built from
 * the client's `Accept` header (mirroring the reference servers: Python
 * `EventEncoder(accept=...)`, gaunt-sloth `new EventEncoder({ accept })`). Today
 * that always yields canonical SSE (`data: <json>\n\n`); protobuf is a future TODO
 * in [EventEncoder].
 *
 * We use [respondBytesWriter] rather than the `ktor-server-sse` `sse { }` DSL
 * because that DSL is GET-oriented, whereas AG-UI drives the run from a POST body.
 *
 * A malformed request body fails the `AgUiJson.decodeFromString` **before** the
 * response writer opens, so it surfaces as a normal HTTP error. Once the writer
 * opens the 200 headers are committed, so a mid-stream failure from `agent.run()`
 * or [verifyEvents] can no longer change the status; instead we write one terminal
 * `RUN_ERROR` frame so the client receives a well-formed, parseable stream rather
 * than a truncated socket. The synthetic frame is written **only if no terminal
 * event (RUN_FINISHED / RUN_ERROR) already reached the wire** — otherwise a run
 * that forwarded its own terminal and then threw would produce a double terminal
 * that a `verifyEvents`-running client rejects. A [CancellationException] (client
 * disconnect) is re-thrown untouched, with no frame written.
 *
 * @param path the route pattern to bind (defaults to `/agents/{agentId}/run`).
 * @param agent the agent whose event flow is streamed to the client.
 */
fun Route.aguiEndpoint(path: String = "/agents/{agentId}/run", agent: AgUiAgent) {
    post(path) {
        // Decode BEFORE opening the writer: a malformed body becomes a proper HTTP error.
        val input = AgUiJson.decodeFromString<RunAgentInput>(call.receiveText())
        val encoder = EventEncoder(call.request.headers[HttpHeaders.Accept])
        call.respondBytesWriter(contentType = ContentType.parse(encoder.getContentType())) {
            var terminalSeen = false
            try {
                agent.run(input).verifyEvents().collect { event ->
                    writeStringUtf8(encoder.encode(event))
                    flush()
                    if (event.eventType == EventType.RUN_FINISHED || event.eventType == EventType.RUN_ERROR) {
                        terminalSeen = true
                    }
                }
            } catch (ce: CancellationException) {
                // Client disconnected / call cancelled: nothing useful to send; propagate.
                throw ce
            } catch (t: Throwable) {
                // Headers are already committed — emit a terminal RUN_ERROR frame so the
                // stream stays well-formed instead of truncating mid-flight, but only if
                // the agent has not already forwarded a terminal event (avoid a double
                // terminal that a verifyEvents-running client would reject).
                if (!terminalSeen) {
                    writeStringUtf8(encoder.encode(EventFactory.runError(t.message ?: t.toString())))
                    flush()
                }
            }
        }
    }
}
