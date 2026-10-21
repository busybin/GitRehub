package io.github.busybin.gitrehub.compose.issues

import io.github.busybin.gitrehub.core.model.RepoPermissions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Write buttons on issue/PR pages are gated by the permissions GitHub reports for the repository.
 * A read-only visitor must get no merge, close, edit or manage action.
 */
class RepoPermissionTest {

    private fun perms(
        admin: Boolean = false,
        maintain: Boolean = false,
        push: Boolean = false,
        triage: Boolean = false,
        pull: Boolean = true,
    ) = RepoPermissions(
        admin = admin,
        maintain = maintain,
        push = push,
        triage = triage,
        pull = pull,
    )

    @Test
    fun `read only cannot write or triage`() {
        assertFalse(perms().canWrite)
        assertFalse(perms().canTriage)
    }

    @Test
    fun `triage can manage issues but not merge`() {
        val triage = perms(triage = true)
        assertTrue(triage.canTriage)
        assertFalse(triage.canWrite)
    }

    @Test
    fun `push can merge and manage`() {
        assertTrue(perms(push = true).canWrite)
        assertTrue(perms(push = true).canTriage)
        assertTrue(perms(maintain = true).canWrite)
        assertTrue(perms(admin = true).canWrite)
    }
}
