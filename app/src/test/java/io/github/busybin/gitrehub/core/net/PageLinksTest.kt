package io.github.busybin.gitrehub.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageLinksTest {

    @Test
    fun `reads next and last page numbers`() {
        val links = PageLinks.parse(
            "<https://api.github.com/user/repos?per_page=1&page=2>; rel=\"next\", " +
                "<https://api.github.com/user/repos?per_page=1&page=57>; rel=\"last\""
        )
        assertEquals(2, links.next)
        assertEquals(57, links.last)
    }

    @Test
    fun `last page only`() {
        val links = PageLinks.parse("<https://api.github.com/x?page=1&per_page=30>; rel=\"last\"")
        assertNull(links.next)
        assertEquals(1, links.last)
    }

    @Test
    fun `missing or malformed header`() {
        assertEquals(PageLinks(), PageLinks.parse(null))
        assertEquals(PageLinks(), PageLinks.parse(""))
        assertEquals(PageLinks(), PageLinks.parse("garbage"))
    }
}
