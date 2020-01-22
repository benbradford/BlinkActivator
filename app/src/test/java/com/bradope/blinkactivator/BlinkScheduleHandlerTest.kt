package com.bradope.blinkactivator

import com.bradope.blinkactivator.blink.*
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class BlinkScheduleHandlerTest {

    @RelaxedMockK
    lateinit var blinkAccessGuard: BlinkAccessGuard

    @MockK
    lateinit var hoursAndMinsFactory: HoursAndMinsFactory

    @MockK
    lateinit var onOffTimes: BlinkOnAndOffTimes

    val hoursAndMins = mockk<HoursAndMins>()

    @Before
    fun before() {
        MockKAnnotations.init(this)
        every {hoursAndMinsFactory.now()} returns hoursAndMins
        every {hoursAndMins.hours} returns 12
        every {hoursAndMins.minutes} returns 0
    }

    @Test
    fun noSettingsWillAllowChecksToBePerformed() {

        // given
        every {onOffTimes.enableAfterTime} returns null
        every {onOffTimes.disableAfterTime} returns null

        // when
        performCheck()

        //then
        verify (exactly = 1) { blinkAccessGuard.setScheduledToPerformChecks(true) }
        verify (exactly = 0) { hoursAndMins.hours }
        verify (exactly = 0) { hoursAndMins.minutes }
    }

    @Test
    fun onlyEnableAfterTimeWIllPerformCheck() {

        // given
        every {onOffTimes.enableAfterTime} returns HoursAndMins(23, 59)
        every {onOffTimes.disableAfterTime} returns null

        // when
        performCheck()

        //then
        verify (exactly = 1) { blinkAccessGuard.setScheduledToPerformChecks(true) }
        verify (exactly = 0) { hoursAndMins.hours }
        verify (exactly = 0) { hoursAndMins.minutes }
    }

    @Test
    fun onlyDisableAfterTimeWIllPerformCheck() {

        // given
        every {onOffTimes.enableAfterTime} returns null
        every {onOffTimes.disableAfterTime} returns HoursAndMins(0, 0)

        // when
        performCheck()

        //then
        verify (exactly = 1) { blinkAccessGuard.setScheduledToPerformChecks(true) }
        verify (exactly = 0) { hoursAndMins.hours }
        verify (exactly = 0) { hoursAndMins.minutes }
    }

    @Test
    fun disableAfterBeforeEnableAfterWillPerformChecks() {

        // given
        every {onOffTimes.enableAfterTime} returns HoursAndMins(23, 59)
        every {onOffTimes.disableAfterTime} returns HoursAndMins(0, 0)

        // when
        performCheck()

        //then
        verify (exactly = 1) { blinkAccessGuard.setScheduledToPerformChecks(false) }

    }

    @Test
    fun nowIsInBetweenEnableAndDisableTimesThenPerformCheck() {

        // given
        every {onOffTimes.enableAfterTime} returns HoursAndMins(11, 59)
        every {onOffTimes.disableAfterTime} returns HoursAndMins(12, 1)

        // when
        performCheck()

        //then
        verify (exactly = 1) { blinkAccessGuard.setScheduledToPerformChecks(true) }
    }

    @Test
    fun nowIsInBeforeEnableTimeThenDoNotPerformCheck() {

        // given
        every {onOffTimes.enableAfterTime} returns HoursAndMins(12, 1)
        every {onOffTimes.disableAfterTime} returns HoursAndMins(13, 1)

        // when
        performCheck()

        //then
        verify (exactly = 1) { blinkAccessGuard.setScheduledToPerformChecks(false) }
    }

    @Test
    fun nowIsInAfterDisableTimeThenDoNotPerformCheck() {

        // given
        every {onOffTimes.enableAfterTime} returns HoursAndMins(11, 1)
        every {onOffTimes.disableAfterTime} returns HoursAndMins(11, 59)

        // when
        performCheck()

        //then
        verify (exactly = 1) { blinkAccessGuard.setScheduledToPerformChecks(false) }
    }

    @Test
    fun nowIsEnableTimeThenPerformCheck() {

        // given
        every {onOffTimes.enableAfterTime} returns HoursAndMins(12, 0)
        every {onOffTimes.disableAfterTime} returns HoursAndMins(12, 1)

        // when
        performCheck()

        //then
        verify (exactly = 1) { blinkAccessGuard.setScheduledToPerformChecks(true) }
    }

    @Test
    fun nowIsDisableTimeThenDoNotPerformCheck() {

        // given
        every {onOffTimes.enableAfterTime} returns HoursAndMins(11, 0)
        every {onOffTimes.disableAfterTime} returns HoursAndMins(12, 0)

        // when
        performCheck()

        //then
        verify (exactly = 1) { blinkAccessGuard.setScheduledToPerformChecks(false) }
    }

    private fun performCheck() = BlinkScheduleHandler(
        blinkAccessGuard = blinkAccessGuard,
        hoursAndMinsFactory = hoursAndMinsFactory,
        onOffTimes = onOffTimes
    ).performCheck()

}