package com.dayatlas.app.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageReporterTest {
    @Test
    fun dauKeyStripsDashes() {
        assertEquals("dayatlas_alid67_dau_20260929", UsageReporter.dauKey("2026-09-29"))
    }

    @Test
    fun parseValueAcceptsNumberOrString() {
        assertEquals(12L, UsageReporter.parseValue("""{"key":"x","value":12}"""))
        assertEquals(12L, UsageReporter.parseValue("""{"key":"x","value":"12"}"""))
        assertNull(UsageReporter.parseValue("""{"error":"Key not found"}"""))
    }
}
