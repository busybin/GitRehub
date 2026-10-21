# Distribution (GitHub Releases / IzzyOnDroid / F-Droid)

| Channel | Built by | Signed by | Notes |
|---|---|---|---|
| GitHub Releases | us | us (`app/keys_release.jks`) | main channel |
| IzzyOnDroid | us, APK attached to the tag's release | us | keeps our signature, so it updates over the GitHub build |
| F-Droid main repo | F-Droid build machines | F-Droid | re-signed, so it cannot update over (or be updated by) the other two channels |

## Release preparation

1. Bump `versionCode` / `versionName` (same plain integer) and add
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
2. Build `assembleRelease`, check `apksigner verify --print-certs` against the fingerprint below,
   tag the commit with that integer and attach the APK to the GitHub release.
3. Update `fdroid/io.github.busybin.gitrehub.yml` (`Builds`, `CurrentVersion`, `CurrentVersionCode`)
   with the tag's full commit hash.
4. Screenshots live in `fastlane/metadata/android/en-US/images/phoneScreenshots/` (at least one).

## Building without our key (F-Droid)

`app/keys_release.jks` and `app/secrets.properties` are git-ignored. When the key is absent the
release build is left **unsigned** (never falls back to the debug key), which is what the F-Droid
recipe needs. The recipe also passes `-Pgitrehub.store=fdroid`: that compiles the in-app APK updater
out, because an APK signed by us can never be installed over F-Droid's re-signed build. Builds signed
with the release key additionally verify the certificate at runtime before showing the updater.

The OAuth client id also lives in the git-ignored properties file. A from-source F-Droid build has
none: the device-code login entry is hidden, token login and everything else works.

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
- **Switching to the F-Droid build requires uninstalling the GitHub/Izzy build** (different
  signature), which deletes the app's accounts and settings.

## F-Droid submission

Copy `io.github.busybin.gitrehub.yml` to `metadata/io.github.busybin.gitrehub.yml` in an fdroiddata
fork, run `fdroid lint` and open a "New App" merge request. The source repository must be public.
