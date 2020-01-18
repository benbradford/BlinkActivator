package com.bradope.blinkactivator.blink

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

enum class BlinkArmState {
    ARMED,
    DISARMED,
    UNKNOWN
}

data class HttpResponse(val statusCode: Int, val jsonObject: JSONObject)

interface HttpResponseReader {
    fun authToken(response: HttpResponse): String
    fun networks(response: HttpResponse): String
    fun armState(response: HttpResponse): String
    fun statusId(response: HttpResponse): String
    fun isCommandComplete(response: HttpResponse): Boolean
    fun commandCompletionStatus(response: HttpResponse): String
}

class DefaultHttpResponseReader: HttpResponseReader {
    override fun authToken(response: HttpResponse): String = (response.jsonObject["authtoken"] as JSONObject)["authtoken"] as String
    override fun networks(response: HttpResponse): String = (response.jsonObject["networks"] as JSONObject).keys().next()
    override fun armState(response: HttpResponse): String = ((response.jsonObject["devices"] as JSONArray)[0] as JSONObject)["active"] as String
    override fun statusId(response: HttpResponse): String = (response.jsonObject["id"] as Integer).toString()
    override fun isCommandComplete(response: HttpResponse): Boolean = response.jsonObject["complete"] as Boolean
    override fun commandCompletionStatus(response: HttpResponse): String = response.jsonObject["status_msg"] as String
}

interface HttpGetter {
    fun get(url: String, headers: Map<String, String>, data: String, timeout: Double): HttpResponse
}

interface CredentialsDecrypter {
    fun decryptValue(encryptedValue: EncryptedValue): String
}

class DefaultCredentialsDecrypter: CredentialsDecrypter {
    override fun decryptValue(encryptedValue: EncryptedValue): String = decrypt(
        encryptedValue
    )!!
}

class KHttpGetter: HttpGetter {
    override fun get(
        url: String,
        headers: Map<String, String>,
        data: String,
        timeout: Double
    ): HttpResponse {
        val response = khttp.get(url=url, headers=headers, data=data, timeout=timeout)
        if (response.statusCode == 200)
            return HttpResponse(
                response.statusCode,
                response.jsonObject
            )
        return HttpResponse(response.statusCode, JSONObject())
    }
}

