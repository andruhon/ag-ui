package com.agui.server

import com.agui.core.types.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

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
        assertNull(e.code)
    }

    @Test
    fun runErrorWithCode() {
        val e = EventFactory.runError("boom", "E_BOOM")
        assertEquals("boom", e.message)
        assertEquals("E_BOOM", e.code)
    }

    @Test
    fun runFinishedWithResultAndOutcome() {
        val result = JsonPrimitive("ok")
        val e = EventFactory.runFinished("t", "r", result, RunFinishedSuccessOutcome)
        assertEquals(EventType.RUN_FINISHED, e.eventType)
        assertEquals("t", e.threadId)
        assertEquals("r", e.runId)
        assertEquals(result, e.result)
        assertSame(RunFinishedSuccessOutcome, e.outcome)
    }

    @Test
    fun runFinishedTwoArgHasNoResultOrOutcome() {
        val e = EventFactory.runFinished("t", "r")
        assertNull(e.result)
        assertNull(e.outcome)
    }

    @Test
    fun stepStartedAndFinished() {
        val started = EventFactory.stepStarted("plan")
        assertEquals(EventType.STEP_STARTED, started.eventType)
        assertEquals("plan", started.stepName)

        val finished = EventFactory.stepFinished("plan")
        assertEquals(EventType.STEP_FINISHED, finished.eventType)
        assertEquals("plan", finished.stepName)
    }

    @Test
    fun stateSnapshot() {
        val snapshot: State = JsonPrimitive("s")
        val e = EventFactory.stateSnapshot(snapshot)
        assertEquals(EventType.STATE_SNAPSHOT, e.eventType)
        assertEquals(snapshot, e.snapshot)
    }

    @Test
    fun stateDelta() {
        val delta: JsonArray = buildJsonArray { add(JsonPrimitive("patch")) }
        val e = EventFactory.stateDelta(delta)
        assertEquals(EventType.STATE_DELTA, e.eventType)
        assertEquals(delta, e.delta)
    }

    @Test
    fun messagesSnapshot() {
        val messages = listOf(UserMessage(id = "u1", content = "hi"))
        val e = EventFactory.messagesSnapshot(messages)
        assertEquals(EventType.MESSAGES_SNAPSHOT, e.eventType)
        assertEquals(1, e.messages.size)
        assertTrue(e.messages.first() is UserMessage)
    }
}
