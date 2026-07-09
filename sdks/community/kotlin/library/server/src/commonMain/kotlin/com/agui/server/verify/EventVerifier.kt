@file:Suppress("DEPRECATION")

package com.agui.server.verify

import com.agui.core.types.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform

// TODO(BE-3 PR): relocate EventVerifier + SseParser into :kotlin-core and share with :kotlin-client
// (and this :kotlin-server copy). For this task the verifier state machine is copied verbatim from
// client/src/commonMain/kotlin/com/agui/client/verify/EventVerifier.kt to keep the :kotlin-client
// build green; the kermit debug logging was dropped as it is not needed server-side.

/**
 * Custom error class for AG-UI protocol violations.
 * Thrown when events don't follow the proper AG-UI protocol state machine rules.
 *
 * @param message Descriptive error message explaining the protocol violation
 */
class AGUIError(message: String) : Exception(message)

/**
 * Verifies that a server-produced event stream follows the AG-UI protocol rules.
 * Implements the same state machine as the client-side verifier: enforces
 * RUN_STARTED first, matched TEXT_MESSAGE_START -> CONTENT* -> END and
 * TOOL_CALL_START -> ARGS* -> END lifecycles, and terminal RUN_FINISHED /
 * RUN_ERROR handling (supporting sequential runs).
 *
 * @return the same event flow, after validation
 * @throws AGUIError when events violate the AG-UI protocol state machine
 */
