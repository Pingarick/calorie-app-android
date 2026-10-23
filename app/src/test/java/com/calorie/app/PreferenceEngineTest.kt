package com.calorie.app

import com.calorie.app.domain.preferences.PreferenceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceEngineTest {

    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_000_000_000_000L

    @Test
    fun cookedSignalMovesWeightUp() {
        val weight = PreferenceEngine.update(0.0, PreferenceEngine.signal(PreferenceEngine.TYPE_COOKED))
        assertEquals(PreferenceEngine.ALPHA, weight, 1e-9)
    }

    @Test
    fun repeatedCookingConvergesToTop() {
        var weight = 0.0
        repeat(20) { weight = PreferenceEngine.update(weight, PreferenceEngine.signal(PreferenceEngine.TYPE_COOKED)) }
        assertTrue("weight=$weight", weight > 0.9)
        assertTrue("weight=$weight", weight <= 1.0)
    }

    @Test
    fun singleRemovalIsNotHate() {
        val weight = PreferenceEngine.update(0.0, PreferenceEngine.signal(PreferenceEngine.TYPE_REMOVED))
        assertTrue("weight=$weight", weight > -0.5)
    }

    @Test
    fun weightIsClamped() {
        var weight = 0.0
        repeat(100) { weight = PreferenceEngine.update(weight, 3.0) }
        assertEquals(1.0, weight, 1e-9)
        repeat(100) { weight = PreferenceEngine.update(weight, -3.0) }
        assertEquals(-1.0, weight, 1e-9)
    }

    @Test
    fun noveltyFallsWithTime() {
        assertEquals(1.0, PreferenceEngine.novelty(null, now), 1e-9)
        assertEquals(0.0, PreferenceEngine.novelty(now, now), 1e-9)
        assertEquals(1.0, PreferenceEngine.novelty(now - 21 * day, now), 1e-9)
        assertEquals(0.5, PreferenceEngine.novelty(now - 10 * day, now), 0.05)
    }

    @Test
    fun recencyPenaltyHasWindows() {
        assertEquals(0.0, PreferenceEngine.recencyPenalty(null, now), 1e-9)
        assertEquals(-1.5, PreferenceEngine.recencyPenalty(now - 1 * day, now), 1e-9)
        assertEquals(-0.8, PreferenceEngine.recencyPenalty(now - 5 * day, now), 1e-9)
        assertEquals(0.0, PreferenceEngine.recencyPenalty(now - 10 * day, now), 1e-9)
    }

    @Test
    fun unknownEventHasNoSignal() {
        assertEquals(0.0, PreferenceEngine.signal("UNKNOWN"), 1e-9)
    }
}
