package com.nexwatch.core.common

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultCoroutineDispatchersTest {

    @Test
    fun `io dispatcher is the IO dispatcher`() {
        val dispatchers: CoroutineDispatchers = DefaultCoroutineDispatchers()
        assertEquals(Dispatchers.IO, dispatchers.io)
    }

    @Test
    fun `default dispatcher is the Default dispatcher`() {
        val dispatchers: CoroutineDispatchers = DefaultCoroutineDispatchers()
        assertEquals(Dispatchers.Default, dispatchers.default)
    }
}
