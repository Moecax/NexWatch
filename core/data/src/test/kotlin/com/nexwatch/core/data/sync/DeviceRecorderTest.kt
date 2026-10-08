package com.nexwatch.core.data.sync

import com.nexwatch.core.database.inMemoryTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals

class DeviceRecorderTest {

    private val address = "AA:BB:CC:DD:EE:FF"

    @Test
    fun `learning the firmware after binding is not a firmware update`() = runTest {
        val db = inMemoryTestDatabase()
        val recorder = DeviceRecorder(db.deviceDao(), testDispatchers(this))

        recorder.record(address, firmwareVersion = null)
        recorder.record(address, firmwareVersion = "00000105")

        assertEquals("00000105", db.deviceDao().findByAddress(address)?.firmwareVersion)
        assertEquals(listOf("BOUND"), db.deviceDao().observeEvents(address).first().map { it.type })
        db.close()
    }

    @Test
    fun `a changed firmware version is recorded as an update`() = runTest {
        val db = inMemoryTestDatabase()
        val recorder = DeviceRecorder(db.deviceDao(), testDispatchers(this))

        recorder.record(address, firmwareVersion = "00000105")
        recorder.record(address, firmwareVersion = null)
        recorder.record(address, firmwareVersion = "00000106")

        assertEquals("00000106", db.deviceDao().findByAddress(address)?.firmwareVersion)
        val events = db.deviceDao().observeEvents(address).first()
        assertEquals(setOf("BOUND", "FIRMWARE_UPDATED"), events.map { it.type }.toSet())
        assertEquals("00000105 -> 00000106", events.single { it.type == "FIRMWARE_UPDATED" }.details)
        db.close()
    }
}
