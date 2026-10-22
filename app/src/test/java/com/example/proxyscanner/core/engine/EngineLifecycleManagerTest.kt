package com.example.proxyscanner.core.engine

import com.example.proxyscanner.core.model.FailureCategory
import com.example.proxyscanner.core.model.Protocol
import com.example.proxyscanner.core.model.ProxyConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

/**
 * Test double for [ProxySession]. It stands in for an engine session only to
 * exercise lifecycle bookkeeping; it carries no real connection and no result.
 */
private class StubSession : ProxySession {
    var closeCount = 0
    var throwOnClose = false
    override val endpoint = LocalProxyEndpoint("127.0.0.1", 1, LocalProxyType.SOCKS5)
    override val establishedInMs: Long = 0L

    override fun close() {
        closeCount++
        if (throwOnClose) throw IllegalStateException("close failed")
    }
}

/** Scriptable test double for [ProxyEngine]; counts calls so tests can assert on them. */
private class ScriptedEngine(
    var initResult: () -> EngineInitResult = { EngineInitResult.Ready },
    var openResult: () -> EngineOpenResult = { EngineOpenResult.Opened(StubSession()) },
    var supported: Boolean = true,
    var closeThrows: Boolean = false,
) : ProxyEngine {
    var initCalls = 0
    var openCalls = 0
    var closeCalls = 0

    override val descriptor = EngineDescriptor("scripted", "Scripted test engine", null, emptySet(), integrated = false)

    override fun supports(config: ProxyConfig): Boolean = supported

    override suspend fun initialize(): EngineInitResult {
        initCalls++
        return initResult()
    }

    override suspend fun open(config: ProxyConfig, options: SessionOptions): EngineOpenResult {
        openCalls++
        return openResult()
    }

    override suspend fun close() {
        closeCalls++
        if (closeThrows) throw IllegalStateException("shutdown failed")
    }
}

class EngineLifecycleManagerTest {

    private val config = ProxyConfig(
        fingerprint = "fp-1",
        protocol = Protocol.VLESS,
        address = "host.example.invalid",
        port = 443,
    )
    private val options = SessionOptions(connectTimeoutMs = 1_000L)

    @Test
    fun startsUninitializedWithNoError() {
        val manager = EngineLifecycleManager(ScriptedEngine())
        assertEquals(EngineState.Uninitialized, manager.state.value)
        assertNull(manager.lastError.value)
    }

    @Test
    fun initializeSuccessMovesToReady() = runTest {
        val engine = ScriptedEngine()
        val manager = EngineLifecycleManager(engine)
        assertEquals(EngineState.Ready, manager.initialize())
        assertEquals(EngineState.Ready, manager.state.value)
        assertNull(manager.lastError.value)
        assertEquals(1, engine.initCalls)
    }

    @Test
    fun initializeIsNotRepeatedOnceReady() = runTest {
        val engine = ScriptedEngine()
        val manager = EngineLifecycleManager(engine)
        manager.initialize()
        manager.initialize()
        assertEquals(1, engine.initCalls)
    }

    @Test
    fun unavailablePlaceholderEngineReportsFailedAndUnavailable() = runTest {
        val manager = EngineLifecycleManager(UnavailableProxyEngine())
        val state = manager.initialize()
        assertTrue(state is EngineState.Failed)
        assertEquals(EngineErrorKind.UNAVAILABLE, manager.lastError.value?.kind)
        assertFalse(manager.descriptor.integrated)
    }

    @Test
    fun unavailablePlaceholderNeverOpensASession() = runTest {
        val manager = EngineLifecycleManager(UnavailableProxyEngine())
        manager.initialize()
        val result = manager.startSession(config, options)
        assertTrue(result is EngineOpenResult.Failed)
        assertEquals(FailureCategory.ENGINE_UNAVAILABLE, (result as EngineOpenResult.Failed).category)
        // The root cause of the failed initialization is kept, not overwritten.
        assertEquals(EngineErrorKind.UNAVAILABLE, manager.lastError.value?.kind)
    }

