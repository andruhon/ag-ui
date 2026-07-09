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
     * The `.data(...)` payload for a framework SSE emitter that writes the `data:`
     * prefix **without** the conventional single post-colon space: `" " + json`.
     *
     * Spring MVC's `SseEmitter.event().data(x)` emits the line as `data:x` (no
     * space after the colon), so the payload must carry the leading space itself
     * to produce the on-the-wire line `data: {json}` that `@ag-ui/client` parses.
     * This helper supplies exactly that payload (used by BE-1's ADK Spring demo,
     * which drives a Spring `SseEmitter`).
     *
     * This is NOT the canonical wire form — the canonical SSE framing is produced
     * by [encode], which is what the live Ktor path (`respondBytesWriter`) writes.
     * Use [encodeData] only when a space-omitting emitter frames the line for you.
     */
    fun encodeData(event: BaseEvent): String =
        " " + AgUiJson.encodeToString(event)
}
