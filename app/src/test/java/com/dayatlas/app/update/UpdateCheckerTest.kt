package com.dayatlas.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun parsesPlainVersion() {
        assertEquals(
            "v1.2.0",
            UpdateChecker.parseReleaseVersion("DayAtlas Android (latest) - v1.2.0"),
        )
    }

    @Test
    fun parsesVersionWithDiagnosticSuffix() {
        // Regression: a trailing "-diag1" tag used to make the whole regex
        // fail to match (it required digits/dots to be the very last
        // characters), so every update check silently reported "up to date".
        assertEquals(
            "v1.1.0-diag1",
            UpdateChecker.parseReleaseVersion("DayAtlas Android (latest) - v1.1.0-diag1"),
        )
    }

    @Test
    fun returnsNullWhenNoVersionPresent() {
        assertNull(UpdateChecker.parseReleaseVersion("DayAtlas Android (latest)"))
    }
}
