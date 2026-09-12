package com.nexwatch.core.watchapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchNotReadyExceptionTest {

    @Test
    fun `message names the offending state`() {
        val exception = WatchNotReadyException(WatchState.Unbound)
        assertEquals(WatchState.Unbound, exception.state)
        assertTrue(exception.message!!.contains("Unbound"))
    }
}
