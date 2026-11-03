package com.josiglo.reminders.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedTextTest {

    @Test
    fun subjectBecomesTheTitleAndTextTheDescription() {
        assertEquals(
            SharedText("Balcony solar guide", "https://example.com/solar"),
            SharedText.from("Balcony solar guide", "https://example.com/solar"),
        )
    }

    @Test
    fun withoutSubjectTheFirstLineIsTheTitle() {
        assertEquals(SharedText("Call Tom", "About the bike rack\nBefore Friday"), SharedText.from(null, "Call Tom\nAbout the bike rack\nBefore Friday"))
        assertEquals(SharedText("Call Tom", null), SharedText.from("  ", " Call Tom \n"))
    }

    @Test
    fun emptyShareIsNothing() {
        assertNull(SharedText.from(null, null))
        assertNull(SharedText.from(" ", "\n"))
    }
}
