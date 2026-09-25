package com.nexwatch.core.database

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Stands in for a MigrationTestHelper test until schema version 2 exists (§5.5) — there is
 * nothing to migrate FROM yet, so this instead proves version 1 builds clean and every DAO
 * is reachable, which is what "green for every schema version so far" means with one version.
 */
class SchemaSmokeTest {

    @Test
    fun `database opens and every DAO responds to an empty query`() = runTest {
        val db = inMemoryTestDatabase()

        assertNull(db.stepsDao().latestEndTime("no-such-device"))
        assertNull(db.deviceDao().findByAddress("no-such-device"))
        assertNull(db.dailySummaryDao().findByDate("no-such-device", "2026-01-01"))
        assertNull(db.changeLogDao().latestSeq())

        db.close()
    }
}
