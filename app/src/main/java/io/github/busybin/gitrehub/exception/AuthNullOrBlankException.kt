package io.github.busybin.gitrehub.exception

import java.io.IOException

/** Raised when `/user` is requested without a token, i.e. nobody is signed in. */
class AuthNullOrBlankException : IOException("No authentication token")
