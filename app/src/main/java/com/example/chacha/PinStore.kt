package com.example.chacha

import android.content.Context
import java.security.SecureRandom

class PinStore(context: Context) {
    private val prefs = context.getSharedPreferences("pin", Context.MODE_PRIVATE)

    fun hasPin(): Boolean = prefs.contains("hash") && prefs.contains("salt")

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt)
        prefs.edit()
            .putString("salt", salt.joinToString(",") { it.toString() })
            .putString("hash", hash.joinToString(",") { it.toString() })
            .apply()
    }

    fun checkPin(pin: String): Boolean {
        val saltStr = prefs.getString("salt", null) ?: return false
        val hashStr = prefs.getString("hash", null) ?: return false
        val salt = saltStr.split(",").map { it.toByte() }.toByteArray()
        val expected = hashStr.split(",").map { it.toByte() }.toByteArray()
        val actual = pbkdf2(pin, salt)
        return expected.contentEquals(actual)
    }

    private fun pbkdf2(pin: String, salt: ByteArray): ByteArray {
        val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, 10000, 256)
        return javax.crypto.SecretKeyFactory
            .getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
    }
}