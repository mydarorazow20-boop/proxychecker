package com.example.proxyscanner.core.engine

/** Why the engine layer reported a problem. Never carries credentials. */
enum class EngineErrorKind {
    /** No engine is integrated, or the engine declared itself unavailable. */
    UNAVAILABLE,

    /** [ProxyEngine.initialize] threw. */
    INITIALIZATION_FAILED,

    /** A session could not be opened because of an engine fault (not a bad config). */
    SESSION_FAILED,

    /** [ProxyEngine.close] threw while stopping. */
    SHUTDOWN_FAILED,

    /** An operation was requested in a lifecycle state that does not allow it. */
    INVALID_STATE,
}

/** [message] is already redacted and length-limited; it is safe to show or log. */
data class EngineError(val kind: EngineErrorKind, val message: String)

/**
 * Lifecycle of one [EngineLifecycleManager].
 *
 * ```
 * Uninitialized -> Initializing -> Ready <-> Running(n)
 *                       |            |          |
 *                       v            +----------+--> Stopping -> Stopped (terminal)
 *                    Failed  --(initialize again)--> Initializing
 * ```
 */
sealed interface EngineState {
    data object Uninitialized : EngineState

    data object Initializing : EngineState

    /** Initialized, no session open. */
    data object Ready : EngineState

    /** At least one session is open. */
    data class Running(val activeSessions: Int) : EngineState

    data object Stopping : EngineState

    /** Terminal for this manager instance; create a new manager (and engine) to start again. */
    data object Stopped : EngineState

    /** Initialization failed or the engine is unavailable. [initialize] may be retried. */
    data class Failed(val error: EngineError) : EngineState
}
