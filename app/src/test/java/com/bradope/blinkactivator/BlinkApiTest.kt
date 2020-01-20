package com.bradope.blinkactivator

import com.bradope.blinkactivator.blink.*
import io.mockk.*
import io.mockk.impl.annotations.MockK
import junit.framework.Assert.*
import org.junit.Before
import org.junit.Test

class BlinkApiTest {

    val authToken = "12345"
    val networks = "6789"
    val email = "me@mail.com"
    val password = "password"
    val statusId = "commandId"
    val commandCompleteString= "Command succeeded"
    val reigsterUrl = "https://rest.prod.immedia-semi.com/login"
    val armStateUrl = "https://rest.prde.immedia-semi.com/homescreen"
    val armUrl = "https://rest.prde.immedia-semi.com/network/$networks/arm"
    val disarmUrl = "https://rest.prde.immedia-semi.com/network/$networks/disarm"
    val commandStatusUrl = "https://rest.prde.immedia-semi.com/network/$networks/command/$statusId"

    @MockK
    lateinit var getter: HttpGetter

    @MockK
    lateinit var responseReader: HttpResponseReader

    @MockK
    lateinit var encryptedEmail: EncryptedValue

    @MockK
    lateinit var encryptedPassword: EncryptedValue

    @MockK
    lateinit var credentials: Credentials

    @MockK
    lateinit var decrypter: CredentialsDecrypter

    @Before
    fun before() {
        MockKAnnotations.init(this)
        every {credentials.email}.returns (encryptedEmail)
        every {credentials.pass}.returns (encryptedPassword)
        every { decrypter.decryptValue(encryptedEmail)}.returns (email)
        every { decrypter.decryptValue(encryptedPassword)}.returns(password)
    }

    @Test
    fun canRegister() {
        // given
        //getter setup
        val response = createDefaultRegisterResponse()

        // when
        val api = BlinkApi(getter, responseReader, decrypter)
        api.register(credentials)

        // then
        verify(exactly = 1) { getter.get(any(), any(), any(), any()) }
        assertNotNull(api.getSession())
        assertEquals(authToken, api.getSession()!!.token)
        assertEquals(networks, api.getSession()!!.network)
    }

    @Test
    fun failureToRegisterDoesNotCreateSession() {
        // given
        //getter setup
        val response = mockk<HttpResponse>()

        every {getter.get(reigsterUrl, any(), any(), any())}.returns(response)
        every {response.statusCode}.returns(500)

        // when
        val api = BlinkApi(getter, responseReader, decrypter)
        api.register(credentials)

        // then
        verify(exactly = 1) { getter.get(any(), any(), any(), any()) }
        assertNull(api.getSession())
    }

    @Test
    fun noSessionWillNotCallApi() {
        assertNull(BlinkApi(getter, responseReader, decrypter).getArmState())
        assertFalse(BlinkApi(getter, responseReader, decrypter).arm())
        assertFalse(BlinkApi(getter, responseReader, decrypter).disarm())
    }

    @Test
    fun canGetArmState() {
        // given
        createDefaultRegisterResponse()
        val response = mockk<HttpResponse>()
        every {response.statusCode}.returns(200)
        every {responseReader.armState(response)}.returns("armed")
        every { getter.get(armStateUrl, any(), any(), any())}.returns(response)

        // when
        val api = BlinkApi(getter, responseReader, decrypter)
        api.register(credentials)
        val state = api.getArmState()

        // then
        assertEquals(BlinkArmState.ARMED, state)
        verify (exactly = 1){getter.get(armStateUrl, any(), any(), any())}
    }

    @Test
    fun canGetDisArmedState() {
        // given
        createDefaultRegisterResponse()
        val response = mockk<HttpResponse>()
        every {response.statusCode}.returns(200)
        every {responseReader.armState(response)}.returns("disarmed")
        every { getter.get(armStateUrl, any(), any(), any())}.returns(response)

        // when
        val api = BlinkApi(getter, responseReader, decrypter)
        api.register(credentials)
        val state = api.getArmState()

        // then
        assertEquals(BlinkArmState.DISARMED, state)
        verify (exactly = 1){getter.get(armStateUrl, any(), any(), any())}
    }

    @Test
    fun canGetUnknownArmState() {
        // given
        createDefaultRegisterResponse()
        val response = mockk<HttpResponse>()
        every {response.statusCode}.returns(200)
        every {responseReader.armState(response)}.returns("??")
        every {getter.get(armStateUrl, any(), any(), any())}.returns(response)

        // when
        val api = BlinkApi(getter, responseReader, decrypter)
        api.register(credentials)
        val state = api.getArmState()

        // then
        assertEquals(BlinkArmState.UNKNOWN, state)
        verify (exactly = 1){getter.get(armStateUrl, any(), any(), any())}
    }

    @Test
    fun canArm() {
        // given
        createDefaultRegisterResponse()
        createSuccessfulArmResponse()
        createSuccesfulStatusResponse()

        // when
        val api = BlinkApi(getter, responseReader, decrypter)
        api.register(credentials)
        val result = api.arm()

        // then
        assertTrue(result)
        verify(exactly = 1) {getter.get(armUrl, any(), any(), any())}
        verify(exactly = 1) {getter.get(commandStatusUrl, any(), any(), any())}
    }

