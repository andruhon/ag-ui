package com.agui.server

import com.agui.core.types.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AbstractServerAgentTest {

    private class EchoAgent : AbstractServerAgent() {
        override fun run(input: RunAgentInput, emitter: EventEmitter) {
            emitter.emit(EventFactory.runStarted(input.threadId, input.runId))
            emitter.emit(EventFactory.textMessageStart("m1"))
            emitter.emit(EventFactory.textMessageContent("m1", "hello"))
            emitter.emit(EventFactory.textMessageEnd("m1"))
            emitter.emit(EventFactory.runFinished(input.threadId, input.runId))
            emitter.complete()
        }
    }

    private class FailingAgent : AbstractServerAgent() {
        override fun run(input: RunAgentInput, emitter: EventEmitter) {
            emitter.emit(EventFactory.runStarted(input.threadId, input.runId))
            emitter.error(IllegalStateException("boom"))
        }
    }

    @Test
    fun imperativeEmitterFeedsTheFlow() = runTest {
        val events = EchoAgent().run(RunAgentInput(threadId = "t", runId = "r")).toList()
        assertEquals(5, events.size)
        assertEquals(EventType.RUN_STARTED, events.first().eventType)
        assertEquals("hello", (events[2] as TextMessageContentEvent).delta)
        assertEquals(EventType.RUN_FINISHED, events.last().eventType)
    }

    @Test
    fun errorOnEmitterFailsTheFlow() = runTest {
        assertFailsWith<IllegalStateException> {
            FailingAgent().run(RunAgentInput(threadId = "t", runId = "r")).toList()
        }
    }
}
