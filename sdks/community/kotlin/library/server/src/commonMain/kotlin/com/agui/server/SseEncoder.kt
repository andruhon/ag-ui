package com.agui.server

import com.agui.core.types.AgUiJson
import com.agui.core.types.BaseEvent
import kotlinx.serialization.encodeToString

/**
 * Serializes AG-UI [BaseEvent]s to the exact byte forms the AG-UI wire protocol
 * expects. All serialization goes through the shared [AgUiJson] instance so the
 * `"type"` discriminator and field encoding match the client SDK.
 */
object SseEncoder {

    /** The SSE content type served by an AG-UI endpoint. */
    const val CONTENT_TYPE: String = "text/event-stream"

    /**
     * Canonical SSE framing for one event: `"data: " + json + "\n\n"`.
     *
     * This is what a raw byte/writer transport (e.g. the Ktor binding using
     * `respondBytesWriter`) writes per event. The blank line terminates the SSE
     * message so a standards-compliant client dispatches it.
     */
    fun encode(event: BaseEvent): String =
        "data: " + AgUiJson.encodeToString(event) + "\n\n"

    /**
     * The payload a Spring `SseEmitter.event().data(...)` should be given:
     * `" " + json`, reproducing the Java server's leading-space quirk
     * byte-for-byte (see `AgUiService`).
     *
     * The Spring `SseEmitter` adds the `data:` prefix and the trailing blank line
     * itself; passing the space-prefixed JSON here yields a `data:  {json}`
     * line on the wire that `@ag-ui/client` parses as `" {json}"` and trims. Use
     * [encode] instead for a raw writer transport.
     */
    fun encodeData(event: BaseEvent): String =
        " " + AgUiJson.encodeToString(event)
}
