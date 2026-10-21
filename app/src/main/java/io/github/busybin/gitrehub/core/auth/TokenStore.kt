package io.github.busybin.gitrehub.core.auth

import android.content.Context
import io.github.busybin.gitrehub.helper.PrefGetter
import io.github.busybin.gitrehub.helper.PrefHelper
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * One stored account. The token is part of this object because the file it lives in is encrypted
 * as a whole (see [KeystoreCrypto]); it is never written anywhere else.
 */
@Serializable
data class StoredAccount(
    val id: Long = 0,
    val login: String,
    val name: String? = null,
    val avatarUrl: String? = null,
    val token: String,
    val scopes: List<String> = emptyList(),
    /** GitHub Enterprise accounts are stored (so they are not lost) but cannot be driven by this UI. */
    val isEnterprise: Boolean = false,
)

@Serializable
internal data class AccountFile(
    val current: String? = null,
    val accounts: List<StoredAccount> = emptyList(),
)

/**
 * The account list and the token in use, encrypted at rest.
 *
 * This is the only place a token is persisted. `PrefGetter.token` is an in-memory mirror for the
 * OkHttp interceptors and never reaches disk.
 *
 * One-time migration: [Session.importLegacyAccount] adopts the token the pre-rewrite build left in
 * MMKV (see [legacyToken]); this object only exposes it and removes it again.
 */
object TokenStore {

    private const val FILE_NAME = "accounts.bin"
    private const val LEGACY_TOKEN_KEY = "token"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Volatile
    private var state: AccountFile? = null

    @Volatile
    private var file: File? = null

    /** Loads the file once, imports the legacy accounts on first run, and seeds the token mirror. */
    @Synchronized
    fun init(context: Context) {
        if (state != null) return
        file = File(context.applicationContext.filesDir, FILE_NAME)
        state = readFile()
        seedTokenMirror()
    }

    fun current(): StoredAccount? {
        val snapshot = state ?: return null
        return snapshot.accounts.firstOrNull { it.login.equals(snapshot.current, ignoreCase = true) }
    }

    fun accounts(): List<StoredAccount> = state?.accounts.orEmpty()

    fun token(): String? = current()?.token

    fun scopes(): List<String> = current()?.scopes.orEmpty()

    /** Adds or replaces an account. [makeCurrent] also updates the legacy token mirror. */
    @Synchronized
    fun upsert(account: StoredAccount, makeCurrent: Boolean) {
        val snapshot = state ?: AccountFile()
        val others = snapshot.accounts.filterNot { it.login.equals(account.login, ignoreCase = true) }
        val next = AccountFile(
            current = if (makeCurrent) account.login else snapshot.current,
            accounts = others + account,
        )
        state = next
        persist(next)
        seedTokenMirror()
    }

    @Synchronized
    fun updateProfile(login: String, name: String?, avatarUrl: String?, scopes: List<String>?) {
        val snapshot = state ?: return
        val updated = snapshot.accounts.map { account ->
            if (!account.login.equals(login, ignoreCase = true)) account
            else account.copy(
                name = name ?: account.name,
                avatarUrl = avatarUrl ?: account.avatarUrl,
                scopes = scopes ?: account.scopes,
            )
        }
        val next = snapshot.copy(accounts = updated)
        state = next
        persist(next)
    }

    @Synchronized
    fun setCurrent(login: String): Boolean {
        val snapshot = state ?: return false
        val target = snapshot.accounts.firstOrNull { it.login.equals(login, ignoreCase = true) }
            ?: return false
        if (snapshot.current.equals(target.login, ignoreCase = true)) return true
        val next = snapshot.copy(current = target.login)
        state = next
        persist(next)
        seedTokenMirror()
        return true
    }

    /** Forgets a stored account; the current one has to be left through [clearCurrent]. */
    @Synchronized
    fun remove(login: String): Boolean {
        val snapshot = state ?: return false
        if (snapshot.current.equals(login, ignoreCase = true)) return false
        val remaining = snapshot.accounts.filterNot { it.login.equals(login, ignoreCase = true) }
        if (remaining.size == snapshot.accounts.size) return false
        val next = snapshot.copy(accounts = remaining)
        state = next
        persist(next)
        return true
    }

    /** Signs out: the current account is dropped (other accounts stay stored). */
    @Synchronized
    fun clearCurrent() {
        val snapshot = state ?: return
        val remaining = snapshot.accounts.filterNot {
            it.login.equals(snapshot.current, ignoreCase = true)
        }
        val next = AccountFile(current = null, accounts = remaining)
        state = next
        persist(next)
        PrefGetter.token = null
    }

    /** Drops everything (used by tests and "clear all settings"). */
    @Synchronized
    fun clearAll() {
        state = AccountFile()
        file?.delete()
        PrefGetter.token = null
    }

    private fun readFile(): AccountFile {
        val target = file ?: return AccountFile()
        val bytes = runCatching { target.readBytes() }.getOrNull() ?: return AccountFile()
        val plain = KeystoreCrypto.decrypt(bytes) ?: run {
            // Keystore reset / corrupt file: drop it rather than crash every launch.
            runCatching { target.delete() }
            return AccountFile()
        }
        return runCatching { json.decodeFromString(AccountFile.serializer(), plain.decodeToString()) }
            .getOrDefault(AccountFile())
    }

    private fun persist(snapshot: AccountFile) {
        val target = file ?: return
        val plain = json.encodeToString(AccountFile.serializer(), snapshot).encodeToByteArray()
        val encrypted = KeystoreCrypto.encrypt(plain) ?: return
        runCatching {
            val tmp = File(target.parentFile, "$FILE_NAME.tmp")
            tmp.writeBytes(encrypted)
            if (!tmp.renameTo(target)) {
                target.writeBytes(encrypted)
                tmp.delete()
            }
        }
    }

    private fun seedTokenMirror() {
        PrefGetter.token = current()?.token
    }

    /** Reads the pre-rewrite token so a user who was signed in does not have to sign in again. */
    internal fun legacyToken(): String? =
        runCatching { PrefHelper.getString(LEGACY_TOKEN_KEY) }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Drops the plaintext copy once [Session.importLegacyAccount] has adopted it. */
    internal fun forgetLegacyToken() {
        runCatching { PrefHelper.clearKey<String>(LEGACY_TOKEN_KEY) }
    }
}
