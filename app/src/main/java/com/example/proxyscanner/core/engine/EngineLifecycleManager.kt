package com.example.proxyscanner.core.engine

import com.example.proxyscanner.core.model.FailureCategory
import com.example.proxyscanner.core.model.ProxyConfig
import com.example.proxyscanner.core.security.Redactor
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Owns the lifecycle of one [ProxyEngine]: initialize, start sessions, stop,
 * and report errors. It adds state tracking and error reporting around the
 * engine; it never fabricates a connection. A session is only ever handed out
 * if the engine itself returned [EngineOpenResult.Opened].
 *
 * Threading: [initialize] and [stop] are serialized by a mutex. [startSession]
 * may run in parallel (the engine contract allows several sessions) and holds
 * no lock while the engine opens a connection.
 *
 * Restart rule: [EngineState.Failed] can be retried with [initialize].
 * [EngineState.Stopped] is terminal, because [ProxyEngine] is documented as
 * initialize-once; create a new manager with a new engine to start again.
 */
class EngineLifecycleManager(private val engine: ProxyEngine) {

    private val lifecycleMutex = Mutex()
    private val lock = Any()
    private val tracked = LinkedHashSet<TrackedSession>()

    private val _state = MutableStateFlow<EngineState>(EngineState.Uninitialized)
    private val _lastError = MutableStateFlow<EngineError?>(null)

    val state: StateFlow<EngineState> = _state

    /** Most recent engine-level error, or null. Cleared when initialization succeeds. */
    val lastError: StateFlow<EngineError?> = _lastError

    val descriptor: EngineDescriptor get() = engine.descriptor

    /**
     * Initializes the engine. Returns the resulting state: [EngineState.Ready]
     * on success, [EngineState.Failed] otherwise. A no-op (returns the current
     * state) when already initialized or after [stop]. Rethrows cancellation
     * and restores the previous state.
     */
    suspend fun initialize(): EngineState = lifecycleMutex.withLock {
        val previous = _state.value
        when (previous) {
            is EngineState.Uninitialized, is EngineState.Failed -> Unit
            else -> return@withLock previous
        }
        _state.value = EngineState.Initializing
        val result: EngineInitResult = try {
            engine.initialize()
        } catch (e: CancellationException) {
            _state.value = previous
            throw e
        } catch (e: Exception) {
            val error = EngineError(
                EngineErrorKind.INITIALIZATION_FAILED,
                "Engine initialization failed: ${describe(e)}",
            )
            _lastError.value = error
            _state.value = EngineState.Failed(error)
            return@withLock _state.value
        }
        when (result) {
            is EngineInitResult.Ready -> {
                _lastError.value = null
                _state.value = EngineState.Ready
            }
            is EngineInitResult.Unavailable -> {
                val error = EngineError(EngineErrorKind.UNAVAILABLE, safe(result.reason))
                _lastError.value = error
                _state.value = EngineState.Failed(error)
            }
        }
        _state.value
    }

