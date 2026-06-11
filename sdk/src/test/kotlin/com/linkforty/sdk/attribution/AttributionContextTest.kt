package com.linkforty.sdk.attribution

import com.linkforty.sdk.testhelpers.MockStorageManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AttributionContextTest {

    private lateinit var storage: MockStorageManager

    @BeforeEach
    fun setUp() {
        storage = MockStorageManager()
    }

    @Test
    fun `fresh context has a session but no link`() {
        val ctx = AttributionContext(storage)
        val stamp = ctx.getStamp()
        assertTrue(stamp.sessionId.isNotEmpty())
        assertNull(stamp.attributedLinkId)
        assertNull(stamp.attributedClickId)
        assertNull(stamp.linkOpenedAt)
    }

    @Test
    fun `recordDeepLinkOpen stamps the link`() {
        val ctx = AttributionContext(storage)
        ctx.recordDeepLinkOpen("link-A", "click-1")

        val stamp = ctx.getStamp()
        assertEquals("link-A", stamp.attributedLinkId)
        assertEquals("click-1", stamp.attributedClickId)
        assertNotNull(stamp.linkOpenedAt)
    }

    @Test
    fun `newest open supersedes and rotates the session`() {
        val ctx = AttributionContext(storage)
        ctx.recordDeepLinkOpen("link-A")
        val first = ctx.getStamp()

        ctx.recordDeepLinkOpen("link-B")
        val second = ctx.getStamp()

        assertEquals("link-B", second.attributedLinkId) // newest wins
        assertNotEquals(first.sessionId, second.sessionId) // session rotates
    }

    @Test
    fun `organic open (no linkId) is a no-op`() {
        val ctx = AttributionContext(storage)
        ctx.recordDeepLinkOpen("link-A")
        val sessionAfterLink = ctx.getStamp().sessionId

        ctx.recordDeepLinkOpen(null)
        val stamp = ctx.getStamp()

        assertEquals("link-A", stamp.attributedLinkId)
        assertEquals(sessionAfterLink, stamp.sessionId)
    }

    @Test
    fun `active context persists across instances and starts a new session`() {
        val first = AttributionContext(storage)
        first.recordDeepLinkOpen("link-A")

        val second = AttributionContext(storage)
        val stamp = second.getStamp()

        assertEquals("link-A", stamp.attributedLinkId)
        assertNotEquals(first.getStamp().sessionId, stamp.sessionId)
    }

    @Test
    fun `clear removes the link and rotates the session`() {
        val ctx = AttributionContext(storage)
        ctx.recordDeepLinkOpen("link-A")
        val before = ctx.getStamp().sessionId

        ctx.clear()
        val stamp = ctx.getStamp()

        assertNull(stamp.attributedLinkId)
        assertNotEquals(before, stamp.sessionId)

        // Cleared state must not be restored by a new instance.
        val reopened = AttributionContext(storage)
        assertNull(reopened.getStamp().attributedLinkId)
    }
}
