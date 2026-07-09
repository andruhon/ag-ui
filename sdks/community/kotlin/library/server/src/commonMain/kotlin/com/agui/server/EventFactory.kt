package com.agui.server

import com.agui.core.types.*
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Static factory for the most commonly emitted AG-UI events, mirroring the Java
 * `EventFactory`. Each builder hides the optional `timestamp`/`rawEvent` tail so
 * a Java caller can construct an event in a single call.
 *
 * Methods are annotated [JvmStatic] so they are callable as
 * `EventFactory.runStarted(...)` from Java, and [JvmOverloads] is applied where a
 * parameter has a default so Java sees the shorter overloads too.
 */
object EventFactory {

    /** Creates a [RunStartedEvent] for the given thread/run. */
    @JvmStatic
    fun runStarted(threadId: String, runId: String): RunStartedEvent =
        RunStartedEvent(threadId = threadId, runId = runId)

    /**
     * Creates a [TextMessageStartEvent].
     *
     * @param role defaults to [Role.ASSISTANT]; Java sees both the 1-arg and
     *   2-arg forms via [JvmOverloads].
     */
    @JvmStatic
    @JvmOverloads
    fun textMessageStart(messageId: String, role: Role = Role.ASSISTANT): TextMessageStartEvent =
        TextMessageStartEvent(messageId = messageId, role = role)

    /**
     * Creates a [TextMessageContentEvent]. [delta] must be non-empty (enforced by
     * the event's own `init` block).
     */
    @JvmStatic
    fun textMessageContent(messageId: String, delta: String): TextMessageContentEvent =
        TextMessageContentEvent(messageId = messageId, delta = delta)

    /** Creates a [TextMessageEndEvent]. */
    @JvmStatic
    fun textMessageEnd(messageId: String): TextMessageEndEvent =
        TextMessageEndEvent(messageId = messageId)

    /**
     * Creates a [ToolCallStartEvent].
     *
     * @param parentMessageId optional id of the message that owns this tool call.
     */
    @JvmStatic
    @JvmOverloads
    fun toolCallStart(
        toolCallId: String,
        name: String,
        parentMessageId: String? = null
    ): ToolCallStartEvent =
        ToolCallStartEvent(
            toolCallId = toolCallId,
            toolCallName = name,
            parentMessageId = parentMessageId
        )

    /** Creates a [ToolCallArgsEvent] carrying an argument-string [delta]. */
    @JvmStatic
    fun toolCallArgs(toolCallId: String, delta: String): ToolCallArgsEvent =
        ToolCallArgsEvent(toolCallId = toolCallId, delta = delta)

    /** Creates a [ToolCallEndEvent]. */
    @JvmStatic
    fun toolCallEnd(toolCallId: String): ToolCallEndEvent =
        ToolCallEndEvent(toolCallId = toolCallId)

    /**
     * Creates a [ToolCallResultEvent]. Parameter order follows the Kotlin
     * [ToolCallResultEvent] constructor (messageId, toolCallId, content, role).
     */
    @JvmStatic
    @JvmOverloads
    fun toolCallResult(
        messageId: String,
        toolCallId: String,
        content: String,
        role: String? = null
    ): ToolCallResultEvent =
        ToolCallResultEvent(
            messageId = messageId,
            toolCallId = toolCallId,
            content = content,
            role = role
        )

    /** Creates a [RunFinishedEvent] for the given thread/run. */
    @JvmStatic
    fun runFinished(threadId: String, runId: String): RunFinishedEvent =
        RunFinishedEvent(threadId = threadId, runId = runId)

    /** Creates a [RunErrorEvent] with the given [message]. */
    @JvmStatic
    fun runError(message: String): RunErrorEvent =
        RunErrorEvent(message = message)
}
