package io.github.busybin.gitrehub.compose

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwitchAccount
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.accounts.AccountsScreen
import io.github.busybin.gitrehub.compose.actions.ActionsScreen
import io.github.busybin.gitrehub.compose.actions.RunLogsScreen
import io.github.busybin.gitrehub.compose.auth.DeviceLoginScreen
import io.github.busybin.gitrehub.compose.auth.LoginMode
import io.github.busybin.gitrehub.compose.auth.LoginScreen
import io.github.busybin.gitrehub.compose.code.BranchesScreen
import io.github.busybin.gitrehub.compose.code.CodeBrowserScreen
import io.github.busybin.gitrehub.compose.code.CodeSearchScreen
import io.github.busybin.gitrehub.compose.code.CommitDetailScreen
import io.github.busybin.gitrehub.compose.code.CommitsScreen
import io.github.busybin.gitrehub.compose.code.EditFileScreen
import io.github.busybin.gitrehub.compose.code.FileHistoryScreen
import io.github.busybin.gitrehub.compose.code.FileViewerScreen
import io.github.busybin.gitrehub.compose.code.RefKind
import io.github.busybin.gitrehub.compose.common.isWideLayout
import io.github.busybin.gitrehub.compose.common.Avatar
import io.github.busybin.gitrehub.compose.common.AvatarVersions
import io.github.busybin.gitrehub.compose.common.ConfirmDialog
import io.github.busybin.gitrehub.core.offline.DraftQueue
import io.github.busybin.gitrehub.compose.common.DraftsScreen
import io.github.busybin.gitrehub.compose.common.OfflineBanner
import io.github.busybin.gitrehub.compose.common.PackSyncBar
import io.github.busybin.gitrehub.compose.common.TabBadges
import io.github.busybin.gitrehub.compose.common.UpdatePromptHost
import io.github.busybin.gitrehub.compose.common.UpdateSupport
import io.github.busybin.gitrehub.compose.common.restartModernUi
import io.github.busybin.gitrehub.compose.common.PendingText
import io.github.busybin.gitrehub.compose.common.Hairline
import io.github.busybin.gitrehub.compose.common.shareText
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.runBlocking
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.offline.RepoPackSyncer
import io.github.busybin.gitrehub.compose.data.CacheWarmer
import io.github.busybin.gitrehub.compose.discussions.DiscussionDetailScreen
import io.github.busybin.gitrehub.compose.discussions.DiscussionsScreen
import io.github.busybin.gitrehub.compose.discussions.NewDiscussionScreen
import io.github.busybin.gitrehub.compose.feed.FeedFilterAction
import io.github.busybin.gitrehub.compose.feed.FeedFilterSheet
import io.github.busybin.gitrehub.compose.feed.FeedScreen
import io.github.busybin.gitrehub.compose.gists.GistDetailScreen
import io.github.busybin.gitrehub.compose.gists.GistEditorScreen
import io.github.busybin.gitrehub.compose.gists.GistsScreen
import io.github.busybin.gitrehub.compose.issues.IssueKind
import io.github.busybin.gitrehub.compose.issues.IssueDetailScreen
import io.github.busybin.gitrehub.compose.issues.PullRequestFilesScreen
import io.github.busybin.gitrehub.compose.mywork.MyWorkScreen
import io.github.busybin.gitrehub.compose.issues.IssuesScreen
import io.github.busybin.gitrehub.compose.issues.NewIssueScreen
import io.github.busybin.gitrehub.compose.issues.NewPullScreen
import io.github.busybin.gitrehub.compose.notifications.NotificationPrefs
import io.github.busybin.gitrehub.compose.notifications.NotificationsScreen
import io.github.busybin.gitrehub.compose.profile.ProfileScreen
import io.github.busybin.gitrehub.compose.profile.UserProfileScreen
import io.github.busybin.gitrehub.compose.repos.RepoActivityScreen
import io.github.busybin.gitrehub.compose.repos.RepoDetailScreen
import io.github.busybin.gitrehub.compose.repos.RepoInsightsScreen
import io.github.busybin.gitrehub.compose.repos.RepoLabelsScreen
import io.github.busybin.gitrehub.compose.repos.RepoMilestonesScreen
import io.github.busybin.gitrehub.compose.repos.RepoPackAction
import io.github.busybin.gitrehub.compose.repos.ReposScreen
import io.github.busybin.gitrehub.compose.repos.ContributorsScreen
import io.github.busybin.gitrehub.compose.repos.ForksScreen
import io.github.busybin.gitrehub.compose.repos.StargazersScreen
import io.github.busybin.gitrehub.compose.repos.WatchersScreen
import io.github.busybin.gitrehub.compose.repos.TrendingScreen
import io.github.busybin.gitrehub.compose.releases.CompareScreen
import io.github.busybin.gitrehub.compose.releases.ReleaseEditorScreen
import io.github.busybin.gitrehub.compose.releases.ReleasesScreen
import io.github.busybin.gitrehub.compose.search.SearchScreen
import io.github.busybin.gitrehub.compose.saved.SavedScreen
import io.github.busybin.gitrehub.compose.saved.SavedThreadScreen
import io.github.busybin.gitrehub.compose.settings.SettingsScreen
import io.github.busybin.gitrehub.compose.theme.FaTheme
import io.github.busybin.gitrehub.core.auth.StoredAccount
import io.github.busybin.gitrehub.helper.GithubConfigHelper
import io.github.busybin.gitrehub.helper.LinkHandling
import io.github.busybin.gitrehub.helper.LinkHandlingPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


private data class Tab(val route: String, @StringRes val labelRes: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.FEED, R.string.m3_tab_feed, Icons.Outlined.Home),
    Tab(Routes.REPOS, R.string.m3_tab_repos, Icons.Outlined.Book),
    Tab(Routes.NOTIFICATIONS, R.string.m3_tab_notifications, Icons.Outlined.Notifications),
    Tab(Routes.MY_WORK, R.string.m3_my_work, Icons.Outlined.Assignment),
    Tab(Routes.PROFILE, R.string.m3_tab_profile, Icons.Outlined.Person),
)

private val tabRoutes = tabs.map { it.route }.toSet()

