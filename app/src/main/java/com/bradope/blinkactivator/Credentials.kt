package com.bradope.blinkactivator

const val BLINK_CREDENTIALS_KEY = "blink-cred"

data class Credentials(val email: EncryptedValue, val pass: EncryptedValue)

fun createCredentials(email: String, pass: String): Credentials {
    val key = generateSymmetricKey(BLINK_CREDENTIALS_KEY)
    val emailEncrypted = encrypt(email, key)
    val passEncrypted = encrypt(pass, key)

    return Credentials(emailEncrypted!!, passEncrypted!!)
}
