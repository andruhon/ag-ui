package com.agui.example.server.spring

import com.agui.core.types.AgUiJson
import com.agui.core.types.RunAgentInput
import com.agui.encoder.EventEncoder
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody

/**
 * AG-UI endpoint. The servlet-MVC transliteration of the Python example server:
 * decode a [RunAgentInput], build a per-request [EventEncoder] from the client's
 * `Accept` header, then stream `encoder.encode(event)` for each event the [Agent]
 * emits.
 *
 * Framing note — we return **[StreamingResponseBody]**, NOT Spring's `SseEmitter`
 * / `ServerSentEvent`. `EventEncoder.encode()` already emits canonical SSE
 * (`data: {json}\n\n`); `SseEmitter` would add its OWN `data:` framing on top and
 * double-frame the stream. `StreamingResponseBody` + `encoder.encode()` is the
 * raw-streaming pairing that mirrors FastAPI's `StreamingResponse`, and keeps the
 * wire identical across the Python, Ktor and Spring starters. (If you ever DID use
 * `SseEmitter`, you would hand it only `AgUiJson.encodeToString(event)` and let
 * Spring frame — but we are deliberately not doing that.)
 *
 * We decode with `AgUiJson` (kotlinx-serialization), NOT Spring's Jackson: the
 * kotlin-core types are kotlinx-serialization `@Serializable`, so `@RequestBody`
 * is taken as a raw `String` and decoded here. Decoding happens BEFORE the stream
 * opens, so a malformed body is a clean 4xx (see [handleBadBody]).
 */
@RestController
class AgUiController(
    // The agent seam — injected as a bean (see Application.agent()); swap the bean
    // for a real (e.g. Koog-backed) Agent to replace the echo.
    private val agent: Agent,
) {

    @PostMapping("/agents/{agentId}/run", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun run(
        @PathVariable agentId: String,
        @RequestBody body: String,
        @RequestHeader(value = HttpHeaders.ACCEPT, required = false) accept: String?,
    ): ResponseEntity<StreamingResponseBody> {
        val input = AgUiJson.decodeFromString<RunAgentInput>(body)
        val encoder = EventEncoder(accept)

        val stream = StreamingResponseBody { out ->
            for (event in agent.run(input)) {
                out.write(encoder.encode(event).toByteArray(Charsets.UTF_8))
                out.flush()
            }
        }

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(encoder.getContentType()))
            .body(stream)
    }

    /** A body that is not a valid [RunAgentInput] is a client error, not a 500. */
    @ExceptionHandler(SerializationException::class, IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleBadBody(e: Exception): String = "Invalid RunAgentInput: ${e.message}"
}
