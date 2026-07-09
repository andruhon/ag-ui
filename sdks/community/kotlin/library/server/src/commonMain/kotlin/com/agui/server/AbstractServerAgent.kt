package com.agui.server

import com.agui.core.types.BaseEvent
import com.agui.core.types.RunAgentInput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

/**
 * Base class for agents that produce events imperatively, mirroring the Java
 * `LocalAgent` (`protected abstract void run(RunAgentInput, AgentSubscriber)`).
 *
 * Subclasses implement the imperative [run] overload, pushing events through an
 * [EventEmitter] and finishing with [EventEmitter.complete] (or failing with
 * [EventEmitter.error]). The public [AgUiAgent.run] implementation wraps that in
 * a [channelFlow] so the imperative emitter feeds a cold [Flow]. This is the
 * class a Java agent (e.g. the ADK integration, BE-1) is expected to extend.
 *
 * The imperative [run] may emit synchronously before returning, or hand the
 * [EventEmitter] to another thread and return early: the flow stays open until
 * [EventEmitter.complete] or [EventEmitter.error] is called.
 */
abstract class AbstractServerAgent : AgUiAgent {

    /**
     * Imperative run hook. Emit events via [emitter], then call
     * [EventEmitter.complete] (or [EventEmitter.error]) exactly once to end the
     * stream. Overridable from Java.
     *
     * @param input the run request
     * @param emitter the sink to push events and the terminal signal into
     */
    protected abstract fun run(input: RunAgentInput, emitter: EventEmitter)

    final override fun run(input: RunAgentInput): Flow<BaseEvent> = channelFlow {
        // Completed by the imperative run via the emitter's complete()/error().
        val terminal = CompletableDeferred<Unit>()

        val emitter = object : EventEmitter {
            override fun emit(event: BaseEvent) {
                // Bridges a non-suspending caller (possibly a foreign thread)
                // into the channel; honours backpressure via the buffer.
                trySendBlocking(event).getOrThrow()
            }

            override fun complete() {
                terminal.complete(Unit)
            }

            override fun error(t: Throwable) {
                terminal.completeExceptionally(t)
            }
        }

        // A synchronous throw from run() propagates out and closes the flow with
        // that failure, matching complete()/error() semantics.
        run(input, emitter)

        // Suspend until the emitter signals completion/failure. If run() already
        // completed synchronously this returns immediately; error() re-throws
        // here, closing the channelFlow with the failure.
        terminal.await()
    }
}
