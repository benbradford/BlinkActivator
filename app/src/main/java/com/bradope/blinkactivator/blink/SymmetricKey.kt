package com.bradope.blinkactivator.blink

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.Key
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec

const val CIPHER_AES = "AES/CBC/PKCS7Padding"

data class EncryptedValue(val key: Key, val cipher: ByteArray, val iv: IvParameterSpec) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedValue

        if (key != other.key) return false
        if (!cipher.contentEquals(other.cipher)) return false
        if (iv != other.iv) return false

        return true
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + cipher.contentHashCode()
        result = 31 * result + iv.hashCode()
        return result
    }
}

fun generateSymmetricKey(keyAlias: String): Key {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    return makeOrGetKey(keyAlias, keyStore)
}

fun encrypt(plainText: String, key: Key): EncryptedValue? {
    return try {
        val cipher = Cipher.getInstance(CIPHER_AES)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        EncryptedValue(key, cipherText, IvParameterSpec(cipher.iv))
    } catch (exception: GeneralSecurityException) {
        null
    }
}

fun decrypt(value: EncryptedValue): String? {
    val cipher = Cipher.getInstance(CIPHER_AES)
    cipher.init(Cipher.DECRYPT_MODE, value.key, value.iv)
    val plainText = cipher.doFinal(value.cipher)
    return String(plainText!!, Charsets.UTF_8)
}

private fun makeOrGetKey(keyAlias: String, keyStore: KeyStore): Key {
    return if (!keyStore.containsAlias(keyAlias)) {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val keyGenParameterSpec =
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .build()
        keyGenerator.init(keyGenParameterSpec)
        keyGenerator.generateKey()
    } else {
        keyStore.getKey(keyAlias, null)
    }
}
