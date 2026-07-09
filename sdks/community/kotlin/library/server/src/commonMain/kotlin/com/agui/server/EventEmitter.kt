package com.agui.server

import com.agui.core.types.BaseEvent

/**
 * Imperative, push-style sink for AG-UI events.
 *
 * This is the Kotlin analog of the Java `IEventStream<BaseEvent>` callback model:
 * an agent (or a bridge such as [AgentStreamer]) pushes events via [emit] and
 * signals termination with either [complete] or [error]. Implementations are
 * expected to be safe to call from any thread and to ignore any calls made after
 * completion.
 *
 * @see CallbackEventStream a thread-safe, emit-once implementation for driving a
 *   Spring `SseEmitter` (or any other imperative transport).
 * @see AbstractServerAgent which adapts an imperative [EventEmitter]-based `run`
 *   into an [AgUiAgent] flow.
 */
interface EventEmitter {
    /** Pushes a single [event] downstream. No-op once completed or errored. */
    fun emit(event: BaseEvent)

    /** Signals successful, terminal completion of the stream. Idempotent. */
    fun complete()

    /** Signals terminal failure of the stream with [t]. No-op once completed. */
    fun error(t: Throwable)
}