    @Test
    fun initializeExceptionBecomesRedactedFailure() = runTest {
        val engine = ScriptedEngine(
            initResult = { throw IllegalStateException("bad link vless://secret-id@host.example.invalid:443") },
        )
        val manager = EngineLifecycleManager(engine)
        val state = manager.initialize()
        assertTrue(state is EngineState.Failed)
        val error = manager.lastError.value
        assertEquals(EngineErrorKind.INITIALIZATION_FAILED, error?.kind)
        assertFalse(error!!.message.contains("secret-id"))
        assertFalse(error.message.contains("host.example.invalid"))
        assertTrue(error.message.contains("IllegalStateException"))
    }

    @Test
    fun unavailableReasonIsRedactedAndLengthLimited() = runTest {
        val engine = ScriptedEngine(
            initResult = { EngineInitResult.Unavailable("token=abc123 " + "x".repeat(500)) },
        )
        val manager = EngineLifecycleManager(engine)
        manager.initialize()
        val message = manager.lastError.value!!.message
        assertFalse(message.contains("abc123"))
        assertTrue(message.length <= 200)
    }

    @Test
    fun failedInitializationCanBeRetried() = runTest {
        var attempts = 0
        val engine = ScriptedEngine(
            initResult = {
                attempts++
                if (attempts == 1) EngineInitResult.Unavailable("not yet") else EngineInitResult.Ready
            },
        )
        val manager = EngineLifecycleManager(engine)
        assertTrue(manager.initialize() is EngineState.Failed)
        assertEquals(EngineState.Ready, manager.initialize())
        assertNull(manager.lastError.value)
        assertEquals(2, engine.initCalls)
    }

    @Test
    fun cancellationDuringInitializeIsRethrownAndStateRestored() = runTest {
        val engine = ScriptedEngine(initResult = { throw CancellationException("cancelled") })
        val manager = EngineLifecycleManager(engine)
        try {
            manager.initialize()
            fail("CancellationException expected")
        } catch (expected: CancellationException) {
            // expected
        }
        assertEquals(EngineState.Uninitialized, manager.state.value)
        assertNull(manager.lastError.value)
    }

    @Test
    fun startSessionBeforeInitializeFailsWithoutCallingEngine() = runTest {
        val engine = ScriptedEngine()
        val manager = EngineLifecycleManager(engine)
        val result = manager.startSession(config, options)
        assertEquals(FailureCategory.ENGINE_UNAVAILABLE, (result as EngineOpenResult.Failed).category)
        assertEquals(0, engine.openCalls)
        assertEquals(EngineErrorKind.INVALID_STATE, manager.lastError.value?.kind)
    }

    @Test
    fun unsupportedConfigIsRejectedWithoutCallingEngineOrRecordingAnEngineError() = runTest {
        val engine = ScriptedEngine(supported = false)
        val manager = EngineLifecycleManager(engine)
        manager.initialize()
        val result = manager.startSession(config, options)
        assertEquals(FailureCategory.UNSUPPORTED_PROTOCOL, (result as EngineOpenResult.Failed).category)
        assertEquals(0, engine.openCalls)
        assertEquals(EngineState.Ready, manager.state.value)
        assertNull(manager.lastError.value)
    }

    @Test
    fun openedSessionIsTrackedAndClosingItReturnsToReady() = runTest {
        val stub = StubSession()
        val engine = ScriptedEngine(openResult = { EngineOpenResult.Opened(stub) })
        val manager = EngineLifecycleManager(engine)
        manager.initialize()
        val result = manager.startSession(config, options)
        val session = (result as EngineOpenResult.Opened).session
        assertEquals(EngineState.Running(1), manager.state.value)
        assertSame(stub.endpoint, session.endpoint)

        session.close()
        session.close() // idempotent
        assertEquals(1, stub.closeCount)
        assertEquals(EngineState.Ready, manager.state.value)
    }

    @Test
    fun severalSessionsAreCounted() = runTest {
        val manager = EngineLifecycleManager(ScriptedEngine())
        manager.initialize()
        val a = (manager.startSession(config, options) as EngineOpenResult.Opened).session
        val b = (manager.startSession(config, options) as EngineOpenResult.Opened).session
        assertEquals(EngineState.Running(2), manager.state.value)
        a.close()
        assertEquals(EngineState.Running(1), manager.state.value)
        b.close()
        assertEquals(EngineState.Ready, manager.state.value)
    }

