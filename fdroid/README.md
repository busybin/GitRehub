# Distribution

| Channel | Built by | Signed by | Notes |
|---|---|---|---|
| GitHub Releases | us | us (`app/keys_release.jks`) | main channel |
| Self-hosted F-Droid repo | us (`fdroid update`) | APKs: us; index: repo key | same APK signature, so users can move over from GitHub without uninstalling |
| F-Droid main repo (optional) | F-Droid build machines | F-Droid | re-signed, so it cannot upgrade over (or be updated by) the other two; the inclusion policy asks for ≥3 months of public development |
| IzzyOnDroid | — | — | not used: its inclusion policy rejects LLM-generated code and the request template requires AI disclosure |
| Google Play | — | — | not used |

## Release preparation

1. Bump `versionCode` / `versionName` (same plain integer) and add
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
2. Build `assembleRelease`, check `apksigner verify --print-certs` against the fingerprint below,
   tag the commit with that integer and attach the APK to the GitHub release.
3. Update `fdroid/io.github.busybin.gitrehub.yml` (`Builds`, `CurrentVersion`, `CurrentVersionCode`)
   with the tag's full commit hash.
4. Screenshots live in `fastlane/metadata/android/en-US/images/phoneScreenshots/` (at least one).
5. Re-run `fdroid update` for the self-hosted repo so the new version is in the index.

## Building without our key (F-Droid)

`app/keys_release.jks` and `app/secrets.properties` are git-ignored. When the key is absent the
release build is left **unsigned** (never falls back to the debug key), which is what an F-Droid
build needs. The F-Droid variant is built with `-Pgitrehub.store=fdroid`: that compiles the in-app
APK updater out, because an APK signed by us can never be installed over a re-signed build. Builds
signed with the release key additionally verify the certificate at runtime before showing the
updater.

The OAuth client id also lives in the git-ignored properties file. A from-source F-Droid build has
none: the device-code login entry is hidden, token login and everything else works.

## Self-hosted F-Droid repo

One repository hosts several apps (Deluge, GitRehub, Leftover, IRL Noob) and keeps every APK
signature: only the index is signed with a separate repo key. The F-Droid client can install and
update any of them from it.

## Anti-features

- **NonFreeNet** — the app exists to access GitHub, a non-free network service, with no alternative.
- **Tracking does not apply** — the update check is off by default; while it is off, startup sends
  nothing. Only tapping "check" or enabling the toggle reads the public releases page (opt-in).

## Release signing

- `app/keys_release.jks` + `app/secrets.properties` are gitignored and backed up outside the repo.
- Certificate SHA-256: `c42c7adc15075ed7349ef5600dd03ddb45177cf82f615534d8309bc0633f53bc`
- Alias `gitrehub`, RSA 4096, valid until 2054-03-05.
- `app/keys_debug.jks` is a debug keystore shipped on purpose (stable signature for `install -r`);
  release builds never use it.
- **Changing the key breaks overwrite installs** — keep this key forever.
- **Switching to the F-Droid main-repo build requires uninstalling the GitHub/self-hosted build**
  (different signature), which deletes the app's accounts and settings.

## F-Droid main repo submission

Copy `io.github.busybin.gitrehub.yml` to `metadata/io.github.busybin.gitrehub.yml` in an fdroiddata
fork, run `fdroid lint` and open a "New App" merge request. The source repository must be public.
