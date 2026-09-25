package com.nexwatch.core.data.journal

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.database.inMemoryTestDatabase
import com.nexwatch.core.watchapi.RawBatch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JournalRepositoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : CoroutineDispatchers {
        override val io = dispatcher
        override val default = dispatcher
    }

    @Test
    fun `append writes an unprocessed row with the batch's dataType and payload`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        val repository = JournalRepository(db.rawIngestDao(), dispatchers)

        repository.append(RawBatch(dataType = "step", payloadJson = """["AAA="]"""))

        val rows = db.rawIngestDao().findUnprocessed("step")
        assertEquals(1, rows.size)
        assertEquals("""["AAA="]""", rows[0].payloadJson)
        assertNull(rows[0].processedAt)
        db.close()
    }
}