/** Root of the modern UI: theme, drawer, bottom navigation and the whole navigation graph. */
@Composable
fun FaApp(routeRequest: MutableState<String?>? = null) {
    FaTheme {
        val nav = rememberNavController()
        // Wide windows get a navigation rail instead of a bottom bar; the content shifts right.
        val wide = isWideLayout()
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        // Re-signed builds cannot install our APK, so they never prompt for updates.
        val selfUpdateAvailable = remember { UpdateSupport.isAvailable(context) }

        val backStack by nav.currentBackStackEntryAsState()
        val current = backStack?.destination?.route
        val packState by RepoPackSyncer.state.collectAsStateWithLifecycle()
        val start = remember {
            runBlocking { Session.repairSession() }
            if (Session.isLoggedIn) Routes.FEED
            else {
                // Another stored account exists: go to the account picker, not the login page
                val hasOthers = runBlocking { Session.accounts().others.isNotEmpty() }
                if (hasOthers) Routes.ACCOUNTS else Routes.LOGIN
            }
        }

        // A link handed in from outside (DeepLinkActivity → MainComposeActivity) opens its own
        // screen. It waits until there is a token: every one of those screens needs one.
        LaunchedEffect(routeRequest?.value) {
            val route = routeRequest?.value ?: return@LaunchedEffect
            if (Session.isLoggedIn) {
                routeRequest.value = null
                nav.navigate(route)
            }
        }

        val accountRevision by Session.accountRevision.collectAsStateWithLifecycle()
        val account by produceState<StoredAccount?>(initialValue = null, current, accountRevision) {
            value = withContext(Dispatchers.IO) { Session.currentAccount() }
        }

        // The OAuth screen runs in its own activity; re-check on resume so coming back
        // from it drops the user into the feed.
        var loggedIn by remember { mutableStateOf(Session.isLoggedIn) }

        // The login screens start their own activity/nav entry and only report back through
        // Session.accountRevision. Without this the drawer kept the pre-login (empty) layout until
        // the next process start.
        LaunchedEffect(accountRevision) { loggedIn = Session.isLoggedIn }

        // Refresh enabled repository packs whose default branch moved. Foreground only: this runs
        // once per sign-in and is a no-op for accounts without packs.
        LaunchedEffect(loggedIn) {
            if (!loggedIn) return@LaunchedEffect
            val account = withContext(Dispatchers.IO) { Session.currentAccount() }?.login
                ?: return@LaunchedEffect
            runCatching { RepoPackSyncer.enqueueStale(context.applicationContext, account) }
        }

        // The pre-rewrite build left its token in MMKV but not in our encrypted store yet; adopting
        // it needs a `GET /user` (to learn whose token it is), so it cannot run inside `start`.
        LaunchedEffect(Unit) {
            if (!loggedIn && Session.importLegacyAccount()) loggedIn = true
        }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    loggedIn = Session.isLoggedIn
                    AvatarVersions.checkSeen()
                    AvatarVersions.checkOwn()
                    scope.launch { TabBadges.refreshAll() }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(loggedIn, current) {
            if (loggedIn && current == Routes.LOGIN) {
                nav.navigate(Routes.FEED) { popUpTo(Routes.LOGIN) { inclusive = true } }
            }
        }

        LaunchedEffect(loggedIn) {
            if (loggedIn) {
                scope.launch {
                    withContext(Dispatchers.IO) { DraftQueue.flushAll() }
                }
                NotificationPrefs.ensureService(context)
                // Warm the repo / starred caches so offline users still see their own repos
                CacheWarmer.warm()
            } else {
                TabBadges.reset()
            }
        }
        LaunchedEffect(loggedIn, account?.login) {
            if (loggedIn && !account?.login.isNullOrBlank()) TabBadges.refreshAll(force = true)
        }

        // Completion / failure / cancel feedback for pack downloads: the bar is already gone by the
        // time the outcome arrives, so the toast is the only thing that can say what happened.
        val packOutcome = packState.outcome
        val packOutcomeText = packOutcome?.let { outcome ->
            when {
                outcome.saved -> stringResource(R.string.m3_pack_saved_toast, outcome.fullName)
                outcome.cancelled -> stringResource(R.string.m3_pack_cancelled_toast)
                else -> stringResource(R.string.m3_pack_failed_toast, outcome.fullName)
            }
        }
        LaunchedEffect(packOutcome) {
            if (packOutcome != null && packOutcomeText != null) {
                Toast.makeText(context, packOutcomeText, Toast.LENGTH_SHORT).show()
                RepoPackSyncer.consumeOutcome()
            }
        }

        // Back from the root screen while a pack downloads: keep it running in the background or
        // stop it before leaving. The drawer's own back handling wins while the drawer is open.
        var exitWhileSaving by rememberSaveable { mutableStateOf(false) }
        val atRoot = nav.previousBackStackEntry == null
        BackHandler(enabled = packState.hasWork && atRoot && drawerState.isClosed) {
            exitWhileSaving = true
        }
        // Material3's drawer does not close on back by itself; without this, back at the root
        // would leave the app while the drawer is open.
        BackHandler(enabled = drawerState.isOpen) {
            scope.launch { drawerState.close() }
        }
        if (exitWhileSaving) {
            val runningName = packState.activeName.orEmpty()
            AlertDialog(
                onDismissRequest = { exitWhileSaving = false },
                title = { Text(stringResource(R.string.m3_pack_exit_title)) },
                text = { Text(stringResource(R.string.m3_pack_exit_message, runningName)) },
                confirmButton = {
                    Row {
                        TextButton(
                            onClick = {
                                exitWhileSaving = false
                                context.findActivity()?.finish()
                            },
                        ) { Text(stringResource(R.string.m3_pack_exit_keep)) }
                        TextButton(
                            onClick = {
                                exitWhileSaving = false
                                RepoPackSyncer.cancelAll(silent = true)
                                context.findActivity()?.finish()
                            },
                        ) { Text(stringResource(R.string.m3_pack_exit_stop)) }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { exitWhileSaving = false }) {
                        Text(stringResource(R.string.m3_cancel))
                    }
                },
            )
        }

        // Offer to make github.com links open here. It can only ever be done by hand in the system
        // settings, and hiding that away in the settings screen (as it was) means nobody finds it.
        val linkHandlingAsked by LinkHandlingPrefs.asked.collectAsStateWithLifecycle()
        var offerLinkHandling by rememberSaveable { mutableStateOf(false) }
        var offeredThisRun by remember { mutableStateOf(false) }
        LaunchedEffect(loggedIn, current, linkHandlingAsked) {
            if (!offeredThisRun && loggedIn && current != null && current != Routes.LOGIN &&
                !linkHandlingAsked && !LinkHandling.isHandling(context)
            ) {
                offeredThisRun = true
                offerLinkHandling = true
            }
        }

        // The filter sheet lives in the content area: the top bar is a SubcomposeLayout and a
        // ModalBottomSheet inside it crashes.
        var showFilterSheet by rememberSaveable { mutableStateOf(false) }

        var confirmLogout by rememberSaveable { mutableStateOf(false) }
        if (confirmLogout) {
            val logoutMessage = stringResource(R.string.m3_logout_message) +
                if (packState.hasWork) "\n\n" + stringResource(R.string.m3_pack_logout_note) else ""
            ConfirmDialog(
                title = stringResource(R.string.m3_logout_title),
                message = logoutMessage,
                confirmLabel = stringResource(R.string.m3_logout_confirm),
                destructive = true,
                onConfirm = {
                    confirmLogout = false
                    // Signing out deletes this account's packs; stop the download before the
                    // account disappears so nothing can be published for it afterwards.
                    RepoPackSyncer.cancelAll(silent = true)
                    scope.launch {
                        withContext(Dispatchers.IO) { Session.logout() }
                        // The old account keeps ViewModels on the nav graph; rebuild everything
                        restartModernUi(context)
                    }
                },
                onDismiss = { confirmLogout = false },
            )
        }
        if (offerLinkHandling) {            LinkHandlingDialog(
                onOpenSettings = {
                    offerLinkHandling = false
                    LinkHandlingPrefs.markAsked()
                    LinkHandling.openSettings(context)
                },
                onDismiss = {
                    offerLinkHandling = false
                    LinkHandlingPrefs.markAsked()
                },
            )
        }

        // Ask for the notification permission once after login (Android 13+): it used to be
        // reachable only from a settings toggle nobody found.
        val notifAsked by NotificationPrefs.prompted.collectAsStateWithLifecycle()
        var offerNotifications by rememberSaveable { mutableStateOf(false) }
        var notifOfferedThisRun by remember { mutableStateOf(false) }
        val notifPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) NotificationPrefs.setEnabled(context, true)
        }
        val needsNotifPermission = remember {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        }
        LaunchedEffect(loggedIn, current, notifAsked, needsNotifPermission) {
            if (!notifOfferedThisRun && loggedIn && current != null && current != Routes.LOGIN &&
                needsNotifPermission && !notifAsked
            ) {
                notifOfferedThisRun = true
                offerNotifications = true
            }
        }
        LaunchedEffect(loggedIn, current) {
            if (loggedIn && current != null && current != Routes.LOGIN &&
                !needsNotifPermission && !notifAsked
            ) {
                NotificationPrefs.markPrompted()
            }
        }
        if (offerNotifications) {
            NotificationAskDialog(
                onAllow = {
                    offerNotifications = false
                    NotificationPrefs.markPrompted()
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                onDismiss = {
                    offerNotifications = false
                    NotificationPrefs.markPrompted()
                },
            )
        }

        // Asked on the first launch, before signing in: the check needs no account.
        UpdatePromptHost(enabled = selfUpdateAvailable)

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = loggedIn && current != Routes.LOGIN,
            drawerContent = {
                ModalDrawerSheet {
                    // Must scroll: landscape leaves ~400dp for ~10 drawer items, and ModalDrawerSheet
                    // provides no scrolling container of its own.
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        DrawerHeader(account = account)
                        if (loggedIn) {
                            HorizontalDivider()
                            NavigationDrawerItem(
                                label = { Text(stringResource(R.string.m3_drawer_accounts)) },
                                icon = { Icon(Icons.Outlined.SwitchAccount, contentDescription = null) },
                                selected = current == Routes.ACCOUNTS,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    nav.navigateSingleTop(Routes.ACCOUNTS)
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                            )
                            NavigationDrawerItem(
                                label = { Text(stringResource(R.string.m3_drawer_trending)) },
                                icon = { Icon(Icons.Outlined.TrendingUp, contentDescription = null) },
                                selected = current == Routes.TRENDING,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    nav.navigateSingleTop(Routes.TRENDING)
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                            )
                            NavigationDrawerItem(
                                label = { Text(stringResource(R.string.m3_gists)) },
                                icon = {
                                    Icon(painterResource(R.drawable.ic_gists), contentDescription = null)
                                },
                                selected = current == Routes.GISTS || current == Routes.GIST,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    nav.navigateSingleTop(Routes.gists())
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                            )
                            NavigationDrawerItem(
                                label = { Text(stringResource(R.string.m3_saved)) },
                                icon = { Icon(Icons.Outlined.BookmarkBorder, contentDescription = null) },
                                selected = current == Routes.SAVED || current == Routes.SAVED_THREAD,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    nav.navigateSingleTop(Routes.SAVED)
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                            )
                            NavigationDrawerItem(
                                label = { Text(stringResource(R.string.m3_drawer_search)) },
                                icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                selected = current == Routes.SEARCH,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    nav.navigateSingleTop(Routes.SEARCH)
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                            )
                            NavigationDrawerItem(
                                label = { Text(stringResource(R.string.m3_drafts)) },
                                icon = { Icon(Icons.Outlined.Drafts, contentDescription = null) },
                                selected = current == Routes.DRAFTS,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    nav.navigateSingleTop(Routes.DRAFTS)
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                            )
                            NavigationDrawerItem(
                                label = { Text(stringResource(R.string.m3_drawer_settings)) },
                                icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                                selected = current == Routes.SETTINGS,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    nav.navigateSingleTop(Routes.SETTINGS)
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                            )
                            HorizontalDivider()
                            NavigationDrawerItem(
                                label = { Text(stringResource(R.string.m3_logout)) },
                                icon = { Icon(Icons.Outlined.Logout, contentDescription = null) },
                                selected = false,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    confirmLogout = true
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                            )
                        } else {
                            // Logged out: expose no nav item that needs a token
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.m3_login_subtitle),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
        ) {
            Scaffold(
                topBar = {
                    if (current != Routes.LOGIN && !(current == Routes.ACCOUNTS && !loggedIn)) {
                        Column {
                            FaTopBar(
                                route = current,
                                entry = backStack,
                                onMenu = { scope.launch { drawerState.open() } },
                                actions = {
                                    if (current == Routes.FEED) FeedFilterAction { showFilterSheet = true }
                                    if (current == Routes.REPO) {
                                        val args = backStack?.arguments
                                        val owner = args?.getString("owner").orEmpty()
                                        val name = args?.getString("name").orEmpty()
                                        if (owner.isNotBlank() && name.isNotBlank()) {
                                            RepoPackAction(owner, name)
                                        }
                                    }
                                },
                            )
                            OfflineBanner()
                        }
                    }
                },
                bottomBar = {
                    Column {
                        // Occupies layout instead of floating over the page, and sits inside the
                        // scaffold so the navigation drawer covers it when it opens.
                        PackSyncBar(
                            onOpen = { nav.openRepo(it) },
                            // The navigation bar below already consumes the system inset; without
                            // it the pack bar needs to keep clear of the gesture area itself.
                            modifier = if (current in tabRoutes && !wide) {
                                Modifier
                            } else {
                                Modifier.navigationBarsPadding()
                            },
                        )
                        if (current in tabRoutes && !wide) {
                            val notificationsBadge by TabBadges.notifications.collectAsStateWithLifecycle()
                            val myWorkBadge by TabBadges.myWork.collectAsStateWithLifecycle()
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                tabs.forEach { tab ->
                                    val tabLabel = stringResource(tab.labelRes)
                                    val badge = when (tab.route) {
                                        Routes.NOTIFICATIONS -> notificationsBadge
                                        Routes.MY_WORK -> myWorkBadge
                                        else -> 0
                                    }
                                    NavigationBarItem(
                                        selected = current == tab.route,
                                        onClick = { nav.navigateSingleTop(tab.route) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                            indicatorColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                        icon = {
                                            BadgedBox(
                                                badge = {
                                                    if (badge > 0) {
                                                        Badge {
                                                            Text(if (badge > 99) "99+" else badge.toString())
                                                        }
                                                    }
                                                },
                                            ) {
                                                Icon(tab.icon, contentDescription = tabLabel)
                                            }
                                        },
                                        label = { Text(tabLabel) },
                                    )
                                }
                            }
                        }
                    }
                },
            ) { padding ->
                val keyFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                LaunchedEffect(Unit) { runCatching { keyFocus.requestFocus() } }
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .then(
                            if (wide && current in tabRoutes) Modifier.padding(start = 80.dp)
                            else Modifier,
                        )
                        .focusRequester(keyFocus)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                                return@onPreviewKeyEvent false
                            }
                            // Hardware keyboard (tablet / DeX / Chromebook) shortcuts.
                            if (event.isCtrlPressed) {
                                when (event.key) {
                                    androidx.compose.ui.input.key.Key.K -> {
                                        nav.navigateSingleTop(Routes.SEARCH); true
                                    }
                                    androidx.compose.ui.input.key.Key.One -> {
                                        nav.navigateSingleTop(Routes.FEED); true
                                    }
                                    androidx.compose.ui.input.key.Key.Two -> {
                                        nav.navigateSingleTop(Routes.REPOS); true
                                    }
                                    androidx.compose.ui.input.key.Key.Three -> {
                                        nav.navigateSingleTop(Routes.NOTIFICATIONS); true
                                    }
                                    androidx.compose.ui.input.key.Key.Four -> {
                                        nav.navigateSingleTop(Routes.MY_WORK); true
                                    }
                                    androidx.compose.ui.input.key.Key.Five -> {
                                        nav.navigateSingleTop(Routes.PROFILE); true
                                    }
                                    androidx.compose.ui.input.key.Key.Comma -> {
                                        nav.navigateSingleTop(Routes.SETTINGS); true
                                    }
                                    else -> false
                                }
                            } else if (event.key == androidx.compose.ui.input.key.Key.Escape) {
                                if (nav.previousBackStackEntry != null) nav.popBackStack()
                                true
                            } else {
                                false
                            }
                        },
                ) {
                    if (wide && current in tabRoutes) {
                        AppNavigationRail(
                            current = current,
                            onNavigate = { route -> nav.navigateSingleTop(route) },
                            modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight(),
                        )
                    }
                    NavHost(navController = nav, startDestination = start) {
                        composable(Routes.LOGIN) {
                            LoginScreen(
                                onLoggedIn = {
                                    nav.navigate(Routes.FEED) {
                                        popUpTo(Routes.LOGIN) { inclusive = true }
                                    }
                                },
                                onOpenDeviceLogin = deviceLoginRoute(nav, Routes.DEVICE_LOGIN),
                            )
                        }
                        composable(Routes.ADD_ACCOUNT) {
                            LoginScreen(
                                mode = LoginMode.ADD_ACCOUNT,
                                // The new account becomes current: drop the old ViewModels
                                onLoggedIn = { restartModernUi(context) },
                                onOpenDeviceLogin = deviceLoginRoute(nav, Routes.DEVICE_LOGIN_ADD),
                            )
                        }
                        composable(Routes.DEVICE_LOGIN) {
                            DeviceLoginScreen(
                                onLoggedIn = {
                                    nav.navigate(Routes.FEED) {
                                        popUpTo(Routes.LOGIN) { inclusive = true }
                                    }
                                },
                            )
                        }
                        composable(Routes.DEVICE_LOGIN_ADD) {
                            DeviceLoginScreen(
                                mode = LoginMode.ADD_ACCOUNT,
                                onLoggedIn = { restartModernUi(context) },
                            )
                        }
                        composable(Routes.ACCOUNTS) {
                            AccountsScreen(
                                onAddAccount = { nav.navigate(Routes.ADD_ACCOUNT) },
                                onSwitched = { restartModernUi(context) },
                            )
                        }
                        composable(Routes.FEED) {
                            FeedScreen(
                                onOpenRepo = { nav.openRepo(it) },
                                onOpenUser = { nav.navigate(Routes.user(it)) },
                                onOpenIssue = { owner, repo, number ->
                                    nav.navigate(Routes.issue(owner, repo, number))
                                },
                                onOpenReleases = { owner, repo -> nav.navigate(Routes.releases(owner, repo)) },
                            )
                        }
                        composable(Routes.REPOS) {
                            ReposScreen(
                                onOpenRepo = { nav.openRepo(it) },
                                onOpenIssues = { owner, repo -> nav.navigate(Routes.issues(owner, repo)) },
                                onOpenPulls = { owner, repo -> nav.navigate(Routes.issues(owner, repo, "pull")) },
                                onOpenReleases = { owner, repo -> nav.navigate(Routes.releases(owner, repo)) },
                                onOpenCode = { owner, repo -> nav.navigate(Routes.code(owner, repo)) },
                                onOpenBranches = { owner, repo -> nav.navigate(Routes.branches(owner, repo)) },
                                onOpenTags = { owner, repo -> nav.navigate(Routes.branches(owner, repo, "tag")) },
                                onOpenContributors = { owner, repo -> nav.navigate(Routes.contributors(owner, repo)) },
                                onOpenStargazers = { owner, repo -> nav.navigate(Routes.stargazers(owner, repo)) },
                                onOpenWatchers = { owner, repo -> nav.navigate(Routes.watchers(owner, repo)) },
                                onOpenForks = { owner, repo -> nav.navigate(Routes.forks(owner, repo)) },
                                onOpenDiscussions = { owner, repo -> nav.navigate(Routes.discussions(owner, repo)) },
                                onOpenActions = { owner, repo -> nav.navigate(Routes.actions(owner, repo)) },
                                onOpenActivity = { owner, repo -> nav.navigate(Routes.repoActivity(owner, repo)) },
                                onOpenLabels = { owner, repo -> nav.navigate(Routes.labels(owner, repo)) },
                                onOpenMilestones = { owner, repo -> nav.navigate(Routes.milestones(owner, repo)) },
                                onOpenUser = { nav.navigate(Routes.user(it)) },
                            )
                        }
                        composable(Routes.TRENDING) {
                            TrendingScreen(onOpenRepo = { nav.openRepo(it) })
                        }
                        composable(Routes.MY_WORK) {
                            MyWorkScreen(
                                onOpenIssue = { owner, repo, number ->
                                    nav.navigate(Routes.issue(owner, repo, number))
                                },
                            )
                        }
                        composable(Routes.NOTIFICATIONS) {
                            NotificationsScreen(
                                onOpenIssue = { owner, repo, number ->
                                    nav.navigate(Routes.issue(owner, repo, number))
                                },
                                onOpenDiscussion = { owner, repo, number ->
                                    nav.navigate(Routes.discussion(owner, repo, number))
                                },
                                onOpenCommit = { owner, repo, sha ->
                                    nav.navigate(Routes.commit(owner, repo, sha))
                                },
                                onOpenReleases = { owner, repo ->
                                    nav.navigate(Routes.releases(owner, repo))
                                },
                            )
                        }
                        composable(Routes.PROFILE) {
                            ProfileScreen(
                                onOpenAccounts = { nav.navigateSingleTop(Routes.ACCOUNTS) },
                                onLoggedOut = { restartModernUi(context) },
                            )
                        }
                        composable(Routes.SEARCH) {
                            SearchScreen(
                                onOpenRepo = { nav.openRepo(it) },
                                onOpenUser = { nav.navigate(Routes.user(it)) },
                                onOpenIssue = { owner, repo, number ->
                                    nav.navigate(Routes.issue(owner, repo, number))
                                },
                                onOpenCode = { owner, repo, path, ref ->
                                    nav.navigate(Routes.file(owner, repo, path, ref))
                                },
                            )
                        }
                        composable(Routes.SETTINGS) { SettingsScreen() }
                        composable(
                            route = Routes.GISTS,
                            arguments = listOf(
                                navArgument("login") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                            ),
                        ) { entry ->
                            GistsScreen(
                                login = entry.arguments?.getString("login").orEmpty()
                                    .ifBlank { null },
                                onOpenGist = { gist -> nav.navigate(Routes.gist(gist.id)) },
                                onNewGist = { nav.navigate(Routes.gistEdit()) },
                            )
                        }
                        composable(
                            route = Routes.GIST,
                            arguments = listOf(
                                navArgument("id") { type = NavType.StringType },
                            ),
                        ) { entry ->
                            val gistId = entry.arguments?.getString("id").orEmpty()
                            GistDetailScreen(
                                id = gistId,
                                onDeleted = { nav.popBackStack() },
                                onEdit = { nav.navigate(Routes.gistEdit(gistId)) },
                            )
                        }
                        composable(
                            route = Routes.GIST_EDIT,
                            arguments = listOf(
                                navArgument("id") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                            ),
                        ) { entry ->
                            val editId = entry.arguments?.getString("id").orEmpty().ifBlank { null }
                            val initial = remember { PendingText.gistContent.also { PendingText.gistContent = null } }
                            GistEditorScreen(
                                id = editId,
                                initialContent = initial,
                                onDone = { nav.popBackStack() },
                            )
                        }
                        composable(Routes.SHARE) {
                            val shared = remember { PendingText.shared.also { PendingText.shared = null } }
                                .orEmpty()
                            ShareScreen(
                                text = shared,
                                onNewIssue = { owner, repo ->
                                    PendingText.issueBody = shared
                                    nav.navigate(Routes.newIssue(owner, repo)) {
                                        popUpTo(Routes.SHARE) { inclusive = true }
                                    }
                                },
                                onNewGist = {
                                    PendingText.gistContent = shared
                                    nav.navigate(Routes.gistEdit()) {
                                        popUpTo(Routes.SHARE) { inclusive = true }
                                    }
                                },
                                onDone = { nav.popBackStack() },
                            )
                        }
                        composable(Routes.SAVED) {
                            SavedScreen(
                                onOpen = { meta ->
                                    nav.navigate(
                                        Routes.savedThread(meta.owner, meta.repo, meta.number),
                                    )
                                },
                                onOpenRepo = { owner, repo -> nav.openRepo("$owner/$repo") },
                            )
                        }
                        composable(
                            route = Routes.SAVED_THREAD,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                                navArgument("number") { type = NavType.IntType },
                            ),
                        ) { entry ->
                            val arguments = entry.arguments
                            SavedThreadScreen(
                                owner = arguments?.getString("owner").orEmpty(),
                                repo = arguments?.getString("repo").orEmpty(),
                                number = arguments?.getInt("number") ?: 0,
                                onOpenOnline = { o, r, n -> nav.navigate(Routes.issue(o, r, n)) },
                            )
                        }
                        composable(Routes.DRAFTS) {
                            DraftsScreen(
                                onOpenIssue = { owner, repo, number ->
                                    nav.navigate(Routes.issue(owner, repo, number))
                                },
                            )
                        }
                        composable(
                            route = Routes.USER,
                            arguments = listOf(
                                navArgument("login") { type = NavType.StringType },
                            ),
                        ) { entry ->
                            UserProfileScreen(
                                login = entry.arguments?.getString("login").orEmpty(),
                                onOpenRepo = { nav.openRepo(it) },
                                onOpenUser = { nav.navigate(Routes.user(it)) },
                                onOpenGist = { gist -> nav.navigate(Routes.gist(gist.id)) },
                            )
                        }
                        composable(
                            route = Routes.REPO,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("name") { type = NavType.StringType },
                            ),
                        ) { entry ->
                            RepoDetailScreen(
                                owner = entry.arguments?.getString("owner").orEmpty(),
                                name = entry.arguments?.getString("name").orEmpty(),
                                onOpenIssues = { owner, repo -> nav.navigate(Routes.issues(owner, repo)) },
                                onOpenPulls = { owner, repo -> nav.navigate(Routes.issues(owner, repo, "pull")) },
                                onOpenReleases = { owner, repo -> nav.navigate(Routes.releases(owner, repo)) },
                                onOpenCode = { owner, repo -> nav.navigate(Routes.code(owner, repo)) },
                                onOpenBranches = { owner, repo -> nav.navigate(Routes.branches(owner, repo)) },
                                onOpenTags = { owner, repo -> nav.navigate(Routes.branches(owner, repo, "tag")) },
                                onOpenContributors = { owner, repo -> nav.navigate(Routes.contributors(owner, repo)) },
                                onOpenStargazers = { owner, repo -> nav.navigate(Routes.stargazers(owner, repo)) },
                                onOpenWatchers = { owner, repo -> nav.navigate(Routes.watchers(owner, repo)) },
                                onOpenForks = { owner, repo -> nav.navigate(Routes.forks(owner, repo)) },
                                onOpenDiscussions = { owner, repo -> nav.navigate(Routes.discussions(owner, repo)) },
                                onOpenActions = { owner, repo -> nav.navigate(Routes.actions(owner, repo)) },
                                onOpenActivity = { owner, repo -> nav.navigate(Routes.repoActivity(owner, repo)) },
                                onOpenInsights = { owner, repo -> nav.navigate(Routes.insights(owner, repo)) },
                                onOpenLabels = { owner, repo -> nav.navigate(Routes.labels(owner, repo)) },
                                onOpenMilestones = { owner, repo -> nav.navigate(Routes.milestones(owner, repo)) },
                                onOpenUser = { nav.navigate(Routes.user(it)) },
                                onOpenRepo = { o, r -> nav.navigate(Routes.repo(o, r)) },
                            )
                        }
                        composable(
                            route = Routes.STARGAZERS,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                            ),
                        ) { entry ->
                            StargazersScreen(
                                owner = entry.arguments?.getString("owner").orEmpty(),
                                repo = entry.arguments?.getString("repo").orEmpty(),
                                onOpenUser = { nav.navigate(Routes.user(it)) },
                            )
                        }
                        composable(
                            route = Routes.WATCHERS,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                            ),
                        ) { entry ->
                            WatchersScreen(
                                owner = entry.arguments?.getString("owner").orEmpty(),
                                repo = entry.arguments?.getString("repo").orEmpty(),
                                onOpenUser = { nav.navigate(Routes.user(it)) },
                            )
                        }
                        composable(
                            route = Routes.CONTRIBUTORS,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                            ),
                        ) { entry ->
                            ContributorsScreen(
                                owner = entry.arguments?.getString("owner").orEmpty(),
                                repo = entry.arguments?.getString("repo").orEmpty(),
                                onOpenUser = { nav.navigate(Routes.user(it)) },
                            )
                        }
                        composable(
                            route = Routes.FORKS,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                            ),
                        ) { entry ->
                            ForksScreen(
                                owner = entry.arguments?.getString("owner").orEmpty(),
                                repo = entry.arguments?.getString("repo").orEmpty(),
                                onOpenRepo = { full -> nav.openRepo(full) },
                            )
                        }
                        composable(
                            route = Routes.REPO_ACTIVITY,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                            ),
                        ) { entry ->
                            RepoActivityScreen(
                                owner = entry.arguments?.getString("owner").orEmpty(),
                                repo = entry.arguments?.getString("repo").orEmpty(),
                                onOpenRepo = { nav.openRepo(it) },
                                onOpenUser = { nav.navigate(Routes.user(it)) },
                                onOpenIssue = { owner, repo, number ->
                                    nav.navigate(Routes.issue(owner, repo, number))
                                },
                                onOpenReleases = { owner, repo -> nav.navigate(Routes.releases(owner, repo)) },
                            )
                        }
                        composable(
                            route = Routes.INSIGHTS,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                            ),
                        ) { entry ->
                            RepoInsightsScreen(
                                owner = entry.arguments?.getString("owner").orEmpty(),
                                repo = entry.arguments?.getString("repo").orEmpty(),
                                onOpenIssue = { owner, repo, number ->
                                    nav.navigate(Routes.issue(owner, repo, number))
                                },
                            )
                        }
                        composable(
                            route = Routes.BRANCHES,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                                navArgument("kind") {
                                    type = NavType.StringType
                                    defaultValue = "branch"
                                },
                            ),
                        ) { entry ->
                            val owner = entry.arguments?.getString("owner").orEmpty()
                            val repo = entry.arguments?.getString("repo").orEmpty()
                            BranchesScreen(
                                owner = owner,
                                repo = repo,
                                initialKind = if (entry.arguments?.getString("kind") == "tag") RefKind.TAG else RefKind.BRANCH,
                                onOpenRef = { ref -> nav.navigate(Routes.code(owner, repo, ref)) },
                                onNewPull = { nav.navigate(Routes.newPull(owner, repo)) },
                            )
                        }
                        composable(
                            route = Routes.CODE,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                                navArgument("ref") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                                navArgument("path") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                            ),
                        ) { entry ->
                            val owner = entry.arguments?.getString("owner").orEmpty()
                            val repo = entry.arguments?.getString("repo").orEmpty()
                            val ref = entry.arguments?.getString("ref").orEmpty().ifBlank { null }
                            CodeBrowserScreen(
                                owner = owner,
                                repo = repo,
                                ref = ref,
                                onOpenFile = { path -> nav.navigate(Routes.file(owner, repo, path, ref)) },
                                onOpenBranches = { nav.navigate(Routes.branches(owner, repo)) },
                                onOpenCommit = { sha -> nav.navigate(Routes.commit(owner, repo, sha)) },
                                onOpenCommits = { resolvedRef ->
                                    nav.navigate(Routes.commits(owner, repo, resolvedRef))
                                },
                                onSearchCode = { nav.navigate(Routes.codeSearch(owner, repo, ref)) },
                            )
                        }
                        composable(
                            route = Routes.CODE_SEARCH,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                                navArgument("ref") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                            ),
                        ) { entry ->
                            val searchOwner = entry.arguments?.getString("owner").orEmpty()
                            val searchRepo = entry.arguments?.getString("repo").orEmpty()
                            val searchRef = entry.arguments?.getString("ref").orEmpty().ifBlank { null }
                            CodeSearchScreen(
                                owner = searchOwner,
                                repo = searchRepo,
                                ref = searchRef,
                                onOpenFile = { path ->
                                    nav.navigate(Routes.file(searchOwner, searchRepo, path, searchRef))
                                },
                            )
                        }
                        composable(
                            route = Routes.FILE,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                                navArgument("path") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                                navArgument("ref") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                            ),
                        ) { entry ->
                            val fileOwner = entry.arguments?.getString("owner").orEmpty()
                            val fileRepo = entry.arguments?.getString("repo").orEmpty()
                            val filePath = entry.arguments?.getString("path").orEmpty()
                            val fileRef = entry.arguments?.getString("ref").orEmpty().ifBlank { null }
                            val fileRefresh by entry.savedStateHandle
                                .getStateFlow("file_refresh", false)
                                .collectAsStateWithLifecycle()
                            FileViewerScreen(
                                owner = fileOwner,
                                repo = fileRepo,
                                path = filePath,
                                ref = fileRef,
                                onOpenHistory = {
                                    nav.navigate(
                                        Routes.fileHistory(fileOwner, fileRepo, filePath, fileRef)
                                    )
                                },
                                onEdit = {
                                    nav.navigate(
                                        Routes.editFile(fileOwner, fileRepo, filePath, fileRef)
                                    )
                                },
                                refreshSignal = fileRefresh,
                                onRefreshConsumed = {
                                    entry.savedStateHandle["file_refresh"] = false
                                },
                            )
                        }
                        composable(
                            route = Routes.FILE_HISTORY,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                                navArgument("path") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                                navArgument("ref") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                            ),
                        ) { entry ->
                            val historyOwner = entry.arguments?.getString("owner").orEmpty()
                            val historyRepo = entry.arguments?.getString("repo").orEmpty()
                            FileHistoryScreen(
                                owner = historyOwner,
                                repo = historyRepo,
                                path = entry.arguments?.getString("path").orEmpty(),
                                ref = entry.arguments?.getString("ref").orEmpty().ifBlank { null },
                                onOpenCommit = { sha ->
                                    nav.navigate(Routes.commit(historyOwner, historyRepo, sha))
                                },
                            )
                        }
                        composable(
                            route = Routes.EDIT_FILE,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                                navArgument("path") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                                navArgument("ref") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                            ),
                        ) { entry ->
                            val editOwner = entry.arguments?.getString("owner").orEmpty()
                            val editRepo = entry.arguments?.getString("repo").orEmpty()
                            EditFileScreen(
                                owner = editOwner,
                                repo = editRepo,
                                path = entry.arguments?.getString("path").orEmpty(),
                                ref = entry.arguments?.getString("ref").orEmpty().ifBlank { null },
                                onDone = { prNumber ->
                                    nav.previousBackStackEntry?.savedStateHandle
                                        ?.set("file_refresh", true)
                                    // Leave the editor behind either way: a direct commit goes back
                                    // to the file, a pull request opens on top of it.
                                    nav.popBackStack()
                                    if (prNumber != null) {
                                        nav.navigate(Routes.issue(editOwner, editRepo, prNumber))
                                    }
                                },
                            )
                        }
                        composable(
                            route = Routes.ISSUES,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                                navArgument("kind") {
                                    type = NavType.StringType
                                    defaultValue = "issue"
                                },
                            ),
                        ) { entry ->
                            val issuesOwner = entry.arguments?.getString("owner").orEmpty()
                            val issuesRepo = entry.arguments?.getString("repo").orEmpty()
                            val refreshSignal by entry.savedStateHandle
                                .getStateFlow("issues_refresh", false)
                                .collectAsStateWithLifecycle()
                            IssuesScreen(
                                owner = issuesOwner,
                                repo = issuesRepo,
                                kind = if (entry.arguments?.getString("kind") == "pull") {
                                    IssueKind.PULL
                                } else {
                                    IssueKind.ISSUE
                                },
                                onOpenIssue = { number ->
                                    nav.navigate(Routes.issue(issuesOwner, issuesRepo, number))
                                },
                                onNewIssue = {
                                    nav.navigate(Routes.newIssue(issuesOwner, issuesRepo))
                                },
                                refreshSignal = refreshSignal,
                                onRefreshConsumed = {
                                    entry.savedStateHandle["issues_refresh"] = false
                                },
                            )
                        }
                        composable(
                            route = Routes.NEW_ISSUE,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                            ),
                        ) { entry ->
                            val newOwner = entry.arguments?.getString("owner").orEmpty()
                            val newRepo = entry.arguments?.getString("repo").orEmpty()
                            NewIssueScreen(
                                owner = newOwner,
                                repo = newRepo,
                                onCreated = { number ->
                                    nav.previousBackStackEntry
                                        ?.savedStateHandle
                                        ?.set("issues_refresh", true)
                                    nav.navigate(Routes.issue(newOwner, newRepo, number)) {
                                        popUpTo(Routes.NEW_ISSUE) { inclusive = true }
                                    }
                                },
                            )
                        }
                        composable(
                            route = Routes.NEW_PULL,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                                navArgument("base") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                                navArgument("head") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                            ),
                        ) { entry ->
                            val prOwner = entry.arguments?.getString("owner").orEmpty()
                            val prRepo = entry.arguments?.getString("repo").orEmpty()
                            NewPullScreen(
                                owner = prOwner,
                                repo = prRepo,
                                initialBase = entry.arguments?.getString("base").orEmpty(),
                                initialHead = entry.arguments?.getString("head").orEmpty(),
                                onCreated = { number ->
                                    nav.navigate(Routes.issue(prOwner, prRepo, number)) {
                                        popUpTo(Routes.NEW_PULL) { inclusive = true }
                                    }
                                },
                            )
                        }
                        composable(
                            route = Routes.COMMIT,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                                navArgument("sha") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                            ),
                        ) { entry ->
                            CommitDetailScreen(
                                owner = entry.arguments?.getString("owner").orEmpty(),
                                repo = entry.arguments?.getString("repo").orEmpty(),
                                sha = entry.arguments?.getString("sha").orEmpty(),
                            )
                        }
                        composable(
                            route = Routes.RELEASES,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                            ),
                        ) { entry ->
                            val relOwner = entry.arguments?.getString("owner").orEmpty()
                            val relRepo = entry.arguments?.getString("repo").orEmpty()
                            val relRefresh by entry.savedStateHandle
                                .getStateFlow("releases_refresh", false)
                                .collectAsStateWithLifecycle()
                            ReleasesScreen(
                                owner = relOwner,
                                repo = relRepo,
                                onNewRelease = { nav.navigate(Routes.releaseEdit(relOwner, relRepo)) },
                                onEditRelease = { id -> nav.navigate(Routes.releaseEdit(relOwner, relRepo, id)) },
                                onCompare = { base, head ->
                                    nav.navigate(Routes.compare(relOwner, relRepo, base, head))
                                },
                                refreshSignal = relRefresh,
                                onRefreshConsumed = { entry.savedStateHandle["releases_refresh"] = false },
                            )
                        }
                        composable(
                            route = Routes.RELEASE_EDIT,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                                navArgument("id") {
                                    type = NavType.LongType
                                    defaultValue = 0L
                                },
                            ),
                        ) { entry ->
                            val relOwner = entry.arguments?.getString("owner").orEmpty()
                            val relRepo = entry.arguments?.getString("repo").orEmpty()
                            val id = entry.arguments?.getLong("id") ?: 0L
                            ReleaseEditorScreen(
                                owner = relOwner,
                                repo = relRepo,
                                releaseId = id.takeIf { it > 0 },
                                onDone = {
                                    nav.previousBackStackEntry?.savedStateHandle
                                        ?.set("releases_refresh", true)
                                    nav.popBackStack()
                                },
                            )
                        }
                        composable(
                            route = Routes.COMPARE,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                                navArgument("base") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                                navArgument("head") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                            ),
                        ) { entry ->
                            val cmpOwner = entry.arguments?.getString("owner").orEmpty()
                            val cmpRepo = entry.arguments?.getString("repo").orEmpty()
                            val cmpBase = entry.arguments?.getString("base").orEmpty()
                            val cmpHead = entry.arguments?.getString("head").orEmpty()
                            CompareScreen(
                                owner = cmpOwner,
                                repo = cmpRepo,
                                base = cmpBase,
                                head = cmpHead,
                                onOpenCommit = { sha -> nav.navigate(Routes.commit(cmpOwner, cmpRepo, sha)) },
                                onCreatePull = {
                                    nav.navigate(Routes.newPull(cmpOwner, cmpRepo, cmpBase, cmpHead))
                                },
                            )
                        }
                        composable(
                            route = Routes.ISSUE,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                                navArgument("number") { type = NavType.IntType },
                            ),
                        ) { entry ->
                            val issueOwner = entry.arguments?.getString("owner").orEmpty()
                            val issueRepo = entry.arguments?.getString("repo").orEmpty()
                            IssueDetailScreen(
                                owner = issueOwner,
                                repo = issueRepo,
                                number = entry.arguments?.getInt("number") ?: 0,
                                onOpenCommit = { o, r, sha ->
                                    nav.navigate(Routes.commit(o, r, sha))
                                },
                                onOpenFiles = { o, r, n ->
                                    nav.navigate(Routes.prFiles(o, r, n))
                                },
                                onPullDetected = { entry.savedStateHandle["is_pull"] = it },
                            )
                        }
                        composable(
                            route = Routes.PR_FILES,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                                navArgument("number") { type = NavType.IntType },
                            ),
                        ) { entry ->
                            PullRequestFilesScreen(
                                owner = entry.arguments?.getString("owner").orEmpty(),
                                repo = entry.arguments?.getString("repo").orEmpty(),
                                number = entry.arguments?.getInt("number") ?: 0,
                            )
                        }
                        composable(
                            route = Routes.COMMITS,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                                navArgument("ref") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                            ),
                        ) { entry ->
                            CommitsScreen(
                                owner = entry.arguments?.getString("owner").orEmpty(),
                                repo = entry.arguments?.getString("repo").orEmpty(),
                                ref = entry.arguments?.getString("ref").orEmpty(),
                                onOpenCommit = { sha ->
                                    nav.navigate(
                                        Routes.commit(
                                            entry.arguments?.getString("owner").orEmpty(),
                                            entry.arguments?.getString("repo").orEmpty(),
                                            sha,
                                        )
                                    )
                                },
                            )
                        }
                        composable(
                            route = Routes.DISCUSSIONS,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                            ),
                        ) { entry ->
                            val discussionsOwner = entry.arguments?.getString("owner").orEmpty()
                            val discussionsRepo = entry.arguments?.getString("repo").orEmpty()
                            val discussionsRefresh by entry.savedStateHandle
                                .getStateFlow("discussions_refresh", false)
                                .collectAsStateWithLifecycle()
                            DiscussionsScreen(
                                owner = discussionsOwner,
                                repo = discussionsRepo,
                                onOpenDiscussion = { number ->
                                    nav.navigate(Routes.discussion(discussionsOwner, discussionsRepo, number))
                                },
                                onOpenUser = { nav.navigate(Routes.user(it)) },
                                onNewDiscussion = {
                                    nav.navigate(Routes.newDiscussion(discussionsOwner, discussionsRepo))
                                },
                                refreshSignal = discussionsRefresh,
                                onRefreshConsumed = {
                                    entry.savedStateHandle["discussions_refresh"] = false
                                },
                            )
                        }
                        composable(
                            route = Routes.NEW_DISCUSSION,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                            ),
                        ) { entry ->
                            val newDiscussionOwner = entry.arguments?.getString("owner").orEmpty()
                            val newDiscussionRepo = entry.arguments?.getString("repo").orEmpty()
                            NewDiscussionScreen(
                                owner = newDiscussionOwner,
                                repo = newDiscussionRepo,
                                onCreated = { number ->
                                    nav.previousBackStackEntry
                                        ?.savedStateHandle
                                        ?.set("discussions_refresh", true)
                                    nav.navigate(
                                        Routes.discussion(newDiscussionOwner, newDiscussionRepo, number),
                                    ) {
                                        popUpTo(Routes.NEW_DISCUSSION) { inclusive = true }
                                    }
                                },
                            )
                        }
                        composable(
                            route = Routes.DISCUSSION,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                                navArgument("number") { type = NavType.IntType },
                            ),
                        ) { entry ->
                            DiscussionDetailScreen(
                                owner = entry.arguments?.getString("owner").orEmpty(),
                                repo = entry.arguments?.getString("repo").orEmpty(),
                                number = entry.arguments?.getInt("number") ?: 0,
                                onOpenUser = { nav.navigate(Routes.user(it)) },
                            )
                        }
                        composable(
                            route = Routes.ACTIONS,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                            ),
                        ) { entry ->
                            val actionsOwner = entry.arguments?.getString("owner").orEmpty()
                            val actionsRepo = entry.arguments?.getString("repo").orEmpty()
                            ActionsScreen(
                                owner = actionsOwner,
                                repo = actionsRepo,
                                onOpenLogs = { runId ->
                                    nav.navigate(Routes.runLogs(actionsOwner, actionsRepo, runId))
                                },
                            )
                        }
                        composable(
                            route = Routes.LABELS,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                            ),
                        ) { entry ->
                            RepoLabelsScreen(
                                owner = entry.arguments?.getString("owner").orEmpty(),
                                repo = entry.arguments?.getString("repo").orEmpty(),
                            )
                        }
                        composable(
                            route = Routes.MILESTONES,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                            ),
                        ) { entry ->
                            RepoMilestonesScreen(
                                owner = entry.arguments?.getString("owner").orEmpty(),
                                repo = entry.arguments?.getString("repo").orEmpty(),
                            )
                        }
                        composable(
                            route = Routes.RUN_LOGS,
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                                navArgument("runId") { type = NavType.LongType },
                            ),
                        ) { entry ->
                            RunLogsScreen(
                                owner = entry.arguments?.getString("owner").orEmpty(),
                                repo = entry.arguments?.getString("repo").orEmpty(),
                                runId = entry.arguments?.getLong("runId") ?: 0L,
                            )
                        }
                    }
                    if (showFilterSheet && current == Routes.FEED) {
                        FeedFilterSheet(onDismiss = { showFilterSheet = false })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FaTopBar(
    route: String?,
    entry: androidx.navigation.NavBackStackEntry?,
    onMenu: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    val title = when (route) {
        Routes.FEED -> stringResource(R.string.m3_title_feed)
        Routes.REPOS -> stringResource(R.string.m3_title_repos)
        Routes.TRENDING -> stringResource(R.string.m3_title_trending)
        Routes.NOTIFICATIONS -> stringResource(R.string.m3_title_notifications)
        Routes.PROFILE -> stringResource(R.string.m3_title_profile)
        Routes.ADD_ACCOUNT -> stringResource(R.string.m3_title_add_account)
        Routes.DEVICE_LOGIN -> stringResource(R.string.m3_title_device_login)
        Routes.DEVICE_LOGIN_ADD -> stringResource(R.string.m3_title_add_account)
        Routes.ACCOUNTS -> stringResource(R.string.m3_title_accounts)
        Routes.SEARCH -> stringResource(R.string.m3_title_search)
        Routes.GISTS -> stringResource(R.string.m3_gists)
        Routes.GIST -> stringResource(R.string.m3_gist)
        Routes.GIST_EDIT -> stringResource(R.string.m3_gist_edit)
        Routes.SHARE -> stringResource(R.string.m3_share_title)
        Routes.DRAFTS -> stringResource(R.string.m3_drafts)
        Routes.SETTINGS -> stringResource(R.string.m3_title_settings)
        Routes.REPO -> entry?.arguments?.getString("name")
            ?: stringResource(R.string.m3_title_repo)

        Routes.USER -> entry?.arguments?.getString("login")?.let { "@$it" }
            ?: stringResource(R.string.m3_title_user)

        Routes.ISSUES -> if (entry?.arguments?.getString("kind") == "pull") {
            stringResource(R.string.m3_pulls)
        } else {
            stringResource(R.string.m3_issues)
        }
        Routes.STARGAZERS -> stringResource(R.string.m3_stargazers)
        Routes.WATCHERS -> stringResource(R.string.m3_watchers)
        Routes.FORKS -> stringResource(R.string.m3_forks)
        Routes.CONTRIBUTORS -> stringResource(R.string.m3_contributors)
        Routes.REPO_ACTIVITY -> stringResource(R.string.m3_activity)
        Routes.INSIGHTS -> stringResource(R.string.m3_insights)
        Routes.RELEASES -> stringResource(R.string.m3_releases)
        Routes.RELEASE_EDIT -> stringResource(R.string.m3_release_edit)
        Routes.COMPARE -> stringResource(R.string.m3_compare)
        Routes.BRANCHES -> if (entry?.arguments?.getString("kind") == "tag") {
            stringResource(R.string.m3_tag)
        } else {
            stringResource(R.string.m3_branch)
        }
        Routes.CODE -> entry?.arguments?.getString("path")?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.m3_title_code)

        Routes.FILE -> entry?.arguments?.getString("path")?.substringAfterLast('/')
            ?: stringResource(R.string.m3_title_file)

        Routes.FILE_HISTORY -> stringResource(R.string.m3_file_history)
        Routes.CODE_SEARCH -> stringResource(R.string.m3_code_search)

        Routes.EDIT_FILE -> stringResource(R.string.m3_edit_file)

        Routes.MY_WORK -> stringResource(R.string.m3_my_work)
        Routes.ISSUE -> "#${entry?.arguments?.getInt("number") ?: 0}"
        Routes.PR_FILES -> stringResource(R.string.m3_pr_files_title)
        Routes.NEW_ISSUE -> stringResource(R.string.m3_new_issue)
        Routes.NEW_PULL -> stringResource(R.string.m3_new_pull)
        Routes.COMMIT -> stringResource(R.string.m3_commit)
            .plus(" " + (entry?.arguments?.getString("sha")?.take(7) ?: ""))
        Routes.COMMITS -> stringResource(R.string.m3_commits)
        Routes.DISCUSSIONS -> stringResource(R.string.m3_title_discussions)
        Routes.DISCUSSION -> "#${entry?.arguments?.getInt("number") ?: 0}"
        Routes.NEW_DISCUSSION -> stringResource(R.string.m3_new_discussion)
        Routes.ACTIONS -> stringResource(R.string.m3_title_actions)
        Routes.LABELS -> stringResource(R.string.m3_labels)
        Routes.MILESTONES -> stringResource(R.string.m3_milestones)
        Routes.RUN_LOGS -> stringResource(R.string.m3_actions_logs)
        else -> "GitRehub"
    }
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onMenu) {
                Icon(Icons.Outlined.Menu, contentDescription = stringResource(R.string.m3_menu))
            }
        },
        actions = {
            actions()
            val context = LocalContext.current
            // The issue screen reports whether the thread is a PR once it loads; the share link
            // uses /pull/N then instead of /issues/N.
            val pullFlow = remember(entry) {
                entry?.savedStateHandle?.getStateFlow("is_pull", false) ?: MutableStateFlow(false)
            }
            val isPull by pullFlow.collectAsStateWithLifecycle()
            val shareUrl = remember(route, entry, isPull) { shareUrlFor(route, entry, isPull) }
            if (shareUrl != null) {
                IconButton(onClick = { shareText(context, shareUrl) }) {
                    Icon(
                        Icons.Outlined.Share,
                        contentDescription = stringResource(R.string.m3_share),
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}


/** Canonical github.com URL of the screen on top, for the share action in the top bar. */
private fun shareUrlFor(
    route: String?,
    entry: androidx.navigation.NavBackStackEntry?,
    isPull: Boolean = false,
): String? {
    val args = entry?.arguments ?: return null
    val owner = args.getString("owner")
    val repo = args.getString("repo") ?: args.getString("name")
    if (route == Routes.USER) {
        return args.getString("login")?.takeIf { it.isNotBlank() }?.let { "https://github.com/$it" }
    }
    if (route == Routes.GIST) {
        return args.getString("id")?.takeIf { it.isNotBlank() }
            ?.let { "https://gist.github.com/$it" }
    }
    if (owner.isNullOrBlank() || repo.isNullOrBlank()) return null
    val base = "https://github.com/$owner/$repo"
    val number = args.getInt("number")
    val sha = args.getString("sha").orEmpty()
    val ref = args.getString("ref").orEmpty().ifBlank { "HEAD" }
    val path = args.getString("path").orEmpty().trim('/')
    val encodedPath = path.split('/').filter { it.isNotEmpty() }.joinToString("/") { Uri.encode(it) }
    return when (route) {
        Routes.REPO -> base
        Routes.ISSUES -> if (args.getString("kind") == "pull") "$base/pulls" else "$base/issues"
        Routes.ISSUE -> if (isPull) "$base/pull/$number" else "$base/issues/$number"
        Routes.PR_FILES -> "$base/pull/$number/files"
        Routes.COMMIT -> "$base/commit/$sha"
        Routes.COMMITS -> "$base/commits/$ref"
        Routes.RELEASES -> "$base/releases"
        Routes.BRANCHES -> if (args.getString("kind") == "tag") "$base/tags" else "$base/branches"
        Routes.CODE -> if (encodedPath.isBlank()) "$base/tree/$ref" else "$base/tree/$ref/$encodedPath"
        Routes.FILE -> "$base/blob/$ref/$encodedPath"
        Routes.FILE_HISTORY -> "$base/commits/$ref/$encodedPath"
        Routes.DISCUSSIONS -> "$base/discussions"
        Routes.DISCUSSION -> "$base/discussions/$number"
        Routes.ACTIONS -> "$base/actions"
        else -> null
    }
}

@Composable
private fun DrawerHeader(account: StoredAccount?) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Avatar(account?.avatarUrl, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = account?.name ?: account?.login ?: stringResource(R.string.m3_not_signed_in),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = account?.login?.let { "@$it" } ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Hairline()
    }
}

private fun NavHostController.navigateSingleTop(route: String) {    navigate(route) {
        popUpTo(Routes.FEED) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.openRepo(fullName: String) {
    val parts = fullName.split("/")
    if (parts.size != 2) return
    // Tapping the download bar while already on that repository should not stack a copy.
    val args = currentBackStackEntry?.arguments
    if (currentDestination?.route == Routes.REPO &&
        args?.getString("owner") == parts[0] && args?.getString("name") == parts[1]
    ) {
        return
    }
    navigate(Routes.repo(parts[0], parts[1]))
}

/** Compose hands out a theme-wrapped context often enough that a plain cast would miss. */
private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

/**
 * Device-code login needs our own GitHub OAuth App with Device Flow enabled; with no client
 * id configured the entry point is hidden (see README / app/build.gradle.kts).
 */
private fun deviceLoginRoute(nav: NavHostController, route: String): (() -> Unit)? =
    if (GithubConfigHelper.isConfigured) {
        { nav.navigate(route) }
    } else {
        null
    }

/**
 * The one-time offer to make github.com links open here instead of in the browser; the button
 * goes straight to the system page where the switch lives.
 * straight to the system page where the switch lives — the user should not have to go hunting.
 */
@Composable
private fun LinkHandlingDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.m3_link_prompt_title)) },
        text = { Text(stringResource(R.string.m3_link_prompt_message)) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.m3_link_prompt_open)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.m3_link_prompt_later)) }
        },
    )
}

