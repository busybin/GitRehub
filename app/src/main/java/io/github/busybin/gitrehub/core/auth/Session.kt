package io.github.busybin.gitrehub.core.auth

import io.github.busybin.gitrehub.core.model.User
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.core.offline.OfflineSaves
import io.github.busybin.gitrehub.core.offline.RepoPacks
import io.github.busybin.gitrehub.helper.LocalAccountTraces
import io.github.busybin.gitrehub.helper.PrefGetter
import io.github.busybin.gitrehub.provider.rest.HttpCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the account switcher shows: the account in use plus the other stored ones. */
data class StoredAccounts(
    val current: StoredAccount?,
    val others: List<StoredAccount>,
    /** Enterprise accounts are kept (so a switch back home does not lose them) but cannot be driven. */
    val enterpriseCount: Int,
) {
    val isEmpty: Boolean get() = current == null && others.isEmpty()
}

/**
 * The signed-in session: which account, which token, and the transitions between accounts.
 *
 * Tokens live in [TokenStore] (encrypted file); this object only decides which one is current and
 * keeps the in-memory `PrefGetter.token` mirror in step (it is what the legacy OkHttp interceptors
 * read; it is never written to disk).
 */
object Session {

    private val _accountRevision = MutableStateFlow(0)

    /** Bumped when the stored account changes (profile edit, login, switch), so the drawer reloads. */
    val accountRevision: StateFlow<Int> = _accountRevision.asStateFlow()

    val isLoggedIn: Boolean get() = TokenStore.current() != null

    /** `PATCH /user` needs the `user` scope; an unknown scope list (fine-grained token) tries anyway. */
    val canEditProfile: Boolean
        get() = TokenStore.scopes().let { it.isEmpty() || it.contains("user") }

    suspend fun currentAccount(): StoredAccount? = TokenStore.current()

    suspend fun accounts(): StoredAccounts {
        val all = TokenStore.accounts()
        val current = TokenStore.current()
        return StoredAccounts(
            current = current,
            others = all.filter { !it.isEnterprise && !it.login.equals(current?.login, true) },
            enterpriseCount = all.count { it.isEnterprise && !it.login.equals(current?.login, true) },
        )
    }

    /** `GET /user` for the account in use, stored back so the drawer/profile show fresh fields. */
    suspend fun remoteUser(): User {
        val response = GitHubApi.currentUser()
        val user = response.value
        require(user.login.isNotBlank()) { "GET /user returned no login" }
        storeRemoteUser(user, response.scopes)
        return user
    }

    /**
     * Writes fresh profile data into the stored account, keeping the token. Returns the same user,
     * so callers can render it right away.
     */
    suspend fun storeRemoteUser(user: User, scopes: Set<String>? = null): User {
        TokenStore.updateProfile(user.login, user.name, user.avatarUrl, scopes?.toList())
        _accountRevision.value = _accountRevision.value + 1
        return user
    }

    /**
     * Signs in with a personal access token. The token is validated **before** it is stored: a
     * failed validation used to leave the app "signed in" with no account, so every request came
     * back unauthorized until the next launch.
     */
    suspend fun loginWithToken(token: String): User {
        val clean = token.trim()
        val response = GitHubApi.currentUser(clean)
        val user = response.value
        require(user.login.isNotBlank()) { "GET /user returned no login" }

        val account = StoredAccount(
            id = user.id,
            login = user.login,
            name = user.name,
            avatarUrl = user.avatarUrl,
            token = clean,
            scopes = response.scopes?.toList().orEmpty(),
        )
        val previous = TokenStore.current()?.login
        TokenStore.upsert(account, makeCurrent = true)
        if (previous != null && previous != account.login) clearOfflineCopies(previous)
        HttpCache.clear()
        PrefGetter.token = account.token
        _accountRevision.value = _accountRevision.value + 1
        return user
    }

    /**
     * Adopts the pre-rewrite account (the token the old build left in MMKV) once, by asking GitHub
     * whose token it is. Best-effort: offline on first launch just means no import and a normal
     * login screen.
     */
    suspend fun importLegacyAccount(): Boolean {
        if (TokenStore.current() != null) return false
        val token = TokenStore.legacyToken() ?: return false
        val response = runCatching { GitHubApi.currentUser(token) }.getOrNull() ?: return false
        val user = response.value
        if (user.login.isBlank()) return false
        val account = StoredAccount(
            id = user.id,
            login = user.login,
            name = user.name,
            avatarUrl = user.avatarUrl,
            token = token,
            scopes = response.scopes?.toList().orEmpty(),
        )
        TokenStore.upsert(account, makeCurrent = true)
        TokenStore.forgetLegacyToken()
        HttpCache.clear()
        PrefGetter.token = token
        _accountRevision.value = _accountRevision.value + 1
        return true
    }

    /**
     * Drops a token with no usable account behind it (a failed login of the old build could leave
     * exactly that), and re-seeds the in-memory token from the encrypted store on every launch.
     */
    suspend fun repairSession() {
        val current = TokenStore.current()
        if (current == null) {
            if (PrefGetter.token != null) PrefGetter.clearCredentials()
            return
        }
        if (current.token.isBlank()) {
            logout()
            return
        }
        PrefGetter.token = current.token
    }

    /**
     * Switches accounts. The disk cache must go with it: OkHttp keeps one entry per URL, so pages
     * of the account just left must not be answerable for the one being entered.
     */
    suspend fun switchTo(login: String): Boolean {
        val previous = TokenStore.current()?.login
        if (!TokenStore.setCurrent(login)) return false
        previous?.let { clearOfflineCopies(it) }
        HttpCache.clear()
        PrefGetter.token = TokenStore.current()?.token
        _accountRevision.value = _accountRevision.value + 1
        return true
    }

    /** Forgets a stored account; the current one can only be left through [logout]. */
    suspend fun removeAccount(login: String): Boolean {
        clearOfflineCopies(login)
        val removed = TokenStore.remove(login)
        // Removing the last account leaves the app signed out, and logout() will never run: wipe
        // the browsing traces here so "no accounts left" really means "nothing readable left".
        if (removed && TokenStore.accounts().isEmpty()) {
            runCatching { LocalAccountTraces.clear() }
            runCatching { OfflineSaves.clearAll() }
            runCatching { RepoPacks.clearAll() }
        }
        return removed
    }

    /** Signs out: the account leaves the encrypted store and the in-memory token with it. */
    suspend fun logout() {
        val previous = TokenStore.current()?.login
        previous?.let { clearOfflineCopies(it) }
        // Signing out wipes the browsing traces too: their keys name private repositories.
        runCatching { LocalAccountTraces.clear() }
        HttpCache.clear()
        TokenStore.clearCurrent()
        // Signing out of the last account leaves no readable copy anywhere on the device.
        if (TokenStore.accounts().isEmpty()) runCatching { OfflineSaves.clearAll() }
        PrefGetter.clearCredentials()
        _accountRevision.value = _accountRevision.value + 1
    }

    /**
     * Saved threads are private content, so they never outlive the account that saved them — even
     * when the user only switches to another account for a minute.
     *
     * Browsing traces are handled separately: they are wiped on sign-out ([logout]) but kept across
     * switches, because they are what keeps already-read threads from turning unread again.
     */
    private fun clearOfflineCopies(login: String) {
        runCatching { OfflineSaves.clearAccount(login) }
        runCatching { RepoPacks.clearAccount(login) }
    }
}
