package io.github.busybin.gitrehub.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Relative image paths in GitHub's rendered README HTML. This is the bug behind "README images
 * do not show": the API leaves `<img src="screenshots/home.png">` relative and an image loader has
 * no host to hit.
 */
class WebLinksTest {

    private val base = "https://raw.githubusercontent.com/busybin/DelugeLauncher/HEAD/"

    @Test
    fun relativePathsAreJoinedOntoTheBase() {
        assertEquals(base + "screenshots/home.png", WebLinks.resolve(base, "screenshots/home.png"))
        assertEquals(base + ".github/assets/logo.png", WebLinks.resolve(base, ".github/assets/logo.png"))
        // `?raw=true` style suffixes survive
        assertEquals(base + "a/b.png?raw=true", WebLinks.resolve(base, "a/b.png?raw=true"))
        // a leading "./" is noise
        assertEquals(base + "x.png", WebLinks.resolve(base, "./x.png"))
    }

    @Test
    fun baseWithoutTrailingSlashStillWorks() {
        assertEquals(
            "https://raw.githubusercontent.com/o/r/HEAD/x.png",
            WebLinks.resolve("https://raw.githubusercontent.com/o/r/HEAD", "x.png"),
        )
    }

    @Test
    fun absoluteUrlsAreUntouched() {
        val avatar = "https://avatars.githubusercontent.com/u/1?v=4"
        assertEquals(avatar, WebLinks.resolve(base, avatar))
        val camo = "https://camo.githubusercontent.com/abc/68747470"
        assertEquals(camo, WebLinks.resolve(base, camo))
        assertEquals("//cdn.example.com/x.png", WebLinks.resolve(base, "//cdn.example.com/x.png"))
        assertEquals("data:image/png;base64,AAAA", WebLinks.resolve(base, "data:image/png;base64,AAAA"))
        assertEquals("mailto:someone@example.com", WebLinks.resolve(base, "mailto:someone@example.com"))
        assertEquals("#anchor", WebLinks.resolve(base, "#anchor"))
    }

    @Test
    fun siteRootPathsGoToGithub() {
        assertEquals("https://github.com/busybin/gitrehub", WebLinks.resolve(base, "/busybin/gitrehub"))
    }

    /**
     * Relative links in README bodies (the GitHub API leaves them relative too). The resolved shape
     * must be exactly what `SchemeParser.routeFor` recognises: `github.com/{o}/{r}/blob/<ref>/…`.
     */
    @Test
    fun relativeLinksInAReadmeResolveAgainstTheBlobUrl() {
        val blob = "https://github.com/busybin/DelugeLauncher/blob/main/"
        assertEquals(blob + "docs/ARCHITECTURE.md", WebLinks.resolve(blob, "docs/ARCHITECTURE.md"))
        assertEquals(blob + "LICENSE", WebLinks.resolve(blob, "LICENSE"))
        assertEquals("https://github.com/adelmonte", WebLinks.resolve(blob, "https://github.com/adelmonte"))
        assertEquals("mailto:x@y.z", WebLinks.resolve(blob, "mailto:x@y.z"))
        assertEquals("#install", WebLinks.resolve(blob, "#install"))
    }

    @Test
    fun withoutABaseNothingIsInvented() {
        assertEquals("screenshots/home.png", WebLinks.resolve(null, "screenshots/home.png"))
        assertEquals("screenshots/home.png", WebLinks.resolve("", "screenshots/home.png"))
        assertEquals(null, WebLinks.resolve(base, null))
    }

    @Test
    fun isAbsoluteRecognisesSchemes() {
        assertTrue(WebLinks.isAbsolute("https://a/b"))
        assertTrue(WebLinks.isAbsolute("data:image/png;base64,x"))
        assertFalse(WebLinks.isAbsolute("screenshots/home.png"))
        assertFalse(WebLinks.isAbsolute("/busybin/gitrehub"))
        // a colon inside a path must not read as a scheme
        assertFalse(WebLinks.isAbsolute("a b/c.png"))
    }
}