    /**
     * Opens one session through the engine. Requires [EngineState.Ready] or
     * [EngineState.Running]. Failures are returned, not thrown, except
     * cancellation. A bad or unsupported configuration is reported to the
     * caller only; it does not become an engine-level [lastError].
     */
    suspend fun startSession(config: ProxyConfig, options: SessionOptions): EngineOpenResult {
        val current = _state.value
        if (current !is EngineState.Ready && current !is EngineState.Running) {
            // After a failed initialization the root cause is already recorded; keep it.
            val detail = if (current is EngineState.Failed) {
                current.error.message
            } else {
                "Engine is not ready (${stateName(current)})."
            }
            if (current !is EngineState.Failed) record(EngineErrorKind.INVALID_STATE, detail)
            return EngineOpenResult.Failed(FailureCategory.ENGINE_UNAVAILABLE, detail)
        }
        if (!engine.supports(config)) {
            return EngineOpenResult.Failed(
                FailureCategory.UNSUPPORTED_PROTOCOL,
                "The engine does not support this configuration.",
            )
        }
        val result: EngineOpenResult = try {
            engine.open(config, options)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val detail = "Engine failed while opening a session: ${describe(e)}"
            record(EngineErrorKind.SESSION_FAILED, detail)
            return EngineOpenResult.Failed(FailureCategory.ENGINE_ERROR, detail)
        }
        if (result !is EngineOpenResult.Opened) return result

        val wrapped = TrackedSession(result.session)
        val accepted = synchronized(lock) {
            val s = _state.value
            if (s is EngineState.Ready || s is EngineState.Running) {
                tracked.add(wrapped)
                _state.value = EngineState.Running(tracked.size)
                true
            } else {
                false
            }
        }
        if (!accepted) {
            // The engine was stopped while this session was opening: release it.
            closeQuietly(result.session)
            return EngineOpenResult.Failed(
                FailureCategory.CANCELLED,
                "Engine was stopped while the session was opening.",
            )
        }
        return EngineOpenResult.Opened(wrapped)
    }

    /**
     * Closes every open session, then closes the engine. Idempotent. Runs to
     * completion even if the caller is cancelled. Ends in [EngineState.Stopped];
     * a failure of [ProxyEngine.close] is recorded in [lastError].
     */
    suspend fun stop() {
        lifecycleMutex.withLock {
            withContext(NonCancellable) {
                val current = _state.value
                if (current is EngineState.Uninitialized || current is EngineState.Stopped) {
                    return@withContext
                }
                val toClose = synchronized(lock) {
                    _state.value = EngineState.Stopping
                    val copy = tracked.toList()
                    tracked.clear()
                    copy
                }
                toClose.forEach { it.close() }
                try {
                    engine.close()
                } catch (e: Exception) {
                    _lastError.value = EngineError(
                        EngineErrorKind.SHUTDOWN_FAILED,
                        "Engine shutdown failed: ${describe(e)}",
                    )
                }
                _state.value = EngineState.Stopped
            }
        }
    }

    private fun onSessionClosed(session: TrackedSession) {
        synchronized(lock) {
            tracked.remove(session)
            if (_state.value is EngineState.Running) {
                _state.value = if (tracked.isEmpty()) {
                    EngineState.Ready
                } else {
                    EngineState.Running(tracked.size)
                }
            }
        }
    }

    private fun record(kind: EngineErrorKind, message: String) {
        _lastError.value = EngineError(kind, safe(message))
    }

    private fun closeQuietly(session: ProxySession) {
        try {
            session.close()
        } catch (e: Exception) {
            // ProxySession.close must not throw; if it does there is nothing more to release.
        }
    }

    /** Wraps an engine session so closing it updates the manager's state. Idempotent. */
    private inner class TrackedSession(private val delegate: ProxySession) : ProxySession {
        private val closed = AtomicBoolean(false)

        override val endpoint: LocalProxyEndpoint get() = delegate.endpoint

        override val establishedInMs: Long get() = delegate.establishedInMs

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            closeQuietly(delegate)
            onSessionClosed(this)
        }
    }

    private companion object {
        const val MAX_MESSAGE_LENGTH = 200

        fun safe(text: String): String = Redactor.redact(text).take(MAX_MESSAGE_LENGTH)

        /** Exception class name plus redacted message; never the raw message. */
        fun describe(e: Exception): String {
            val name = e::class.simpleName ?: "Exception"
            val message = e.message
            return if (message.isNullOrBlank()) name else safe("$name: $message")
        }

        fun stateName(state: EngineState): String = when (state) {
            is EngineState.Uninitialized -> "not initialized"
            is EngineState.Initializing -> "initializing"
            is EngineState.Ready -> "ready"
            is EngineState.Running -> "running"
            is EngineState.Stopping -> "stopping"
            is EngineState.Stopped -> "stopped"
            is EngineState.Failed -> "failed"
        }
    }
}
