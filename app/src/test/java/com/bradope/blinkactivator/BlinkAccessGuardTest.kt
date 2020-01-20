package com.bradope.blinkactivator

import com.bradope.blinkactivator.blink.*
import junit.framework.TestCase.assertTrue
import junit.framework.TestCase.assertFalse
import org.junit.Before
import org.junit.Test


class BlinkAccessGuardTest {

    lateinit var guard: BlinkAccessGuard

    @Before
    fun initGuard() {
        guard = BlinkAccessGuard()
    }

    @Test
    fun wilLCheckWhenScheduledToPerform() {
        // given
        guard.setScheduledToPerformChecks(true)

        // when
        val canAccess = guard.canAccessBlink()

        // then
        assertTrue(canAccess)
    }

    @Test
    fun wilLNotCheckWhenNotScheduledToPerform() {
        // given
        guard.setScheduledToPerformChecks(false)

        // when
        val canAccess = guard.canAccessBlink()

        // then
        assertFalse(canAccess)
    }

    @Test
    fun willNotCheckWhenPauseds() {
        // given
        guard.resumeAccess()
        guard.pauseAccess()

        // when
        val canAccess = guard.canAccessBlink()

        // then
        assertFalse(canAccess)
    }

    @Test
    fun willCheckWhenNotPauseds() {
        // given
        guard.pauseAccess()
        guard.resumeAccess()

        // when
        val canAccess = guard.canAccessBlink()

        // then
        assertTrue(canAccess)
    }

    @Test
    fun willNotCheckIfScheduledButPaused() {
        // given
        guard.pauseAccess()
        guard.setScheduledToPerformChecks(true)

        // when
        val canAccess = guard.canAccessBlink()

        // then
        assertFalse(canAccess)
    }

    @Test
    fun willNotCheckIfNotScheduledButResumed() {
        // given
        guard.resumeAccess()
        guard.setScheduledToPerformChecks(false)

        // when
        val canAccess = guard.canAccessBlink()

        // then
        assertFalse(canAccess)
    }

    @Test
    fun willCheckIfScheduledAndResumed() {
        // given
        guard.pauseAccess()
        guard.setScheduledToPerformChecks(true)
        guard.resumeAccess()

        // when
        val canAccess = guard.canAccessBlink()

        // then
        assertTrue(canAccess)
    }
}