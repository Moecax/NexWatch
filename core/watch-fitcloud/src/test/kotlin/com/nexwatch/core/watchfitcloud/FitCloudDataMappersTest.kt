package com.nexwatch.core.watchfitcloud

import com.nexwatch.core.model.DecodedHealthRecord
import com.topstep.fitcloud.sdk.v2.model.data.FcStepData
import com.topstep.fitcloud.sdk.v2.model.data.FcTodayTotalData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FitCloudDataMappersTest {

    @Test
    fun `step mapping matches the real recon fixture`() {
        // docs/recon/fixtures/step_bucket.txt: t=1789472400000 step=14 distance=0.00915 calories=0.393
        val real = FcStepData(1_789_472_400_000L, 14, 0.00915f, 0.393f, 0)

        val decoded = real.toDecodedStep()

        assertEquals(1_789_472_400_000L, decoded.startMs)
        assertEquals(14, decoded.count)
        assertEquals(9.15f, decoded.distanceM, 0.01f)
        assertEquals(0.393f, decoded.kcal, 0.001f)
    }

    @Test
    fun `today-total mapping converts calorie from milli-kcal and drops a zero heart rate`() {
        val real = FcTodayTotalData(1_789_472_400_000L, 9, 9, 393, 0, 0, 0, 0, 0, 0, 0)

        val decoded = real.toDecodedTodayTotal()

        assertEquals(9, decoded.steps)
        assertEquals(9, decoded.distanceM)
        assertEquals(0.393f, decoded.kcal, 0.001f)
        assertNull(decoded.heartRateBpm)
    }
}
