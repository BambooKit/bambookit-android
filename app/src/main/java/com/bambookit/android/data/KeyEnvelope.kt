package com.bambookit.android.data

import kotlinx.serialization.Serializable
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.KeyFactory
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/**
 * A provider API key encrypted end to end for one PC (SET_PROVIDER_KEY). Only that PC's RSA private key
 * opens it; the BambooKit API only ever sees this ciphertext.
 *
 *   alg  = "RSA-OAEP-256+A256GCM"
 *   key  = base64 RSA-OAEP (SHA-256, MGF1-SHA-256) of a random 32-byte AES key
 *   iv   = base64 12 random bytes
 *   data = base64 AES-256-GCM ciphertext of the UTF-8 JSON {"apiKey":"..."} with the 16-byte tag appended
 */
@Serializable
data class KeyEnvelope(val alg: String, val key: String, val iv: String, val data: String) {
    /** Never print the ciphertext either. */
    override fun toString(): String = "KeyEnvelope(alg=$alg)"
}

object KeyEnvelopes {
    const val ALG = "RSA-OAEP-256+A256GCM"

    val oaep: OAEPParameterSpec = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)

    /** Reads an RSA public key from an SPKI PEM ("-----BEGIN PUBLIC KEY-----"). */
    fun publicKey(pem: String): PublicKey {
        val body = pem.replace(Regex("-----(BEGIN|END) PUBLIC KEY-----"), "").replace(Regex("\\s"), "")
        require(body.isNotEmpty()) { "The PC's encryption key is empty" }
        return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(body)))
    }

    /**
     * Encrypts [apiKey] for the PC whose public key is [pem]. Every intermediate buffer holding the key or
     * the AES key is zeroed before returning; the caller should clear [apiKey] afterwards.
     */
    fun seal(apiKey: CharArray, pem: String, random: SecureRandom = SecureRandom()): KeyEnvelope {
        val aes = ByteArray(32).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val plain = jsonApiKey(apiKey)
        try {
            val rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
            rsa.init(Cipher.ENCRYPT_MODE, publicKey(pem), oaep)
            val wrapped = rsa.doFinal(aes)
            val gcm = Cipher.getInstance("AES/GCM/NoPadding")
            gcm.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aes, "AES"), GCMParameterSpec(128, iv))
            val data = gcm.doFinal(plain)
            val enc = Base64.getEncoder()
            return KeyEnvelope(ALG, enc.encodeToString(wrapped), enc.encodeToString(iv), enc.encodeToString(data))
        } finally {
            aes.fill(0)
            plain.fill(0)
        }
    }

    /** UTF-8 bytes of {"apiKey":"<escaped key>"} built without an intermediate String. */
    internal fun jsonApiKey(apiKey: CharArray): ByteArray {
        val chars = CharArray(apiKey.size * 6 + 14)
        var n = 0
        fun put(c: Char) { chars[n++] = c }
        "{\"apiKey\":\"".forEach(::put)
        for (c in apiKey) {
            when {
                c == '"' -> { put('\\'); put('"') }
                c == '\\' -> { put('\\'); put('\\') }
                c < ' ' -> "\\u%04x".format(c.code).forEach(::put)
                else -> put(c)
            }
        }
        put('"'); put('}')
        val buf: ByteBuffer = Charsets.UTF_8.encode(CharBuffer.wrap(chars, 0, n))
        val out = ByteArray(buf.remaining()).also { buf.get(it) }
        chars.fill('\u0000')
        if (buf.hasArray()) buf.array().fill(0)
        return out
    }
}