open class BlinkApi(
    val httpGetter: HttpGetter = KHttpGetter(),
    val httpResponseReader: HttpResponseReader = DefaultHttpResponseReader(),
    val decrypter: CredentialsDecrypter = DefaultCredentialsDecrypter()
)
{
    data class BlinkApiSession(val token: String, val network: String, val credentials: Credentials)

    private val BLINK_API_CALL_TIMEOUT = 5.0
    private val LOG_TAG = "bradope_log " + BlinkApiSession::class.java.simpleName

    private var currentSession: BlinkApiSession? = null

    fun getSession() = currentSession // for testing

    fun register(credentials: Credentials): Boolean {
        try {
            Log.i(LOG_TAG, "register with blink")
            val res = httpGetter.get(
                url = "https://rest.prod.immedia-semi.com/login",
                headers = requiredHeaders(),
                data = createBody(credentials),
                timeout = BLINK_API_CALL_TIMEOUT
            )
            Log.i(LOG_TAG, "register with blink got res " + res.statusCode)

            if (res.statusCode == 200) {
                currentSession = BlinkApiSession(
                    token = httpResponseReader.authToken(res),
                    network = httpResponseReader.networks(res),
                    credentials = credentials
                )
                return true
            }
            Log.e(LOG_TAG, "registerWithBlink error response: " + res.statusCode)
           return false
        } catch (e: Exception) {
            Log.i(LOG_TAG , "exception " + e)
            return false
        }
    }

    fun getArmState(): BlinkArmState? {
        if (currentSession == null) {
            Log.w(LOG_TAG, "No currentSession in getArmState")
            return null
        }
        val res = httpGetter.get(
            url = "https://rest.prde.immedia-semi.com/homescreen",
            headers = mapOf(
                "Host" to "prod.immedia-semi.com",
                "TOKEN_AUTH" to currentSession!!.token
            ),
            data = "",
            timeout = BLINK_API_CALL_TIMEOUT
        )
        if (res.statusCode != 200) {
            Log.e(LOG_TAG , "getArmState error status code: " + res.statusCode)
            return BlinkArmState.UNKNOWN
        }

        val armString = httpResponseReader.armState(res)
        return when (armString) {
            "armed" -> BlinkArmState.ARMED
            "disarmed" -> BlinkArmState.DISARMED
            else -> {
                Log.e(LOG_TAG , "getArmState unknown state " + armString)
                BlinkArmState.UNKNOWN
            }
        }
    }

    fun arm(): Boolean {
        if (currentSession == null) {
            Log.w(LOG_TAG, "No currentSession in arm")
            return false // :TODO: need to send error
        }
        val status = httpGetter.get(
            url =  makeNetworkSpecificEndpointUrl( "arm", currentSession!!),
            headers = makeHeadersWithAuth(currentSession!!),
            data = createBody(currentSession!!.credentials),
            timeout = BLINK_API_CALL_TIMEOUT
        )

        if (status.statusCode == 200) {
            val id = httpResponseReader.statusId(status)
            return pollCommandStatus(id)

        } else {
            return false
        }
    }
    
    fun disarm(): Boolean {
        if (currentSession == null) {
            Log.w(LOG_TAG, "No currentSession in disarm")
            return false // :TODO: need to send error
        }
        val status = httpGetter.get(
            url =  makeNetworkSpecificEndpointUrl( "disarm", currentSession!!),
            headers = makeHeadersWithAuth(currentSession!!),
            data = createBody(currentSession!!.credentials),
            timeout = BLINK_API_CALL_TIMEOUT
        )

        Log.i(LOG_TAG,"disarmed $status")
        if (status.statusCode == 200) {
            return status.statusCode == 200 && pollCommandStatus(httpResponseReader.statusId(status))
        } else {
            return false
        }
    }

    private fun pollCommandStatus(id: String, maxWaitTimeInSeconds: Long = 1L): Boolean {
        val checkResult = httpGetter.get (
            url = "https://rest.prde.immedia-semi.com/network/@networkId/command/@commandId"
                .replace("@networkId", currentSession!!.network)
                .replace("@commandId", id),
            headers = mapOf(
                "Host" to "prod.immedia-semi.com",
                "TOKEN_AUTH" to currentSession!!.token
            ),
            data = "",
            timeout = BLINK_API_CALL_TIMEOUT
        )

        // "complete":true,"status":0,"status_msg":"Command succeeded"
        if (checkResult.statusCode == 200 && httpResponseReader.isCommandComplete(checkResult)) {
            return httpResponseReader.commandCompletionStatus(checkResult) == "Command succeeded"
        }

        if (maxWaitTimeInSeconds > 5L) {
            return false
        }

        Thread.sleep(maxWaitTimeInSeconds)

        return pollCommandStatus(id, maxWaitTimeInSeconds * 2L)
    }

    private fun requiredHeaders() = mapOf(
        "Host" to "prod.immedia-semi.com",
        "Content-Type" to "application/json"
    )

    private fun createBody(credentials: Credentials) =
        "{ \"email\" : \"@email\", \"password\" : \"@pass\", \"client_specifier\" : \"android | 29 \" }"
            .replace("@email", decrypter.decryptValue(credentials.email))
            .replace("@pass", decrypter.decryptValue(credentials.pass))

    private fun makeHeadersWithAuth(session: BlinkApiSession) = requiredHeaders() + mapOf(
        "TOKEN_AUTH" to session.token
    )

    private fun makeNetworkSpecificEndpointUrl(endpointName: String, session: BlinkApiSession) =
        "https://rest.prde.immedia-semi.com/network/@network_key/@endpointName"
            .replace("@network_key", session.network)
            .replace("@endpointName", endpointName)
}
