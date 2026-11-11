package io.github.busybin.gitrehub.compose.discussions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.render

/**
 * New discussion: category, title and body in one GraphQL mutation.
 * On success the number is handed back via `onCreated`.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NewDiscussionScreen(owner: String, repo: String, onCreated: (Int) -> Unit) {
    val vm: NewDiscussionViewModel = viewModel()
    val setup by vm.setup.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    val submitting by vm.submitting.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val created by vm.created.collectAsStateWithLifecycle()

    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(owner, repo) { vm.load(owner, repo) }
    // First category is a sensible default, like the web form.
    LaunchedEffect(setup) {
        if (categoryId == null) categoryId = setup?.categories?.firstOrNull()?.id
    }
    LaunchedEffect(created) { created?.let(onCreated) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
    ) {
        when {
            loading -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
            }

            failed || setup == null -> {
                MetaText(stringResource(R.string.m3_new_discussion_categories_failed))
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { vm.load(owner, repo, force = true) }) {
                    Text(stringResource(R.string.m3_retry))
                }
            }

            else -> {
                val categories = setup?.categories.orEmpty()
                Text(
                    text = stringResource(R.string.m3_new_discussion_category),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = categoryId == category.id,
                            onClick = { categoryId = category.id },
                            label = { Text(category.name) },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.m3_new_issue_title)) },
                    placeholder = { Text(stringResource(R.string.m3_write_title_hint)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    label = { Text(stringResource(R.string.m3_new_issue_body)) },
                    placeholder = { Text(stringResource(R.string.m3_write_comment_hint)) },
                )
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = it.render(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { vm.create(owner, repo, categoryId, title, body) },
                    enabled = !submitting && title.isNotBlank() && !categoryId.isNullOrBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.m3_new_discussion_submit))
                }
            }
        }
    }
}
