package com.dayatlas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpContentTest {
    private val sample = """
        DayAtlas Help

        DayAtlas records your location in the background throughout the day.

        QUICK START

        Turn on Daily Mode in Settings and the app starts recording automatically.

        1) RECORDING MODES

        Daily mode is on by default.

        Manual mode uses the Start/Stop button on the home screen.

        FREQUENTLY ASKED QUESTIONS

        Does recording stop if I close the app?
        No, it keeps recording in the background.

        Developed by: Ali Dinçer
    """.trimIndent()

    @Test
    fun splitsTitleIntroSectionsAndFooter() {
        val parsed = HelpContent.parse(sample)
        assertEquals("DayAtlas Help", parsed.title)
        assertTrue(parsed.intro.contains("records your location"))
        assertEquals(listOf("QUICK START", "1) RECORDING MODES", "FREQUENTLY ASKED QUESTIONS"), parsed.sections.map { it.heading })
        assertEquals("Developed by: Ali Dinçer", parsed.footer)
    }

    @Test
    fun sectionBodyJoinsAllItsParagraphs() {
        val parsed = HelpContent.parse(sample)
        val recordingModes = parsed.sections.first { it.heading == "1) RECORDING MODES" }
        assertTrue(recordingModes.body.contains("Daily mode is on by default."))
        assertTrue(recordingModes.body.contains("Manual mode uses the Start/Stop button"))
    }

    @Test
    fun faqQuestionAndAnswerStayInOneBlock() {
        val parsed = HelpContent.parse(sample)
        val faq = parsed.sections.first { it.heading == "FREQUENTLY ASKED QUESTIONS" }
        assertTrue(faq.body.contains("Does recording stop if I close the app?\nNo, it keeps recording in the background."))
    }

    @Test
    fun emptyInputProducesEmptyResult() {
        val parsed = HelpContent.parse("")
        assertEquals("", parsed.title)
        assertTrue(parsed.sections.isEmpty())
    }
}
