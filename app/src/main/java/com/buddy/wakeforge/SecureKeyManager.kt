package com.buddy.wakeforge

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Owns the database encryption passphrase.
 *
 * Design (this is the "no plaintext anywhere" part of the security ask):
 *  1. A 256-bit random passphrase is generated once, on first app launch.
 *  2. It's stored inside EncryptedSharedPreferences, which is itself encrypted
 *     with a MasterKey backed by the device's Android Keystore — on most
 *     devices that means the actual key material lives in a hardware secure
 *     element and is never extractable, even with root.
 *  3. The passphrase is only ever held in memory long enough to open the
 *     SQLCipher database (see AppDatabase.kt) — it is never logged, never
 *     written anywhere in plaintext, and never leaves the device.
 *
 * This gives real encryption-at-rest for local data. It is deliberately NOT
 * called "end-to-end encryption" — E2EE is a client-server concept (it
 * describes data encrypted so that even the server relaying it can't read
 * it). This app has no server yet. See SECURITY.md for what to add when
 * cloud sync is introduced.
 */
object SecureKeyManager {

    private const val PREFS_FILE_NAME = "wakeforge_secure_prefs"
    private const val KEY_DB_PASSPHRASE = "db_passphrase_b64"
    private const val PASSPHRASE_BYTES = 32 // 256-bit

    fun getOrCreateDatabasePassphrase(context: Context): ByteArray {
        val prefs = encryptedPrefs(context)

        prefs.getString(KEY_DB_PASSPHRASE, null)?.let {
            return Base64.decode(it, Base64.NO_WRAP)
        }

        val newPassphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_DB_PASSPHRASE, Base64.encodeToString(newPassphrase, Base64.NO_WRAP))
            .apply()
        return newPassphrase
    }

    private fun encryptedPrefs(context: Context) : android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
