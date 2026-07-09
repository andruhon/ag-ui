package com.agui.server

import com.agui.core.types.*
import com.agui.server.verify.AGUIError
import com.agui.server.verify.verifyEvents
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VerifyEventsTest {

    @Test
    fun acceptsAValidRun() = runTest {
        val events = flowOf(
            EventFactory.runStarted("t", "r"),
            EventFactory.textMessageStart("m1"),
            EventFactory.textMessageContent("m1", "hello"),
            EventFactory.textMessageEnd("m1"),
            EventFactory.runFinished("t", "r")
        ).verifyEvents().toList()

        assertEquals(5, events.size)
        assertEquals(EventType.RUN_STARTED, events.first().eventType)
        assertEquals(EventType.RUN_FINISHED, events.last().eventType)
    }

    @Test
    fun rejectsContentBeforeStart() = runTest {
        assertFailsWith<AGUIError> {
            flowOf(
                EventFactory.runStarted("t", "r"),
                EventFactory.textMessageContent("m1", "hello")
            ).verifyEvents().toList()
        }
    }

    @Test
    fun rejectsMissingRunStartedAsFirstEvent() = runTest {
        assertFailsWith<AGUIError> {
            flowOf(
                EventFactory.textMessageStart("m1")
            ).verifyEvents().toList()
        }
    }

    @Test
    fun rejectsSecondRunStartedWhileRunActive() = runTest {
        assertFailsWith<AGUIError> {
            flowOf(
                EventFactory.runStarted("t", "r"),
                EventFactory.runStarted("t", "r2")
            ).verifyEvents().toList()
        }
    }
}
