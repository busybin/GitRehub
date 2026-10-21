package io.github.busybin.gitrehub.core.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES/GCM with a key that never leaves the Android Keystore.
 *
 * The account file is small, so a single encrypt/decrypt pair is enough; no dependency on
 * `androidx.security` (whose `EncryptedSharedPreferences` is deprecated and would pull Tink in).
 *
 * Decryption fails after a Keystore reset (factory reset, restored backup, key invalidated) — the
 * caller treats that as "no accounts", which signs the user out instead of crashing at launch.
 */
internal object KeystoreCrypto {

    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "gitrehub.accounts"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun encrypt(plain: ByteArray): ByteArray? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.iv + cipher.doFinal(plain)
    }.getOrNull()

    fun decrypt(data: ByteArray): ByteArray? = runCatching {
        require(data.size > IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES))
        cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES)
    }.getOrNull()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply { init(spec) }
            .generateKey()
    }
}
