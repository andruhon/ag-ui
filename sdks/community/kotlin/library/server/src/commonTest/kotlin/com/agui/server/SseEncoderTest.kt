package com.agui.server

import com.agui.core.types.RunStartedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SseEncoderTest {

    private val event = RunStartedEvent(threadId = "t1", runId = "r1")
    // Compact JSON with the "type" discriminator first, then declaration order;
    // null timestamp/rawEvent omitted (AgUiJson has explicitNulls = false).
    private val expectedJson = """{"type":"RUN_STARTED","threadId":"t1","runId":"r1"}"""

    @Test
    fun contentTypeIsEventStream() {
        assertEquals("text/event-stream", SseEncoder.CONTENT_TYPE)
    }

    @Test
    fun encodeProducesCanonicalSseFraming() {
        // Exact bytes: "data: " prefix (one space), compact JSON, trailing blank line.
        assertEquals("data: $expectedJson\n\n", SseEncoder.encode(event))
    }

    @Test
    fun encodeStartsWithDataPrefixAndEndsWithBlankLine() {
        val encoded = SseEncoder.encode(event)
        assertTrue(encoded.startsWith("data: "), "must start with 'data: '")
        assertTrue(encoded.endsWith("\n\n"), "must end with a blank line")
    }

    @Test
    fun encodeDataReproducesLeadingSpacePrefix() {
        // Spring wire quirk: exactly one leading space before the JSON, no framing.
        assertEquals(" $expectedJson", SseEncoder.encodeData(event))
        assertEquals(' ', SseEncoder.encodeData(event)[0])
    }
}