    @Test
    fun engineFailureResultIsPassedThroughUnchanged() = runTest {
        val failure = EngineOpenResult.Failed(FailureCategory.CONNECT_TIMEOUT, "timed out")
        val manager = EngineLifecycleManager(ScriptedEngine(openResult = { failure }))
        manager.initialize()
        assertSame(failure, manager.startSession(config, options))
        assertEquals(EngineState.Ready, manager.state.value)
    }

    @Test
    fun exceptionWhileOpeningBecomesEngineErrorAndKeepsEngineReady() = runTest {
        val engine = ScriptedEngine(openResult = { throw IllegalStateException("boom password=hunter2") })
        val manager = EngineLifecycleManager(engine)
        manager.initialize()
        val result = manager.startSession(config, options) as EngineOpenResult.Failed
        assertEquals(FailureCategory.ENGINE_ERROR, result.category)
        assertFalse(result.safeDetail!!.contains("hunter2"))
        assertEquals(EngineErrorKind.SESSION_FAILED, manager.lastError.value?.kind)
        assertEquals(EngineState.Ready, manager.state.value)
    }

    @Test
    fun stopClosesOpenSessionsThenEngineAndEndsStopped() = runTest {
        val stubs = mutableListOf<StubSession>()
        val engine = ScriptedEngine(openResult = { EngineOpenResult.Opened(StubSession().also { stubs += it }) })
        val manager = EngineLifecycleManager(engine)
        manager.initialize()
        manager.startSession(config, options)
        manager.startSession(config, options)

        manager.stop()
        assertEquals(EngineState.Stopped, manager.state.value)
        assertEquals(listOf(1, 1), stubs.map { it.closeCount })
        assertEquals(1, engine.closeCalls)
    }

    @Test
    fun stopIsIdempotentAndSkippedWhenNeverInitialized() = runTest {
        val untouched = ScriptedEngine()
        val fresh = EngineLifecycleManager(untouched)
        fresh.stop()
        assertEquals(EngineState.Uninitialized, fresh.state.value)
        assertEquals(0, untouched.closeCalls)

        val engine = ScriptedEngine()
        val manager = EngineLifecycleManager(engine)
        manager.initialize()
        manager.stop()
        manager.stop()
        assertEquals(1, engine.closeCalls)
    }

    @Test
    fun closingATrackedSessionAfterStopDoesNotChangeState() = runTest {
        val manager = EngineLifecycleManager(ScriptedEngine())
        manager.initialize()
        val session = (manager.startSession(config, options) as EngineOpenResult.Opened).session
        manager.stop()
        session.close()
        assertEquals(EngineState.Stopped, manager.state.value)
    }

    @Test
    fun stoppedIsTerminalAndDoesNotReinitializeTheEngine() = runTest {
        val engine = ScriptedEngine()
        val manager = EngineLifecycleManager(engine)
        manager.initialize()
        manager.stop()
        assertEquals(EngineState.Stopped, manager.initialize())
        assertEquals(1, engine.initCalls)
        val result = manager.startSession(config, options)
        assertTrue(result is EngineOpenResult.Failed)
        assertEquals(0, engine.openCalls)
    }

    @Test
    fun engineCloseFailureIsReportedButStillStops() = runTest {
        val engine = ScriptedEngine(closeThrows = true)
        val manager = EngineLifecycleManager(engine)
        manager.initialize()
        manager.stop()
        assertEquals(EngineState.Stopped, manager.state.value)
        assertEquals(EngineErrorKind.SHUTDOWN_FAILED, manager.lastError.value?.kind)
    }

    @Test
    fun throwingSessionCloseDoesNotBreakStop() = runTest {
        val stub = StubSession().also { it.throwOnClose = true }
        val engine = ScriptedEngine(openResult = { EngineOpenResult.Opened(stub) })
        val manager = EngineLifecycleManager(engine)
        manager.initialize()
        manager.startSession(config, options)
        manager.stop()
        assertEquals(EngineState.Stopped, manager.state.value)
        assertEquals(1, stub.closeCount)
        assertEquals(1, engine.closeCalls)
    }
}
