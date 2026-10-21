package io.github.busybin.gitrehub.compose

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.auth.StoredAccount
import io.github.busybin.gitrehub.core.auth.TokenStore
import io.github.busybin.gitrehub.helper.PrefGetter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Multi-account behaviour against the real encrypted store (no network involved).
 *
 * The whole point of a switch is that the token every request is authenticated with follows the
 * account — a stored `isLoggedIn` flag alone would show one account's name with another one's data.
 */
@RunWith(AndroidJUnit4::class)
class AccountSwitchTest {

    /** Start from an empty store: the first imported legacy account would otherwise leak in. */
    @Before
    fun setUp() {
        TokenStore.clearAll()
        PrefGetter.clearCredentials()
    }

    @After
    fun tearDown() {
        runBlocking { Session.logout() }
        TokenStore.clearAll()
        PrefGetter.clearCredentials()
    }

    @Test
    fun signingInASecondAccountKeepsTheFirstOne() = runBlocking {
        signIn("acct-a", "token-a")
        signIn("acct-b", "token-b")

        assertEquals("acct-b", Session.currentAccount()?.login)
        val accounts = Session.accounts()
        assertEquals("acct-b", accounts.current?.login)
        assertEquals(listOf("acct-a"), accounts.others.map { it.login })
        assertEquals(0, accounts.enterpriseCount)
    }

    @Test
    fun switchingMovesTheStoredToken() = runBlocking {
        signIn("acct-a", "token-a")
        signIn("acct-b", "token-b")

        assertTrue(Session.switchTo("acct-a"))
        // the token is what actually reaches the API — this is the assertion that matters
        assertEquals("token-a", PrefGetter.token)
        assertEquals("acct-a", Session.currentAccount()?.login)

        val accounts = Session.accounts()
        assertEquals("acct-a", accounts.current?.login)
        assertEquals(listOf("acct-b"), accounts.others.map { it.login })

        // switching back works too, and switching to the current account is a no-op
        assertTrue(Session.switchTo("acct-b"))
        assertTrue(Session.switchTo("acct-b"))
        assertEquals("token-b", PrefGetter.token)
    }

    @Test
    fun switchingToAnUnknownAccountFails() = runBlocking {
        signIn("acct-a", "token-a")
        assertFalse(Session.switchTo("nobody"))
        assertEquals("acct-a", Session.currentAccount()?.login)
        assertEquals("token-a", PrefGetter.token)
    }

    @Test
    fun accountsCanBeForgottenButNotTheCurrentOne() = runBlocking {
        signIn("acct-a", "token-a")
        signIn("acct-b", "token-b")

        assertFalse("the logged-in account cannot be removed", Session.removeAccount("acct-b"))
        assertTrue(Session.removeAccount("acct-a"))
        assertEquals(emptyList<String>(), Session.accounts().others.map { it.login })
        assertFalse(Session.removeAccount("acct-a"))
    }

    @Test
    fun logoutClearsBothTheAccountAndTheToken() = runBlocking {
        signIn("acct-a", "token-a")
        assertTrue(Session.isLoggedIn)

        Session.logout()

        assertNull("a leftover token would keep isLoggedIn true", PrefGetter.token)
        assertFalse(Session.isLoggedIn)
        assertNull(Session.currentAccount())
        // the other stored accounts survive a logout
        assertEquals(0, TokenStore.accounts().size)
    }

    @Test
    fun logoutKeepsOtherAccounts() = runBlocking {
        signIn("acct-a", "token-a")
        signIn("acct-b", "token-b")

        Session.logout()

        assertEquals(listOf("acct-a"), TokenStore.accounts().map { it.login })
        assertNull(TokenStore.current())
    }

    @Test
    fun enterpriseAccountsAreCountedButNotOffered() = runBlocking {
        TokenStore.upsert(
            StoredAccount(login = "ent", name = "Enterprise", token = "token-ent", isEnterprise = true),
            makeCurrent = false,
        )
        signIn("acct-a", "token-a")

        val accounts = Session.accounts()
        assertEquals("acct-a", accounts.current?.login)
        assertEquals("the modern UI cannot drive enterprise accounts", emptyList<String>(), accounts.others.map { it.login })
        assertEquals(1, accounts.enterpriseCount)
    }

    /** Mirrors what the app does after a successful token login. */
    private fun signIn(login: String, token: String) {
        TokenStore.upsert(StoredAccount(login = login, name = login, token = token), makeCurrent = true)
        PrefGetter.token = token
    }
}
