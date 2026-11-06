package com.training.trackplanner.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the entire auth session (tokens, email and account ID) using a
 * non-exportable Android Keystore AES-256-GCM key. Never include this blob in
 * Android backup or canonical data export. All ciphertext is authenticated.
 */
internal interface CloudSessionCipher {
    fun encrypt(cleartext: String): String
    fun decrypt(ciphertext: String): String
}

internal object CloudSessionCipherProvider : CloudSessionCipher {
    private val native = AndroidKeystoreSessionCipher()

    // Package-internal test seam. It is never set by production code.
    @Volatile internal var testOverride: CloudSessionCipher? = null

    override fun encrypt(cleartext: String) =
        (testOverride ?: native).encrypt(cleartext)

    override fun decrypt(ciphertext: String) =
        (testOverride ?: native).decrypt(ciphertext)
}

internal class AndroidKeystoreSessionCipher : CloudSessionCipher {
    private companion object {
        const val KEY_ALIAS = "whatyougottado.auth.session.aesgcm.v1"
        const val PREFIX = "aesgcm:v1:"
        const val AAD = "whatyougottado/cloud_auth_session/v1"
    }

    @Synchronized
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore")
        store.load(null)
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    override fun encrypt(cleartext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(AAD.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        require(iv.size == 12) { "Unexpected GCM nonce size" }
        val encrypted = cipher.doFinal(cleartext.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    override fun decrypt(ciphertext: String): String {
        require(ciphertext.startsWith(PREFIX)) { "Unsupported auth storage version" }
        val bytes = Base64.decode(ciphertext.removePrefix(PREFIX), Base64.NO_WRAP)
        require(bytes.size >= 12 + 16) { "Invalid encrypted auth session" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(AAD.toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
}