fun Flow<BaseEvent>.verifyEvents(): Flow<BaseEvent> {
    // State tracking - using Maps to support concurrent messages/tool calls like TypeScript SDK
    val activeMessages = mutableMapOf<String, Boolean>()
    val activeToolCalls = mutableMapOf<String, Boolean>()
    var runFinished = false
    var runError = false
    var firstEventReceived = false
    val activeSteps = mutableMapOf<String, Boolean>()
    var activeThinkingStep = false
    var activeThinkingStepMessage = false
    var runStarted = false

    return transform { event ->
        val eventType = event.eventType

        // Check if run has errored
        if (runError) {
            throw AGUIError(
                "Cannot send event type '$eventType': The run has already errored with 'RUN_ERROR'. No further events can be sent."
            )
        }

        // Check if run has already finished (but allow RUN_STARTED for new run)
        if (runFinished && eventType != EventType.RUN_ERROR && eventType != EventType.RUN_STARTED) {
            throw AGUIError(
                "Cannot send event type '$eventType': The run has already finished with 'RUN_FINISHED'. Start a new run with 'RUN_STARTED'."
            )
        }

        // First event validation and RUN_STARTED handling (matching TypeScript SDK)
        if (!firstEventReceived) {
            firstEventReceived = true
            if (eventType != EventType.RUN_STARTED && eventType != EventType.RUN_ERROR) {
                throw AGUIError("First event must be 'RUN_STARTED'")
            }
        } else if (eventType == EventType.RUN_STARTED) {
            // Allow RUN_STARTED after RUN_FINISHED (new run), but not during an active run
            if (runStarted && !runFinished) {
                throw AGUIError(
                    "Cannot send 'RUN_STARTED' while a run is still active. The previous run must be finished with 'RUN_FINISHED' before starting a new run."
                )
            }
            // Reset state for new run
            if (runFinished) {
                activeMessages.clear()
                activeToolCalls.clear()
                activeSteps.clear()
                activeThinkingStep = false
                activeThinkingStepMessage = false
                runFinished = false
                runError = false
            }
            runStarted = true
        }

        // Event-specific validation (matching TypeScript SDK - supports concurrent messages/tool calls)
        when (event) {
            is TextMessageStartEvent -> {
                val messageId = event.messageId
                if (activeMessages.containsKey(messageId)) {
                    throw AGUIError(
                        "Cannot send 'TEXT_MESSAGE_START' event: A text message with ID '$messageId' is already in progress. Complete it with 'TEXT_MESSAGE_END' first."
                    )
                }
                activeMessages[messageId] = true
            }

            is TextMessageContentEvent -> {
                val messageId = event.messageId
                if (!activeMessages.containsKey(messageId)) {
                    throw AGUIError(
                        "Cannot send 'TEXT_MESSAGE_CONTENT' event: No active text message found with ID '$messageId'. Start a text message with 'TEXT_MESSAGE_START' first."
                    )
                }
            }

            is TextMessageEndEvent -> {
                val messageId = event.messageId
                if (!activeMessages.containsKey(messageId)) {
                    throw AGUIError(
                        "Cannot send 'TEXT_MESSAGE_END' event: No active text message found with ID '$messageId'. A 'TEXT_MESSAGE_START' event must be sent first."
                    )
                }
                activeMessages.remove(messageId)
            }

            is ToolCallStartEvent -> {
                val toolCallId = event.toolCallId
                if (activeToolCalls.containsKey(toolCallId)) {
                    throw AGUIError(
                        "Cannot send 'TOOL_CALL_START' event: A tool call with ID '$toolCallId' is already in progress. Complete it with 'TOOL_CALL_END' first."
                    )
                }
                activeToolCalls[toolCallId] = true
            }

            is ToolCallArgsEvent -> {
                val toolCallId = event.toolCallId
                if (!activeToolCalls.containsKey(toolCallId)) {
                    throw AGUIError(
                        "Cannot send 'TOOL_CALL_ARGS' event: No active tool call found with ID '$toolCallId'. Start a tool call with 'TOOL_CALL_START' first."
                    )
                }
            }

            is ToolCallEndEvent -> {
                val toolCallId = event.toolCallId
                if (!activeToolCalls.containsKey(toolCallId)) {
                    throw AGUIError(
                        "Cannot send 'TOOL_CALL_END' event: No active tool call found with ID '$toolCallId'. A 'TOOL_CALL_START' event must be sent first."
                    )
                }
                activeToolCalls.remove(toolCallId)
            }

            is StepStartedEvent -> {
                val stepName = event.stepName
                if (activeSteps.containsKey(stepName)) {
                    throw AGUIError("Step \"$stepName\" is already active for 'STEP_STARTED'")
                }
                activeSteps[stepName] = true
            }

            is StepFinishedEvent -> {
                val stepName = event.stepName
                if (!activeSteps.containsKey(stepName)) {
                    throw AGUIError(
                        "Cannot send 'STEP_FINISHED' for step \"$stepName\" that was not started"
                    )
                }
                activeSteps.remove(stepName)
            }

            is RunFinishedEvent -> {
                // Check that all steps are finished before run ends
                if (activeSteps.isNotEmpty()) {
                    val unfinishedSteps = activeSteps.keys.joinToString(", ")
                    throw AGUIError(
                        "Cannot send 'RUN_FINISHED' while steps are still active: $unfinishedSteps"
                    )
                }
                // Check that all messages are finished before run ends
                if (activeMessages.isNotEmpty()) {
                    val unfinishedMessages = activeMessages.keys.joinToString(", ")
                    throw AGUIError(
                        "Cannot send 'RUN_FINISHED' while text messages are still active: $unfinishedMessages"
                    )
                }
                // Check that all tool calls are finished before run ends
                if (activeToolCalls.isNotEmpty()) {
                    val unfinishedToolCalls = activeToolCalls.keys.joinToString(", ")
                    throw AGUIError(
                        "Cannot send 'RUN_FINISHED' while tool calls are still active: $unfinishedToolCalls"
                    )
                }
                runFinished = true
            }

            is RunStartedEvent -> {
                runStarted = true
            }

            is RunErrorEvent -> {
                runError = true
            }

            // Thinking Events Validation
            is ThinkingStartEvent -> {
                if (activeThinkingStep) {
                    throw AGUIError(
                        "Cannot send 'THINKING_START' event: A thinking step is already in progress. Complete it with 'THINKING_END' first."
                    )
                }
                activeThinkingStep = true
            }

            is ThinkingEndEvent -> {
                if (!activeThinkingStep) {
                    throw AGUIError(
                        "Cannot send 'THINKING_END' event: No active thinking step found. A 'THINKING_START' event must be sent first."
                    )
                }
                activeThinkingStep = false
            }

            is ThinkingTextMessageStartEvent -> {
                if (!activeThinkingStep) {
                    throw AGUIError(
                        "Cannot send 'THINKING_TEXT_MESSAGE_START' event: No active thinking step found. A 'THINKING_START' event must be sent first."
                    )
                }
                if (activeThinkingStepMessage) {
                    throw AGUIError(
                        "Cannot send 'THINKING_TEXT_MESSAGE_START' event: A thinking text message is already in progress. Complete it with 'THINKING_TEXT_MESSAGE_END' first."
                    )
                }
                activeThinkingStepMessage = true
            }

            is ThinkingTextMessageContentEvent -> {
                if (!activeThinkingStepMessage) {
                    throw AGUIError(
                        "Cannot send 'THINKING_TEXT_MESSAGE_CONTENT' event: No active thinking text message found. Start a thinking text message with 'THINKING_TEXT_MESSAGE_START' first."
                    )
                }
            }

            is ThinkingTextMessageEndEvent -> {
                if (!activeThinkingStepMessage) {
                    throw AGUIError(
                        "Cannot send 'THINKING_TEXT_MESSAGE_END' event: No active thinking text message found. A 'THINKING_TEXT_MESSAGE_START' event must be sent first."
                    )
                }
                activeThinkingStepMessage = false
            }

            else -> {
                // Other events are allowed
            }
        }

        emit(event)
    }
}
