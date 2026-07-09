package com.agui.server

import com.agui.core.types.BaseEvent
import com.agui.core.types.RunAgentInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentStreamerTest {

    /** Records termination signals so we can assert "exactly once" on each path. */
    private class CountingEmitter : EventEmitter {
        val events = mutableListOf<BaseEvent>()
        var completes = 0
        var errors = 0
        var lastError: Throwable? = null
        override fun emit(event: BaseEvent) { events.add(event) }
        override fun complete() { completes++ }
        override fun error(t: Throwable) { errors++; lastError = t }
    }

    private val input = RunAgentInput(threadId = "t", runId = "r")

    @Test
    fun normalEndCompletesExactlyOnce() = runTest {
        val emitter = CountingEmitter()
        val agent = AgUiAgent { i ->
            flowOf(
                EventFactory.runStarted(i.threadId, i.runId),
                EventFactory.runFinished(i.threadId, i.runId)
            )
        }

        AgentStreamer.streamToCallback(agent, input, emitter, this).join()

        assertEquals(2, emitter.events.size)
        assertEquals(1, emitter.completes)
        assertEquals(0, emitter.errors)
    }

    @Test
    fun agentFailureErrorsExactlyOnce() = runTest {
        val emitter = CountingEmitter()
        val agent = AgUiAgent { i ->
            flow {
                emit(EventFactory.runStarted(i.threadId, i.runId))
                throw IllegalStateException("boom")
            }
        }

        AgentStreamer.streamToCallback(agent, input, emitter, this).join()

        assertEquals(0, emitter.completes)
        assertEquals(1, emitter.errors)
        assertEquals("boom", emitter.lastError?.message)
    }

    @Test
    fun cancellationTerminatesExactlyOnceThenPropagates() = runTest {
        val emitter = CountingEmitter()
        val agent = AgUiAgent { i ->
            flow {
                emit(EventFactory.runStarted(i.threadId, i.runId))
                awaitCancellation() // hang until cancelled
            }
        }

        val job = AgentStreamer.streamToCallback(agent, input, emitter, this)
        runCurrent() // let it start, emit RUN_STARTED, and suspend
        job.cancelAndJoin()

        assertEquals(1, emitter.events.size) // RUN_STARTED made it through
        assertEquals(0, emitter.completes)
        assertEquals(1, emitter.errors) // terminated exactly once on cancel
        assertTrue(emitter.lastError is CancellationException)
        assertTrue(job.isCancelled)
    }
}
