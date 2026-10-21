[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Releases](https://img.shields.io/github/release/busybin/gitrehub.svg)](https://github.com/busybin/gitrehub/releases/latest)

# GitRehub

An open-source GitHub client for Android with a modern **Material 3 / Jetpack Compose** interface.

## Features

**Interface**
- Material 3, dark only, nine colour themes (GitHub, Follow wallpaper, One Dark, Dracula, Nord, Catppuccin, Solarized, Gruvbox, Tokyo Night), four font sizes, comfortable/compact density
- Single-activity Compose UI with a navigation drawer, bottom bar, and two-pane layouts on tablets

**Activity feed**
- One merged stream: your received events, your own activity, and what other people did to *your* repositories (stars, forks, PRs, issues)
- **Filters**: event type, time range, source, read state, ordering, per-author, per-repository, keyword block-list, hide bots. Everything is saved and survives a restart; only “Clear filters” resets it
- “Mark all as read” shared with the notifications screen

**Repositories, issues, pull requests**
- Repository browser: rendered README, code browsing (directories, files, branches, tags) with line numbers and in-file search, releases with downloadable assets, activity and contributor pages
- Native issue/PR detail pages: labels, comments (edit and delete), reactions, assignees, milestones, lock, reviews, and comment/close/merge actions where your permissions allow
- Notifications with filtering, custom swipe actions, and mark-all-as-read
- Search repositories, users, issues/PRs and code; a My Work tab with your issues, PRs, reviews and mentions

**Accounts and data**
- Access-token login, device-code login (needs your own OAuth app), multiple accounts with switching, profile editing
- Offline cache (OkHttp disk cache) so what you browsed still shows without a network
- Update check against GitHub Releases (manual + opt-in automatic check)
- No ads, no trackers, no Google Play Services

## Install

Download the APK from [Releases](https://github.com/busybin/gitrehub/releases/latest) and sideload it.
Android 8.0 (API 26) or newer.

## Build

Gradle 9.8.1 / AGP 9.4.0, `compileSdk 37`, `minSdk 26`, Java 17.

```sh
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest  # JVM tests
```

The repository ships **no** OAuth or Imgur credentials. Plain access-token login needs none. To enable
device-code login, create your own GitHub OAuth App (with *Enable Device Flow* ticked) and put the
credentials in `app/secrets.properties` (not committed):

```properties
GITHUB_CLIENT_ID=…
GITHUB_SECRET=…
# optional, only for image uploads
IMGUR_CLIENT_ID=…
IMGUR_SECRET=…
```

### Versioning and release signing

No `V` prefix: `versionName` and `versionCode` are the same plain number, so the next
release after `1` is `2`, tagged `2`.

Release builds are signed from a git-ignored `app/keys_release.jks` (with `PASSWORD` /
`KEY_ALIAS` in `app/secrets.properties`). Without it a release build is left unsigned and prints a
warning — that is what the a third-party store build machine gets; it never falls back to debug signing. The
from-source build also builds with `-Pgitrehub.store=external`, which disables the in-app APK updater
(a third-party store re-signs, so our APK could not be installed over it); builds signed with the release key
verify the certificate at runtime before offering an update. See
[the build docs](the build docs) for the key fingerprint, the anti-features this app carries on
a third-party store, and the release checklist.

## Issues

Report bugs and feature requests at <https://github.com/busybin/gitrehub/issues>.

## License

GitRehub is free software under the **GNU General Public License v3.0** — see [LICENSE](LICENSE)
for the full text. Bundled third-party components and their licenses are listed in
[THIRD-PARTY.md](THIRD-PARTY.md).
