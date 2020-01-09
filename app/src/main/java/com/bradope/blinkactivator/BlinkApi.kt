package com.bradope.blinkactivator

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

class DefaultHttpResponseReader: HttpResponseReader{
    override fun authToken(response: HttpResponse): String = (response.jsonObject["authtoken"] as JSONObject)["authtoken"] as String
    override fun networks(response: HttpResponse): String = (response.jsonObject["networks"] as JSONObject).keys().next()
    override fun armState(response: HttpResponse): String = ((response.jsonObject["devices"] as JSONArray)[0] as JSONObject)["active"] as String
    override fun statusId(response: HttpResponse): String = response.jsonObject["id"] as String
    override fun isCommandComplete(response: HttpResponse): Boolean = response.jsonObject["complete"] as Boolean
    override fun commandCompletionStatus(response: HttpResponse): String = response.jsonObject["status_msg"] as String
}

interface HttpGetter {
    fun get(url: String, headers: Map<String, String>, data: String, timeout: Double): HttpResponse
}

interface CredentialsDecrypter {
    fun decrypt(encryptedValue: EncryptedValue): String
}

class DefaultCredentialsDecrypter: CredentialsDecrypter {
    override fun decrypt(encryptedValue: EncryptedValue): String = decrypt(encryptedValue)
}

class KHttpGetter: HttpGetter {
    override fun get(
        url: String,
        headers: Map<String, String>,
        data: String,
        timeout: Double
    ): HttpResponse {
        val response = khttp.get(url=url, headers=headers, data=data, timeout=timeout)
        return HttpResponse(response.statusCode, response.jsonObject)
    }
}

open class BlinkApi(
    val httpGetter: HttpGetter = KHttpGetter(),
    val httpResponseReader: HttpResponseReader = DefaultHttpResponseReader(),
    val decrypter: CredentialsDecrypter = DefaultCredentialsDecrypter())
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
                    token = httpResponseReader.authToken(res) ,
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
        // curl -H "Host: prod.immedia-semi.com" -H "TOKEN_AUTH: a106YpSl5bAmdgLPKMcdrg" --compressed https://rest.prde.immedia-semi.com/network/25948/syncmodules
        // curl -H "Host: prod.immedia-semi.com" -H "TOKEN_AUTH: a106YpSl5bAmdgLPKMcdrg" --compressed https://rest.prde.immedia-semi.com/homescreen
        //curl -H "Host: prod.immedia-semi.com" -H "TOKEN_AUTH: a106YpSl5bAmdgLPKMcdrg" --compressed https://rest.prde.immedia-semi.com/homescreen
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
            url = "https://rest.prod.immedia-semi.com/network/@networkId/command/@commandId"
                .replace("@networkId", currentSession!!.network)
                .replace("@commandId", id),
            headers = makeHeadersWithAuth(currentSession!!),
            data = createBody(currentSession!!.credentials),
            timeout = BLINK_API_CALL_TIMEOUT
        )

        // "complete":true,"status":0,"status_msg":"Command succeeded"
        if (httpResponseReader.isCommandComplete(checkResult)) {
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
            .replace("@email", decrypter.decrypt(credentials.email))
            .replace("@pass", decrypter.decrypt(credentials.pass))

    private fun makeHeadersWithAuth(session: BlinkApiSession) = requiredHeaders() + mapOf(
        "TOKEN_AUTH" to session.token
    )

    private fun makeNetworkSpecificEndpointUrl(endpointName: String, session: BlinkApiSession) =
        "https://rest.prde.immedia-semi.com/network/@network_key/@endpointName"
            .replace("@network_key", session.network)
            .replace("@endpointName", endpointName)
}

/* ➜  BlinkActivator git:(master) ✗ curl -H "Host: prod.immedia-semi.com" -H "TOKEN_AUTH: a106YpSl5bAmdgLPKMcdrg" --compressed https://rest.prde.immedia-semi.com/network/25948/syncmodules
{"syncmodule":{"id":49975,"created_at":"2018-05-06T06:32:39+00:00","updated_at":"2020-01-11T00:01:57+00:00","last_activity":"2020-01-11","name":"My Blink Sync Module","fw_version":"2.13.16","mac_address":null,"ip_address":"5.67.81.45","lfr_frequency":null,"serial":"201302110","status":"online","onboarded":true,"server":"i-0ba9ef658949ebf8c","last_hb":"2020-01-11T22:02:37+00:00","os_version":"3.1.6","last_wifi_alert":null,"wifi_alert_count":0,"last_offline_alert":"2019-11-19T02:44:47+00:00","offline_alert_count":8,"table_update_sequence":1578337000,"feature_plan_id":null,"account_id":20928,"network_id":25948,"wifi_strength":5}}%     ➜  BlinkActivator git:(master) ✗ curl -H "Host: prod.immedia-semi.com" -H "TOKEN_AUTH: a106YpSl5bAmdgLPKMcdrg" --compressed https://rest.prde.immedia-semi.com/homescreen
{"account":{"notifications":1},"network":{"name":"Home","wifi_strength":5,"status":"ok","armed":false,"notifications":1,"warning":0,"enable_temp_alerts":true,"error_msg":""},"devices":[{"device_type":"camera","device_id":76027,"type":"xt","updated_at":"2020-01-11T21:53:38+00:00","name":"Front","thumbnail":"/media/production/account/20928/network/25948/camera/76027/clip_fmR8FjOV_2020_01_07__11_47AM","active":"disarmed","notifications":1,"warning":0,"error_msg":"","status":"done","enabled":true,"armed":false,"errors":0,"wifi_strength":3,"lfr_strength":5,"temp":49,"battery":3,"battery_state":"ok","usage_rate":false},{"device_type":"camera","device_id":76029,"type":"xt","updated_at":"2020-01-11T21:53:39+00:00","name":"Side","thumbnail":"/media/production/account/20928/network/25948/camera/76029/clip_xlA0UrW4_2020_01_07__11_47AM","active":"disarmed","notifications":1,"warning":0,"error_msg":"","status":"done","enabled":true,"armed":false,"errors":0,"wifi_strength":1,"lfr_strength":3,"temp":50,"battery":3,"battery_state":"ok","usage_rate":false},{"device_type":"camera","device_id":117344,"type":"xt","updated_at":"2020-01-11T21:53:39+00:00","name":"Inside","thumbnail":"/media/production/account/20928/network/25948/camera/117344/clip_whn_TaIZ_2020_01_03__15_30PM","active":"disarmed","notifications":1,"warning":0,"error_msg":"","status":"done","enabled":true,"armed":false,"errors":0,"wifi_strength":5,"lfr_strength":3,"temp":68,"battery":3,"battery_state":"ok","usage_rate":false},{"device_type":"camera","device_id":117348,"type":"xt","updated_at":"2020-01-11T21:53:39+00:00","name":"Back","thumbnail":"/media/production/account/20928/network/25948/camera/117348/clip_Bmi6i2I__2020_01_07__11_47AM","active":"disarmed","notifications":1,"warning":0,"error_msg":"","status":"done","enabled":true,"armed":false,"errors":0,"wifi_strength":5,"lfr_strength":5,"temp":50,"battery":3,"battery_state":"ok","usage_rate":false},{"device_type":"sync_module","device_id":49975,"updated_at":"2020-01-11T00:01:57+00:00","notifications":0,"warning":0,"error_msg":"","status":"online","errors":0,"last_hb":"2020-01-11T22:02:37+00:00"}]}%
*/
