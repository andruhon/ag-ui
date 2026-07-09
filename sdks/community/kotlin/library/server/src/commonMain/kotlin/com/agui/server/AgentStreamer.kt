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
 *
 * The [stream] is terminated **exactly once** on every exit path — normal
 * completion, agent error, and cancellation — so a downstream consumer (e.g. a
 * Spring `SseEmitter`) always receives a terminal signal and never hangs until a
 * container timeout.
 */
object AgentStreamer {

    /**
     * Collects `agent.run(input)` into [stream] on the given [scope].
     *
     * Termination is guaranteed and single: normal end -> [EventEmitter.complete];
     * agent failure -> [EventEmitter.error]; cancellation (e.g. the [scope] or the
     * returned [Job] is cancelled) -> [EventEmitter.error] with the
     * [CancellationException], after which the exception is rethrown to preserve
     * structured concurrency.
     *
     * @return the [Job] running the collection; cancel it to stop streaming.
     */
    fun streamToCallback(
        agent: AgUiAgent,
        input: RunAgentInput,
        stream: EventEmitter,
        scope: CoroutineScope
    ): Job = scope.launch {
        var terminated = false
        try {
            agent.run(input).collect { event ->
                stream.emit(event)
            }
            terminated = true
            stream.complete()
        } catch (ce: CancellationException) {
            // Terminate the consumer before propagating so it can't hang, then
            // rethrow to preserve structured concurrency.
            if (!terminated) {
                terminated = true
                stream.error(ce)
            }
            throw ce
        } catch (t: Throwable) {
            if (!terminated) {
                terminated = true
                stream.error(t)
            }
        }
    }
}
