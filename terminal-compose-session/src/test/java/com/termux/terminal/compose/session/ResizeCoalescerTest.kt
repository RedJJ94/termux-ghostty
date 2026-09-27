package com.termux.terminal.compose.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResizeCoalescerTest {

    @Test
    fun zeroWindowAlwaysAppliesImmediately() {
        var now = 0L
        val coalescer = ResizeCoalescer(0L) { now }
        assertTrue(coalescer.shouldApplyNow())
        coalescer.onApplied()
        now += 1L
        assertTrue(coalescer.shouldApplyNow())
    }

    @Test
    fun firstResizeAppliesImmediately() {
        val coalescer = ResizeCoalescer(50L) { 0L }
        assertTrue(coalescer.shouldApplyNow())
    }

    @Test
    fun burstWithinWindowDefersToTrailing() {
        var now = 1_000L
        val coalescer = ResizeCoalescer(50L) { now }
        assertTrue(coalescer.shouldApplyNow())
        coalescer.onApplied()
        now += 10L
        assertFalse(coalescer.shouldApplyNow())
    }

    @Test
    fun expiredWindowAppliesAgain() {
        var now = 1_000L
        val coalescer = ResizeCoalescer(50L) { now }
        coalescer.onApplied()
        now += 50L
        assertTrue(coalescer.shouldApplyNow())
    }

    @Test
    fun lastApplyTracksMostRecentApply() {
        var now = 1_000L
        val coalescer = ResizeCoalescer(50L) { now }
        coalescer.onApplied()
        now += 60L
        assertTrue(coalescer.shouldApplyNow())
        coalescer.onApplied()
        now += 10L
        assertFalse(coalescer.shouldApplyNow())
    }

    @Test
    fun negativeWindowIsCoercedToImmediate() {
        val coalescer = ResizeCoalescer(-5L) { 0L }
        assertTrue(coalescer.shouldApplyNow())
        coalescer.setDebounceMillis(-1L)
        assertTrue(coalescer.shouldApplyNow())
    }
}
