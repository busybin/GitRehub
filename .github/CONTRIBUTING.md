# Contributing to GitRehub

Contributions are welcome — bug reports, translations and pull requests.

## Issues

- Search existing issues first; duplicate reports get closed.
- Describe what you did, what you expected and what happened. A logcat excerpt helps a lot.

## Pull requests

- One topic per pull request, and keep the existing code style.
- Build it before you send it:

  ```sh
  ./gradlew assembleDebug
  ./gradlew testDebugUnitTest
  ```

- Add a JVM unit test for pure logic (`app/src/test`). Anything that needs a real `Context`,
  ObjectBox or Compose goes to `app/src/androidTest` instead.

## Translations

`app/src/main/res/values-<locale>/strings.xml` — copy the keys you want to translate from
`values/strings.xml` and keep the `%s`/`%d` placeholders intact. Do not translate GitHub terms
(Issue, Pull Request, Commit, Fork, Release…).

## Accounts and credentials

Do not commit credentials. `app/secrets.properties` is git-ignored and is the only place for OAuth
and Imgur keys.
