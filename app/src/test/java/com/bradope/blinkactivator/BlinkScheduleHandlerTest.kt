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
    lateinit var onOffTimes: BlinkDailySchedule

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
        every {onOffTimes.scheduledDisarmTime} returns null
        every {onOffTimes.scheduledArmTime} returns null

        // when
        performCheck()

        //then
        verify (exactly = 1) { blinkAccessGuard.setScheduledToPerformChecks(true) }
        verify (exactly = 0) { hoursAndMins.hours }
        verify (exactly = 0) { hoursAndMins.minutes }
    }

    @Test
    fun onlyDisarmTimeWillPerformCheck() {

        // given
        every {onOffTimes.scheduledDisarmTime} returns HoursAndMins(23, 59)
        every {onOffTimes.scheduledArmTime} returns null

        // when
        performCheck()

        //then
        verify (exactly = 1) { blinkAccessGuard.setScheduledToPerformChecks(true) }
        verify (exactly = 0) { hoursAndMins.hours }
        verify (exactly = 0) { hoursAndMins.minutes }
    }

    @Test
    fun onlyArmTimeWillPerformCheck() {

        // given
        every {onOffTimes.scheduledDisarmTime} returns null
        every {onOffTimes.scheduledArmTime} returns HoursAndMins(0, 0)

        // when
        performCheck()

        //then
        verify (exactly = 1) { blinkAccessGuard.setScheduledToPerformChecks(true) }
        verify (exactly = 0) { hoursAndMins.hours }
        verify (exactly = 0) { hoursAndMins.minutes }
    }

    @Test
    fun disarmTimeAfterArmWillPerformChecks() {

        // given
        every {onOffTimes.scheduledDisarmTime} returns HoursAndMins(23, 59)
        every {onOffTimes.scheduledArmTime} returns HoursAndMins(0, 0)

        // when
        performCheck()

        //then
        verify (exactly = 1) { blinkAccessGuard.setScheduledToPerformChecks(false) }

    }

    @Test
    fun nowIsInBetweenArmAndDisarmTimesThenPerformCheck() {

        // given
        every {onOffTimes.scheduledDisarmTime} returns HoursAndMins(11, 59)
        every {onOffTimes.scheduledArmTime} returns HoursAndMins(12, 1)

        // when
        performCheck()

        //then
        verify (exactly = 1) { blinkAccessGuard.setScheduledToPerformChecks(true) }
    }

    @Test
    fun nowIsBeforeDisarmThenDoNotPerformCheck() {

        // given
        every {onOffTimes.scheduledDisarmTime} returns HoursAndMins(12, 1)
        every {onOffTimes.scheduledArmTime} returns HoursAndMins(13, 1)

        // when
        performCheck()

        //then
        verify (exactly = 1) { blinkAccessGuard.setScheduledToPerformChecks(false) }
    }

    @Test
    fun nowIsAfterArmThenDoNotPerformCheck() {

        // given
        every {onOffTimes.scheduledDisarmTime} returns HoursAndMins(11, 1)
        every {onOffTimes.scheduledArmTime} returns HoursAndMins(11, 59)

        // when
        performCheck()

        //then
        verify (exactly = 1) { blinkAccessGuard.setScheduledToPerformChecks(false) }
    }

    @Test
    fun nowIsDisarmTimeThenPerformCheck() {

        // given
        every {onOffTimes.scheduledDisarmTime} returns HoursAndMins(12, 0)
        every {onOffTimes.scheduledArmTime} returns HoursAndMins(12, 1)

        // when
        performCheck()

        //then
        verify (exactly = 1) { blinkAccessGuard.setScheduledToPerformChecks(true) }
    }

    @Test
    fun nowIsArmTimeThenDoNotPerformCheck() {

        // given
        every {onOffTimes.scheduledDisarmTime} returns HoursAndMins(11, 0)
        every {onOffTimes.scheduledArmTime} returns HoursAndMins(12, 0)

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