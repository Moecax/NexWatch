package com.nexwatch.core.watchfake

import app.cash.turbine.test
import com.nexwatch.core.watchapi.OutgoingNotification
import com.nexwatch.core.watchapi.UserProfile
import com.nexwatch.core.watchapi.WatchNotReadyException
import com.nexwatch.core.watchapi.WatchState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FakeWatchClientTest {

    private val profile = UserProfile(
        sex = UserProfile.Sex.MALE,
        age = 30,
        heightCm = 175,
        weightKg = 70,
    )

    @Test
    fun `initial state is Unbound`() = runTest {
        val client = FakeWatchClient()
        assertEquals(WatchState.Unbound, client.state.value)
    }

    @Test
    fun `bind transitions through Connecting to Ready`() = runTest {
        val client = FakeWatchClient()
        client.state.test {
            assertEquals(WatchState.Unbound, awaitItem())
            client.bind("AA:BB:CC:DD:EE:FF", profile)
            assertEquals(WatchState.Connecting, awaitItem())
            val ready = awaitItem()
            assertTrue(ready is WatchState.Ready)
        }
    }

    @Test
    fun `unbind returns to Unbound`() = runTest {
        val client = FakeWatchClient()
        client.bind("AA:BB:CC:DD:EE:FF", profile)
        client.unbind(keepWatchData = true)
        assertEquals(WatchState.Unbound, client.state.value)
    }

    @Test
    fun `commands fail fast when not Ready`() = runTest {
        val client = FakeWatchClient()
        try {
            client.batteryLevel()
            fail("expected WatchNotReadyException")
        } catch (e: WatchNotReadyException) {
            assertEquals(WatchState.Unbound, e.state)
        }
    }

    @Test
    fun `sendNotification succeeds once Ready`() = runTest {
        val client = FakeWatchClient()
        client.bind("AA:BB:CC:DD:EE:FF", profile)
        val notification = OutgoingNotification(
            sourcePackage = "com.whatsapp",
            type = OutgoingNotification.NotificationType.WHATSAPP,
            title = "Ada",
            content = "hey",
        )
        val result = client.sendNotification(notification)
        assertTrue(result is com.nexwatch.core.watchapi.SendResult.Sent)
    }

    @Test
    fun `syncHealthData emits progress and completes`() = runTest {
        val client = FakeWatchClient()
        client.bind("AA:BB:CC:DD:EE:FF", profile)
        val progress = client.syncHealthData().toList()
        assertTrue(progress.isNotEmpty())
        assertTrue(progress.last().completed)
        assertEquals(progress.last().itemsSynced, progress.last().totalItems)
    }

    @Test
    fun `liveHeartRate emits while collected`() = runTest {
        val client = FakeWatchClient()
        client.bind("AA:BB:CC:DD:EE:FF", profile)
        val first = client.liveHeartRate().first()
        assertTrue(first in 40..200)
    }

    @Test
    fun `forceState sets any state on demand`() = runTest {
        val client = FakeWatchClient()
        client.forceState(WatchState.AuthFailed("simulated"))
        assertEquals(WatchState.AuthFailed("simulated"), client.state.value)
    }
}
