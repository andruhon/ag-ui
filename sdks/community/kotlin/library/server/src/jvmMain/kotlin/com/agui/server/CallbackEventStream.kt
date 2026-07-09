package com.agui.server

import com.agui.core.types.BaseEvent
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thread-safe, emit-once [EventEmitter] backed by three callbacks. Direct analog
 * of the Java `EventStream<BaseEvent>`, letting an imperative application (e.g. a
 * Spring controller driving an `SseEmitter`) consume agent events without
 * touching coroutines.
 *
 * Semantics (matching the Java implementation):
 * - [emit] is a no-op once the stream is cancelled or completed; an exception
 *   thrown by [onNext] is routed to [error].
 * - [error] marks the stream completed and invokes [onError] at most once.
 * - [complete] invokes [onComplete] exactly once.
 * - [cancel] permanently silences all further callbacks.
 *
 * @param onNext invoked per emitted event
 * @param onError invoked at most once on failure
 * @param onComplete invoked exactly once on normal completion
 */
class CallbackEventStream(
    private val onNext: (BaseEvent) -> Unit,
    private val onError: (Throwable) -> Unit,
    private val onComplete: () -> Unit
) : EventEmitter {

    private val cancelled = AtomicBoolean(false)
    private val completed = AtomicBoolean(false)
    private val lock = Any()

    override fun emit(event: BaseEvent) {
        synchronized(lock) {
            if (cancelled.get() || completed.get()) {
                return
            }
            try {
                onNext(event)
            } catch (e: Throwable) {
                error(e)
            }
        }
    }

    override fun error(t: Throwable) {
        synchronized(lock) {
            if (cancelled.get() || completed.getAndSet(true)) {
                return
            }
            try {
                onError(t)
            } catch (ignored: Exception) {
                // An error handler that itself throws must not loop.
            }
        }
    }

    override fun complete() {
        synchronized(lock) {
            if (cancelled.get() || completed.getAndSet(true)) {
                return
            }
            try {
                onComplete()
            } catch (ignored: Exception) {
                // Swallow: completion has already been recorded.
            }
        }
    }

    /** Permanently silences the stream; all later callbacks are ignored. */
    fun cancel() {
        synchronized(lock) {
            cancelled.set(true)
        }
    }

    /** Whether [cancel] has been called. */
    fun isCancelled(): Boolean = cancelled.get()

    /** Whether the stream has terminated via [complete] or [error]. */
    fun isCompleted(): Boolean = completed.get()
}
