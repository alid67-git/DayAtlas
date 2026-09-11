package com.dayatlas.app.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateCheckSchedulerLogicTest {
    @Test
    fun middayConstantIsNoon() {
        assertEquals(12, UpdateCheckScheduler.MIDDAY.hour)
        assertEquals(0, UpdateCheckScheduler.MIDDAY.minute)
    }
}
