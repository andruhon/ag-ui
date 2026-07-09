package com.agui.server

import com.agui.core.types.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EventFactoryTest {

    @Test
    fun runStartedCarriesIds() {
        val e = EventFactory.runStarted("thread-1", "run-1")
        assertEquals(EventType.RUN_STARTED, e.eventType)
        assertEquals("thread-1", e.threadId)
        assertEquals("run-1", e.runId)
    }

    @Test
    fun textMessageStartDefaultsToAssistantRole() {
        val e = EventFactory.textMessageStart("m1")
        assertEquals(EventType.TEXT_MESSAGE_START, e.eventType)
        assertEquals("m1", e.messageId)
        assertEquals(Role.ASSISTANT, e.role)
    }

    @Test
    fun textMessageStartHonoursExplicitRole() {
        val e = EventFactory.textMessageStart("m1", Role.USER)
        assertEquals(Role.USER, e.role)
    }

    @Test
    fun textMessageContentCarriesDelta() {
        val e = EventFactory.textMessageContent("m1", "hello")
        assertEquals(EventType.TEXT_MESSAGE_CONTENT, e.eventType)
        assertEquals("m1", e.messageId)
        assertEquals("hello", e.delta)
    }

    @Test
    fun textMessageEnd() {
        val e = EventFactory.textMessageEnd("m1")
        assertEquals(EventType.TEXT_MESSAGE_END, e.eventType)
        assertEquals("m1", e.messageId)
    }

    @Test
    fun toolCallStartWithoutParent() {
        val e = EventFactory.toolCallStart("tc1", "search")
        assertEquals(EventType.TOOL_CALL_START, e.eventType)
        assertEquals("tc1", e.toolCallId)
        assertEquals("search", e.toolCallName)
        assertNull(e.parentMessageId)
    }

    @Test
    fun toolCallStartWithParent() {
        val e = EventFactory.toolCallStart("tc1", "search", "m1")
        assertEquals("m1", e.parentMessageId)
    }

    @Test
    fun toolCallArgsAndEnd() {
        val args = EventFactory.toolCallArgs("tc1", "{\"q\":")
        assertEquals(EventType.TOOL_CALL_ARGS, args.eventType)
        assertEquals("tc1", args.toolCallId)
        assertEquals("{\"q\":", args.delta)

        val end = EventFactory.toolCallEnd("tc1")
        assertEquals(EventType.TOOL_CALL_END, end.eventType)
        assertEquals("tc1", end.toolCallId)
    }

    @Test
    fun toolCallResult() {
        val e = EventFactory.toolCallResult("m1", "tc1", "42", "tool")
        assertEquals(EventType.TOOL_CALL_RESULT, e.eventType)
        assertEquals("m1", e.messageId)
        assertEquals("tc1", e.toolCallId)
        assertEquals("42", e.content)
        assertEquals("tool", e.role)
    }

    @Test
    fun runFinished() {
        val e = EventFactory.runFinished("thread-1", "run-1")
        assertEquals(EventType.RUN_FINISHED, e.eventType)
        assertEquals("thread-1", e.threadId)
        assertEquals("run-1", e.runId)
    }

    @Test
    fun runError() {
        val e = EventFactory.runError("boom")
        assertEquals(EventType.RUN_ERROR, e.eventType)
        assertEquals("boom", e.message)
    }
}
