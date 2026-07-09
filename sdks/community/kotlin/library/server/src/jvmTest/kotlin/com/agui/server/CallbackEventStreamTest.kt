package com.agui.server

import com.agui.core.types.BaseEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CallbackEventStreamTest {

    @Test
    fun forwardsEventsThenCompletesOnce() {
        val received = mutableListOf<BaseEvent>()
        var completes = 0
        val stream = CallbackEventStream(
            onNext = { received.add(it) },
            onError = { },
            onComplete = { completes++ }
        )

        stream.emit(EventFactory.runStarted("t", "r"))
        stream.emit(EventFactory.runFinished("t", "r"))
        stream.complete()
        stream.complete() // idempotent

        assertEquals(2, received.size)
        assertEquals(1, completes)
        assertTrue(stream.isCompleted())
    }

    @Test
    fun ignoresEmitAfterComplete() {
        val received = mutableListOf<BaseEvent>()
        val stream = CallbackEventStream(onNext = { received.add(it) }, onError = { }, onComplete = { })

        stream.complete()
        stream.emit(EventFactory.runStarted("t", "r"))

        assertTrue(received.isEmpty())
    }

    @Test
    fun errorInvokesOnErrorAndMarksCompleted() {
        var seen: Throwable? = null
        val stream = CallbackEventStream(onNext = { }, onError = { seen = it }, onComplete = { })

        stream.error(RuntimeException("boom"))

        assertEquals("boom", seen?.message)
        assertTrue(stream.isCompleted())
    }

    @Test
    fun cancelSilencesCallbacks() {
        var completed = false
        val stream = CallbackEventStream(onNext = { }, onError = { }, onComplete = { completed = true })

        stream.cancel()
        stream.complete()

        assertTrue(stream.isCancelled())
        assertFalse(completed)
    }
}
