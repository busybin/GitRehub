package io.github.busybin.gitrehub.compose.notifications

import io.github.busybin.gitrehub.core.model.Notification
import io.github.busybin.gitrehub.helper.PrefHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** A local rule that auto-archives (marks read) matching notifications. */
data class NotificationRule(
    val id: String = UUID.randomUUID().toString(),
    /** `repo` (exact `owner/repo`) or `keyword` (title / repository, case-insensitive). */
    val kind: String = KIND_KEYWORD,
    val value: String = "",
) {
    companion object {
        const val KIND_REPO = "repo"
        const val KIND_KEYWORD = "keyword"
    }
}

/**
 * Local notification rules. GitHub only mutates subscriptions; "this repository is noise to me" is
 * a per-device opinion, so it is stored here and archives (marks read) matching threads instead of
 * unsubscribing from them on the server.
 */
object NotificationRules {

    private const val KEY = "m3_notification_rules"

    private val _rules = MutableStateFlow(read())
    val rules: StateFlow<List<NotificationRule>> = _rules.asStateFlow()

    fun add(kind: String, value: String) {
        val clean = value.trim()
        if (clean.isEmpty()) return
        if (_rules.value.any { it.kind == kind && it.value.equals(clean, ignoreCase = true) }) return
        write(_rules.value + NotificationRule(kind = kind, value = clean))
    }

    fun remove(id: String) = write(_rules.value.filterNot { it.id == id })

    fun matches(item: Notification): Boolean {
        val title = item.subject?.title.orEmpty()
        val repo = item.repoFullName.orEmpty()
        return _rules.value.any { rule ->
            when (rule.kind) {
                NotificationRule.KIND_REPO -> repo.equals(rule.value, ignoreCase = true)
                else -> title.contains(rule.value, ignoreCase = true) ||
                    repo.contains(rule.value, ignoreCase = true)
            }
        }
    }

    private fun write(value: List<NotificationRule>) {
        PrefHelper.putAny(KEY, value.joinToString("\n") { "${it.id}\t${it.kind}\t${it.value}" })
        _rules.value = value
    }

    private fun read(): List<NotificationRule> {
        val raw = PrefHelper.getString(KEY) ?: return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3) null else NotificationRule(parts[0], parts[1], parts[2])
        }.toList()
    }
}
