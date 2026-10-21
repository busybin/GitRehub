# Distribution (GitHub Releases / IzzyOnDroid / F-Droid)

| Channel | Built by | Signed by | Status |
|---|---|---|---|
| GitHub Releases | us | us (`app/keys_release.jks`) | in use |
| IzzyOnDroid | us, attached to the tag's release | us | recommended |
| F-Droid main repo | F-Droid build machines | F-Droid (unless reproducible builds) | optional, see below |

## Release preparation

1. The tag is a plain number (= `versionCode`, starting at `1`), pushed to `busybin/gitrehub`.
2. Build the release APK, verify the signing certificate, and attach it to the tag's GitHub release.
3. `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` holds the changelog for each release.
4. Submission: file an IzzyOnDroid issue (<https://codeberg.org/IzzyOnDroid/repodata/issues>), or open
   an fdroiddata MR following [`io.github.busybin.gitrehub.yml`](./io.github.busybin.gitrehub.yml).

## Anti-features

- **NonFreeNet** — the app exists to access GitHub, a non-free network service, with no alternative.
- **Tracking does not apply** — the update check is off by default (and can be enabled in settings):
  while off, startup sends nothing; only tapping "check" or enabling the toggle reads the public
  releases page. F-Droid accepts this as opt-in.

## Release signing

- `app/keys_release.jks` and `app/secrets.properties` (which also holds the OAuth client id) are both
  gitignored and kept on the build machine plus a private backup.
- Certificate SHA-256: `c42c7adc15075ed7349ef5600dd03ddb45177cf82f615534d8309bc0633f53bc`
- Alias `gitrehub`, RSA 4096, valid until 2054-03-05.
- `app/keys_debug.jks` is a debug keystore shipped in the repo on purpose (stable signature, so
  overwrite installs work); it is used for debug builds only, whose package carries a `.debug` suffix
  and coexists with the release build.
- **Changing the key breaks overwrite installs** — keep this key forever.
