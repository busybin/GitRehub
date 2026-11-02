package io.github.busybin.gitrehub.compose.code

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.launchLoad
import io.github.busybin.gitrehub.App
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.model.CodeEntry
import io.github.busybin.gitrehub.core.offline.RepoPacks
import io.github.busybin.gitrehub.helper.OfflineStatus
import io.github.busybin.gitrehub.compose.data.RepoContent
import io.github.busybin.gitrehub.compose.data.RepoStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FileContent(
    val name: String,
    val path: String,
    val ref: String?,
    val size: Long,
    val htmlUrl: String?,
    val apiUrl: String?,
    val rawUrl: String?,
    val isImage: Boolean,
    /** null when the file could not (or should not) be rendered as text. */
    val text: String?,
    val tooBig: Boolean,
    /** Time of this file's latest commit (the web UI's "last modified"). */
    val lastModified: java.util.Date? = null,
)

/**
 * Reads one file of a repository. Text is shown verbatim; images and oversized files fall back to
 * "open in browser" instead of being decoded into the UI.
 */
class FileViewerViewModel : ViewModel() {

    private val _state = MutableStateFlow<LoadState<FileContent>>(LoadState.Loading)
    val state: StateFlow<LoadState<FileContent>> = _state.asStateFlow()

    private var key: String = ""

    fun load(owner: String, repo: String, path: String, ref: String?, force: Boolean = false) {
        val newKey = "$owner/$repo/$path@$ref"
        if (!force && newKey == key && _state.value is LoadState.Ready) return
        key = newKey
        viewModelScope.launchLoad({ _state.value = it }) { fetch(owner, repo, path, ref) }
    }

    private suspend fun fetch(owner: String, repo: String, path: String, ref: String?): FileContent {
        localFile(owner, repo, path, ref)?.let { return it }
        val entry: CodeEntry? = RepoContent.file(owner, repo, path, ref)
        val name = entry?.name ?: path.substringAfterLast('/')
        val tooBig = (entry?.size ?: 0) > RepoContent.MAX_PREVIEW_BYTES
        val isImage = entry?.isImage == true
        val text = when {
            tooBig || isImage -> null
            else -> entry?.let { RepoContent.text(it) }
        }
        val lastCommit = RepoStats.lastCommit(owner, repo, path, ref)
        val lastModified = lastCommit?.date?.let { java.util.Date.from(it) }
        return FileContent(
            name = name,
            path = entry?.path ?: path,
            ref = ref,
            size = entry?.size ?: 0,
            htmlUrl = entry?.htmlUrl,
            apiUrl = entry?.url,
            rawUrl = entry?.downloadUrl,
            isImage = isImage,
            text = text,
            tooBig = tooBig,
            lastModified = lastModified,
        )
    }

    /** Offline: read the file straight out of the repository pack, if one covers this branch. */
    private suspend fun localFile(owner: String, repo: String, path: String, ref: String?): FileContent? {
        if (OfflineStatus.isOnline) return null
        val context = App.getInstance()
        val account = runCatching { Session.currentAccount()?.login }.getOrNull() ?: return null
        val meta = RepoPacks.get(account, owner, repo) ?: return null
        if (!ref.isNullOrBlank() && ref != meta.branch) return null
        val text = RepoPacks.text(context, meta, path) ?: return null
        return FileContent(
            name = path.substringAfterLast('/'),
            path = path,
            ref = ref,
            size = text.toByteArray(Charsets.UTF_8).size.toLong(),
            htmlUrl = null,
            apiUrl = null,
            rawUrl = null,
            isImage = false,
            text = text,
            tooBig = false,
            lastModified = null,
        )
    }
}
