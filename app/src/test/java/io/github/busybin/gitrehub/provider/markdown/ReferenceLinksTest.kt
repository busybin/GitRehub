package io.github.busybin.gitrehub.provider.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceLinksTest {

    @Test
    fun `local issue reference links to the current repository`() {
        val out = ReferenceLinks.expand("Fixes #12.", "acme/app")
        assertEquals("Fixes [#12](https://github.com/acme/app/issues/12).\n", out)
    }

    @Test
    fun `cross repository reference keeps its own slug`() {
        val out = ReferenceLinks.expand("See acme/lib#7.", "acme/app")
        assertTrue(out.contains("[acme/lib#7](https://github.com/acme/lib/issues/7)"))
    }

    @Test
    fun `code spans and fences are left untouched`() {
        val source = "Use `#1` here\n```\n#2\n```\n"
        val out = ReferenceLinks.expand(source, "acme/app")
        assertFalse(out.contains("issues/1"))
        assertFalse(out.contains("issues/2"))
    }

    @Test
    fun `local references need a repository`() {
        val out = ReferenceLinks.expand("Fixes #12", null)
        assertEquals("Fixes #12", out)
    }

    @Test
    fun `urls are not rewritten`() {
        val url = "https://github.com/acme/app#readme"
        val out = ReferenceLinks.expand(url, "acme/app")
        assertEquals(url + "\n", out)
    }
}
