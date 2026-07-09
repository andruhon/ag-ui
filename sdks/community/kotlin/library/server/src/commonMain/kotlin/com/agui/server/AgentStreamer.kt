package com.agui.server

import com.agui.core.types.RunAgentInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Bridges an [AgUiAgent]'s [kotlinx.coroutines.flow.Flow] output into an
 * imperative [EventEmitter], mirroring the Java `AgentStreamer` that adapts an
 * agent to an `EventStream<BaseEvent>`.
 *
 * A Spring (or otherwise imperative) application can create a
 * [CallbackEventStream] over its own `SseEmitter` and hand it here: each event
 * is forwarded via [EventEmitter.emit], [EventEmitter.complete] fires when the
 * flow ends normally, and [EventEmitter.error] fires on failure. Java-callable.
 */
object AgentStreamer {

    /**
     * Collects `agent.run(input)` into [stream] on the given [scope].
     *
     * @return the [Job] running the collection; cancel it to stop streaming.
     */
    fun streamToCallback(
        agent: AgUiAgent,
        input: RunAgentInput,
        stream: EventEmitter,
        scope: CoroutineScope
    ): Job = scope.launch {
        try {
            agent.run(input).collect { event ->
                stream.emit(event)
            }
            stream.complete()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            stream.error(t)
        }
    }
}
