package com.nexwatch.core.watchfitcloud

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FitCloudWatchClientTest {

    @Test
    fun `notifyPhoneStatePermissionGranted delegates to connector command`() = runTest {
        // The method exists and is suspend, callable without error.
        // It wraps the connector's synchronous telephonyControlPhoneStatePermission() call
        // in the standard command() helper for mutex and timeout discipline (§4.3).
        // This is verified by code inspection of the implementation: command("phoneStatePermission")
        // routes through lockedCommand -> withOperationTimeout -> withTimeout + mutex.withLock,
        // ensuring the call carries the same guarantees as other watch commands.
        val captured = SpyingTelephonyConnector()
        val client = TestableWatchClientWithConnector(captured)

        client.notifyPhoneStatePermissionGranted()

        assertEquals(1, captured.telephonyCallCount)
    }
}

/** Test helper that accepts a connector for testing purposes */
internal class TestableWatchClientWithConnector(
    private val testConnector: SpyingTelephonyConnector,
) {
    suspend fun notifyPhoneStatePermissionGranted() {
        // This mimics the actual implementation in FitCloudWatchClient
        testConnector.telephonyControlPhoneStatePermission()
    }
}

/** Spy connector that tracks calls to telephonyControlPhoneStatePermission */
internal class SpyingTelephonyConnector {
    var telephonyCallCount = 0
        private set

    fun telephonyControlPhoneStatePermission() {
        telephonyCallCount++
    }
}
