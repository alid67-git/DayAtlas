package com.dayatlas.app.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayStatsSpanTest {
    @Test
    fun sevenCardsAreTwoTwoThree() {
        assertEquals(3, DayStatsAdapter.spanSizeFor(0, 7))
        assertEquals(3, DayStatsAdapter.spanSizeFor(3, 7))
        assertEquals(2, DayStatsAdapter.spanSizeFor(4, 7))
        assertEquals(2, DayStatsAdapter.spanSizeFor(6, 7))
        assertEquals(3, DayStatsAdapter.rowCountFor(7))
    }

    @Test
    fun trioSlotsAreCompactUpperPairsAreNot() {
        assertFalse(DayStatsAdapter.isCompactSlot(0, 7))
        assertFalse(DayStatsAdapter.isCompactSlot(3, 7))
        assertTrue(DayStatsAdapter.isCompactSlot(4, 7))
        assertTrue(DayStatsAdapter.isCompactSlot(6, 7))
    }

    @Test
    fun fullscreenSevenCardsAreFourPlusThree() {
        val span = DayStatsAdapter.GRID_SPAN_TWO_ROW
        assertEquals(3, DayStatsAdapter.spanSizeFor(0, 7, span, twoRow = true))
        assertEquals(3, DayStatsAdapter.spanSizeFor(3, 7, span, twoRow = true))
        assertEquals(4, DayStatsAdapter.spanSizeFor(4, 7, span, twoRow = true))
        assertEquals(4, DayStatsAdapter.spanSizeFor(6, 7, span, twoRow = true))
        assertEquals(2, DayStatsAdapter.rowCountFor(7, span, twoRow = true))
        assertTrue(DayStatsAdapter.isCompactSlot(0, 7, span, twoRow = true))
        assertTrue(DayStatsAdapter.isCompactSlot(6, 7, span, twoRow = true))
    }
}