/**
 * Ask once after login whether to enable notifications (the permission used to be
 * reachable only from the settings page).
 */
@Composable
private fun NotificationAskDialog(
    onAllow: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.m3_notif_prompt_title)) },
        text = { Text(stringResource(R.string.m3_notif_prompt_message)) },
        confirmButton = {
            TextButton(onClick = onAllow) { Text(stringResource(R.string.m3_notif_prompt_allow)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.m3_notif_prompt_later)) }
        },
    )
}

/** Rail for tablets and foldables: same five destinations and badges as the bottom bar. */
@Composable
private fun AppNavigationRail(
    current: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val notificationsBadge by TabBadges.notifications.collectAsStateWithLifecycle()
    val myWorkBadge by TabBadges.myWork.collectAsStateWithLifecycle()
    NavigationRail(modifier = modifier) {
        tabs.forEach { tab ->
            val label = stringResource(tab.labelRes)
            val badge = when (tab.route) {
                Routes.NOTIFICATIONS -> notificationsBadge
                Routes.MY_WORK -> myWorkBadge
                else -> 0
            }
            NavigationRailItem(
                selected = current == tab.route,
                onClick = { onNavigate(tab.route) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (badge > 0) {
                                Badge { Text(if (badge > 99) "99+" else badge.toString()) }
                            }
                        },
                    ) { Icon(tab.icon, contentDescription = label) }
                },
                label = { Text(label) },
            )
        }
    }
}
