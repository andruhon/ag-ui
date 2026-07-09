package com.agui.server

import com.agui.core.types.BaseEvent
import com.agui.core.types.RunAgentInput
import kotlinx.coroutines.flow.Flow

/**
 * The primary Kotlin server-side agent abstraction.
 *
 * An [AgUiAgent] turns a [RunAgentInput] request into a cold [Flow] of AG-UI
 * [BaseEvent]s. It is declared as a `fun interface` so that a plain lambda
 * satisfies it:
 *
 * ```kotlin
 * val agent = AgUiAgent { input ->
 *     flowOf(EventFactory.runStarted(input.threadId, input.runId), ...)
 * }
 * ```
 *
 * This mirrors the client-side `AbstractAgent.run(input): Flow<BaseEvent>`
 * contract, so a transport binding (e.g. the Ktor [com.agui.server.ktor]
 * module) can collect the events and frame them as Server-Sent Events.
 */
fun interface AgUiAgent {
    /**
     * Runs the agent for the given [input] and returns the resulting event stream.
     *
     * The returned [Flow] is expected to be cold: collection is what starts the run.
     */
    fun run(input: RunAgentInput): Flow<BaseEvent>
}
