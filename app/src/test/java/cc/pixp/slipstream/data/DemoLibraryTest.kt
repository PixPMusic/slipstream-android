package cc.pixp.slipstream.data

import org.junit.Assert.*
import org.junit.Test

class DemoLibraryTest {
    @Test fun searchMatchesCreatorAndTrimsWhitespace() {
        val posts = DemoLibrary.filter(null, "  FIELD NOTES  ")
        assertEquals(3, posts.size)
        assertTrue(posts.all { it.creator.id == "fieldnotes" })
    }
    @Test fun creatorAndSearchFiltersIntersect() {
        assertEquals(listOf("demo-2"), DemoLibrary.filter("studio", "camera").map { it.id })
        assertTrue(DemoLibrary.filter("fieldnotes", "camera").isEmpty())
    }
    @Test fun clearingSearchRestoresLibrary() {
        assertTrue(DemoLibrary.filter(null, "nonexistent title").isEmpty())
        assertEquals(6, DemoLibrary.filter(null, "").size)
    }
}
