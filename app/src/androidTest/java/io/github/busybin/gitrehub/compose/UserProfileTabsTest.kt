package io.github.busybin.gitrehub.compose

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.profile.UserProfileViewModel
import io.github.busybin.gitrehub.compose.profile.UserTab
import io.github.busybin.gitrehub.core.net.ApiException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * `users/{login}/{starred,following,followers}` through the real stack.
 *
 * These tabs used to fail with "Unknown error": the Rx call subscribed on the caller's thread, so
 * the page reached the network from the main thread and every tab ended in
 * `NetworkOnMainThreadException` (null message, hence the unhelpful text). That exception is not an
 * `IOException`, so a regression fails this test instead of being skipped as "no network".
 */
@RunWith(AndroidJUnit4::class)
class UserProfileTabsTest {

    private val login = "torvalds"

    @Test
    fun everyTabLoadsInsteadOfFailing() = runBlocking {
        val vm = UserProfileViewModel()
        val tabs = listOf(UserTab.STARRED, UserTab.FOLLOWING, UserTab.FOLLOWERS)

        for (tab in tabs) {
            vm.selectTab(login, tab)
            val state = awaitSettled(vm, tab)
            if (state is LoadState.Failure) {
                val cause = state.cause
                val networkOnly = cause is IOException ||
                    (cause is ApiException && cause.error.status == 403)
                assumeTrue("no network / rate limited: $cause", !networkOnly)
            }
            assertTrue("$tab failed: ${(state as? LoadState.Failure)?.cause}", state is LoadState.Ready)
        }

        val starred = vm.starred.value
        assertTrue(
            "starred list came back empty",
            starred is LoadState.Ready && starred.value.isNotEmpty(),
        )
    }

    private suspend fun awaitSettled(vm: UserProfileViewModel, tab: UserTab): LoadState<*>? =
        withTimeout(30_000) {
            while (true) {
                val state = when (tab) {
                    UserTab.STARRED -> vm.starred.value
                    UserTab.FOLLOWING -> vm.following.value
                    UserTab.FOLLOWERS -> vm.followers.value
                    UserTab.REPOS -> null
                }
                if (state != null && state !is LoadState.Loading) return@withTimeout state
                delay(50)
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }
}
