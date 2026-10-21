package io.github.busybin.gitrehub.compose.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.busybin.gitrehub.compose.common.Avatar
import io.github.busybin.gitrehub.compose.common.Pill
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.compactNumber
import io.github.busybin.gitrehub.core.model.User

/**
 * The handful of fields a profile page shows, so the header does not care which DTO it got.
 */
data class UserInfo(
    val login: String?,
    val name: String?,
    val avatarUrl: String?,
    val bio: String?,
    val company: String?,
    val location: String?,
    val blog: String?,
    val email: String?,
    val htmlUrl: String?,
    val publicRepos: Long,
    val publicGists: Long,
    val followers: Long,
    val following: Long,
)

/** The profile DTO, flattened for [ProfileHeader]. */
fun User.toUserInfo(): UserInfo = UserInfo(
    login = login,
    name = name,
    avatarUrl = avatarUrl,
    bio = bio,
    company = company,
    location = location,
    blog = blog,
    email = email,
    htmlUrl = htmlUrl,
    publicRepos = publicRepos,
    publicGists = publicGists,
    followers = followers,
    following = following,
)

/**
 * Avatar, name, bio, the three counters and the contact rows, shared by the profile and another user's
 * page. [actions] is the button row that differs between the two.
 * nothing), placed right under the counters.
 */
@Composable
fun ProfileHeader(
    user: UserInfo,
    onOpenUrl: (String) -> Unit,
    actions: @Composable () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(user.avatarUrl, size = 72.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = user.name ?: user.login.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = user.login?.let { "@$it" } ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!user.bio.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(user.bio, style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(Modifier.height(14.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Stat(stringResource(R.string.m3_profile_repos), compactNumber(user.publicRepos))
                Stat(stringResource(R.string.m3_profile_followers), compactNumber(user.followers))
                Stat(stringResource(R.string.m3_profile_following), compactNumber(user.following))
            }
        }

        Spacer(Modifier.height(14.dp))
        actions()

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        if (!user.company.isNullOrBlank()) InfoRow(Icons.Outlined.Business, user.company)
        if (!user.location.isNullOrBlank()) InfoRow(Icons.Outlined.LocationOn, user.location)
        if (!user.blog.isNullOrBlank()) {
            InfoRow(Icons.Outlined.Link, user.blog, onClick = { onOpenUrl(user.blog) })
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Public repos ${user.publicRepos}")
            Pill("Gists ${user.publicGists}")
        }
    }
}

@Composable
internal fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun InfoRow(
    icon: ImageVector,
    text: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (onClick != null) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}
