package com.bradope.blinkactivator

import android.location.Location
import com.bradope.blinkactivator.blink.*
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.Before
import org.junit.Test

class BlinkRequestHandlerTest {

    val settings = BlinkSettings()

    @MockK
    lateinit var credentials: Credentials

    @MockK
    lateinit var api: BlinkApi

    @MockK
    lateinit var tracker: LocationStateTracker

    @MockK
    lateinit var blinkAccessGuard: BlinkAccessGuard

    @Before
    fun before() {
        MockKAnnotations.init(this)
        every {blinkAccessGuard.canAccessBlink()} returns true
        every {api.getSchedule()} returns BlinkDailySchedule(null, null)
    }

    @Test
    fun canInitialise() {
        // given
        every { api.register(credentials )} returns true
        every { api.getArmState() } returns BlinkArmState.ARMED
        var listener = mockk<BlinkListener>(relaxed = true)

        // when
        BlinkRequestHandler(
            credentials = credentials,
            blinkApi = api,
            tracker = tracker,
            blinkAccessGuard = blinkAccessGuard,
            listener = listener,
            blinkSettings = settings
        ).begin()

        // then
        verify { listener.onRegister(true) }
        verify { listener.onStatusRefresh(BlinkArmState.ARMED) }
    }

    @Test
    fun canDisarmWhenLeaveHouseOnThreeDataPoints() {
        // given
        var listener = mockk<BlinkListener>(relaxed = true)
        var location = mockk<Location>()
        every { api.register(credentials )} returns true
        every { api.getArmState() } returns BlinkArmState.DISARMED andThen BlinkArmState.ARMED
        every { api.arm() } returns true
        every { tracker.getLocationStateForLocation( location) } returns LocationStateTracker.LocationState.OUT

        // when
        val handler = BlinkRequestHandler(
            credentials = credentials,
            blinkApi = api,
            tracker = tracker,
            blinkAccessGuard = blinkAccessGuard,
            listener = listener,
            blinkSettings = settings
        )
        handler.begin()
        handler.newLocation(location)
        handler.newLocation(location)
        handler.newLocation(location)
        handler.pollRequestQueue()
        handler.pollRequestQueue()
        handler.pollRequestQueue()

        // then
        verify { listener.onStatusRefresh(BlinkArmState.ARMED) }
    }

    @Test
    fun willNotDisarmOnLessThanThreeLeaveDataPoints() {
        // given
        var listener = mockk<BlinkListener>(relaxed = true)
        var location = mockk<Location>()
        every { api.register(credentials )} returns true
        every { api.getArmState() } returns BlinkArmState.DISARMED andThen BlinkArmState.ARMED
        every { api.arm() } returns true
        every { tracker.getLocationStateForLocation( location) } returns LocationStateTracker.LocationState.OUT

        // when
        val handler = BlinkRequestHandler(
            credentials = credentials,
            blinkApi = api,
            tracker = tracker,
            blinkAccessGuard = blinkAccessGuard,
            listener = listener,
            blinkSettings = settings
        )
        handler.begin()
        handler.newLocation(location)
        handler.newLocation(location)
        handler.pollRequestQueue()
        handler.pollRequestQueue()

        // then
        verify (exactly = 0) { listener.onStatusRefresh(BlinkArmState.ARMED) }
    }

    @Test
    fun ifNotLeftHouseThenDoNotArm() {
        // given
        var listener = mockk<BlinkListener>(relaxed = true)
        var location = mockk<Location>()
        every { api.register(credentials )} returns true
        every { api.getArmState() } returns BlinkArmState.DISARMED
        every { api.arm() } returns true
        every { tracker.getLocationStateForLocation( location) } returns LocationStateTracker.LocationState.AT_HOME

        // when
        val handler = BlinkRequestHandler(
            credentials = credentials,
            blinkApi = api,
            tracker = tracker,
            blinkAccessGuard = blinkAccessGuard,
            listener = listener,
            blinkSettings = settings
        )
        handler.begin()
        handler.newLocation(location)

        handler.pollRequestQueue()

        // then
        verify { listener.onStatusRefresh(BlinkArmState.DISARMED) }

    }

    @Test
    fun inThenOutThenReturn() {
        // given
        var listener = mockk<BlinkListener>(relaxed = true)
        var location = mockk<Location>()
        every { api.register(credentials )} returns true
        every { api.getArmState() } returns BlinkArmState.DISARMED andThen BlinkArmState.ARMED andThen BlinkArmState.ARMED andThen BlinkArmState.DISARMED
        every { api.disarm() } returns true
        every { api.arm() } returns true
        every { tracker.getLocationStateForLocation( location) } returns LocationStateTracker.LocationState.AT_HOME andThen LocationStateTracker.LocationState.OUT andThen LocationStateTracker.LocationState.AT_HOME

        // when
        val handler = BlinkRequestHandler(
            credentials = credentials,
            blinkApi = api,
            tracker = tracker,
            blinkAccessGuard = blinkAccessGuard,
            listener = listener,
            blinkSettings = settings
        )
        handler.begin()
        handler.newLocation(location)
        handler.newLocation(location)
        handler.newLocation(location)
        handler.pollRequestQueue()
        handler.pollRequestQueue()
        handler.pollRequestQueue()

        // then
        verify { listener.onStatusRefresh(BlinkArmState.DISARMED) }
    }

    @Test
    fun willNotCallStatusUpdateIfCanNotArmWithinThreeAttempts() {
        // given
        var listener = mockk<BlinkListener>(relaxed = true)
        var location = mockk<Location>()
        every { api.register(credentials )} returns true
        every { api.getArmState() } returns BlinkArmState.DISARMED
        every { api.arm() } returns false andThen false andThen false andThen false andThen true
        every { tracker.getLocationStateForLocation( location) } returns LocationStateTracker.LocationState.OUT

        // when
        val handler = BlinkRequestHandler(
            credentials = credentials,
            blinkApi = api,
            tracker = tracker,
            blinkAccessGuard = blinkAccessGuard,
            listener = listener,
            blinkSettings = settings
        )
        handler.begin()
        handler.newLocation(location)
        handler.pollRequestQueue()

        // then
        verify { listener.onStatusRefresh(BlinkArmState.DISARMED)  }
        verify(exactly = 0) { listener.onStatusRefresh(BlinkArmState.ARMED) }
    }

    @Test
    fun willRetryThreeTimesWhenArmfails() {
        // given
        var listener = mockk<BlinkListener>(relaxed = true)
        var location = mockk<Location>()
        every { api.register(credentials )} returns true
        every { api.getArmState() } returns BlinkArmState.DISARMED
        every { api.arm() } returns false andThen false andThen false andThen true
        every { tracker.getLocationStateForLocation( location) } returns LocationStateTracker.LocationState.OUT

        // when
        val handler = BlinkRequestHandler(
            credentials = credentials,
            blinkApi = api,
            tracker = tracker,
            blinkAccessGuard = blinkAccessGuard,
            listener = listener,
            blinkSettings = settings
        )
        handler.begin()

        handler.newLocation(location)
        handler.newLocation(location)
        handler.newLocation(location)

        handler.pollRequestQueue()
        handler.pollRequestQueue()
        handler.pollRequestQueue()

        // then
        verify(exactly = 1) { listener.onStatusRefresh(BlinkArmState.DISARMED)  }
        verify(exactly = 1) { listener.onStatusRefresh(BlinkArmState.ARMED) }
    }

}