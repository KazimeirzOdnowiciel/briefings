package dev.lukat.briefing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FixtureTest {
    @Test
    fun severalMatchesAtOneKickOff() {
        val f = parseFixture("Champions League — Arsenal v Borussia Dortmund, Como v AEK Athens and Feyenoord v Porto, 21:00")
        assertEquals("Champions League", f.label)
        assertEquals(listOf("Arsenal v Borussia Dortmund", "Como v AEK Athens", "Feyenoord v Porto"), f.matches)
        assertEquals("21:00", f.time)
        assertNull(f.day)
    }

    @Test
    fun nbaAfterMidnight() {
        val f = parseFixture("NBA — Pistons v Magic, 01:00 (Wednesday)")
        assertEquals("NBA", f.label)
        assertEquals(listOf("Pistons v Magic"), f.matches)
        assertEquals("01:00", f.time)
        assertEquals("Wed", f.day)
    }

    @Test
    fun polandIsFlagged() {
        val f = parseFixture("Nations League — Poland v Sweden, 20:45 (Wrocław)")
        assertTrue(f.poland)
        assertEquals("20:45", f.time)
        assertEquals(listOf("Poland v Sweden (Wrocław)"), f.matches)
    }

    @Test
    fun aTeamNameWithAndIsNotSplit() {
        val f = parseFixture("Premier League — Brighton and Hove Albion v Fulham, 21:00")
        assertEquals(listOf("Brighton and Hove Albion v Fulham"), f.matches)
    }
}