    @Test
    fun willReturnFalseIfCommandNotComplete() {
        // given
        createDefaultRegisterResponse()
        createSuccessfulArmResponse()
        val statusResponse = mockk<HttpResponse>()

        every {statusResponse.statusCode}.returns(200)
        every {responseReader.isCommandComplete(statusResponse)}.returns(true)
        every {responseReader.commandCompletionStatus(statusResponse)}.returns("unknown response")
        every {getter.get(commandStatusUrl, any(), any(), any())}.returns(statusResponse)

        // when
        val api = BlinkApi(getter, responseReader, decrypter)
        api.register(credentials)
        val result = api.arm()

        // then
        assertFalse(result)
        verify(exactly = 1) {getter.get(armUrl, any(), any(), any())}
        verify(exactly = 1) {getter.get(commandStatusUrl, any(), any(), any())}
    }

    @Test
    fun willKeepTryingToGetArmResponseThenGiveUp() {
        // given
        createDefaultRegisterResponse()
        createSuccessfulArmResponse()

        val statusResponse = mockk<HttpResponse>()

        every {statusResponse.statusCode}.returns(200)
        every {responseReader.isCommandComplete(statusResponse)}.returns(false)
        every {getter.get(commandStatusUrl, any(), any(), any())}.returns(statusResponse)

        // when
        val api = BlinkApi(getter, responseReader, decrypter)
        api.register(credentials)
        val result = api.arm()

        // then
        assertFalse(result)
        verify(exactly = 1) {getter.get(armUrl, any(), any(), any())}
        verify(exactly = 5) {getter.get(commandStatusUrl, any(), any(), any())}
    }

    @Test
    fun willAcceptIfGetArmResponseOnThirdAttempt() {
        // given
        createDefaultRegisterResponse()
        val armResponse = mockk<HttpResponse>()
        val statusResponse = mockk<HttpResponse>()

        every {armResponse.statusCode}.returns(200)
        every {responseReader.statusId(armResponse)}.returns(statusId)
        every {getter.get(armUrl, any(), any(), any())}.returns(armResponse)

        every {statusResponse.statusCode}.returns(200)
        every {responseReader.isCommandComplete(statusResponse)}.returns(false) andThen false andThen false andThen true
        every {responseReader.commandCompletionStatus(statusResponse)}.returns(commandCompleteString)
        every {getter.get(commandStatusUrl, any(), any(), any())}.returns(statusResponse)

        // when
        val api = BlinkApi(getter, responseReader, decrypter)
        api.register(credentials)
        val result = api.arm()

        // then
        assertTrue(result)
        verify(exactly = 1) {getter.get(armUrl, any(), any(), any())}
        verify(exactly = 4) {getter.get(commandStatusUrl, any(), any(), any())}
    }

    @Test
    fun canDisarm() {
        // given
        createDefaultRegisterResponse()
        createSuccessfulDisarmResponse()
        createSuccesfulStatusResponse()

        // when
        val api = BlinkApi(getter, responseReader, decrypter)
        api.register(credentials)
        val result = api.disarm()

        // then
        assertTrue(result)
        verify(exactly = 1) {getter.get(disarmUrl, any(), any(), any())}
        verify(exactly = 1) {getter.get(commandStatusUrl, any(), any(), any())}
    }

    private fun createDefaultRegisterResponse(): HttpResponse {
        val response = mockk<HttpResponse>()

        every {getter.get(reigsterUrl, any(), any(), any())}.returns(response)
        every {response.statusCode}.returns(200)
        every {responseReader.authToken(response)}.returns(authToken)
        every {responseReader.networks(response)}.returns(networks)

        return response
    }

    private fun createSuccessfulArmResponse(): HttpResponse {
        val armResponse = mockk<HttpResponse>()
        every {armResponse.statusCode}.returns(200)
        every {responseReader.statusId(armResponse)}.returns(statusId)
        every {getter.get(armUrl, any(), any(), any())}.returns(armResponse)
        return armResponse
    }

    private fun createSuccessfulDisarmResponse(): HttpResponse {
        val disarmResponse = mockk<HttpResponse>()
        every {disarmResponse.statusCode}.returns(200)
        every {responseReader.statusId(disarmResponse)}.returns(statusId)
        every {getter.get(disarmUrl, any(), any(), any())}.returns(disarmResponse)
        return disarmResponse
    }

    private fun createSuccesfulStatusResponse(): HttpResponse {
        val statusResponse = mockk<HttpResponse>()

        every {statusResponse.statusCode}.returns(200)
        every {responseReader.isCommandComplete(statusResponse)}.returns(true)
        every {responseReader.commandCompletionStatus(statusResponse)}.returns(commandCompleteString)
        every {getter.get(commandStatusUrl, any(), any(), any())}.returns(statusResponse)

        return statusResponse
    }
}