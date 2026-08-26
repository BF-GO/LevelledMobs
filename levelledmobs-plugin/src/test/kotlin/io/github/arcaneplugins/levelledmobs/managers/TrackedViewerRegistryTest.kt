package io.github.arcaneplugins.levelledmobs.managers

import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrackedViewerRegistryTest {
    @Test
    fun `track is idempotent and untrack removes only the requested pair`() {
        val registry = TrackedViewerRegistry<String>()
        val entityId = UUID.randomUUID()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()

        registry.track(entityId, first, "first")
        registry.track(entityId, first, "first")
        registry.track(entityId, second, "second")

        assertEquals(setOf("first", "second"), registry.values(entityId).toSet())
        assertFalse(registry.untrack(entityId, first))
        assertTrue(registry.contains(entityId, second))
        assertTrue(registry.untrack(entityId, second))
        assertEquals(0, registry.entityCount())
    }

    @Test
    fun `parallel track and disconnect cleanup do not throw or corrupt the registry`() {
        val registry = TrackedViewerRegistry<Int>()
        val viewerId = UUID.randomUUID()
        val executor = Executors.newFixedThreadPool(8)
        val entityIds = List(500) { UUID.randomUUID() }

        try {
            entityIds.forEachIndexed { index, entityId ->
                executor.submit { registry.track(entityId, viewerId, index) }
            }
            executor.shutdown()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
        }

        assertEquals(500, registry.entityCount())
        assertEquals(entityIds.toSet(), registry.removeViewer(viewerId))
        assertEquals(0, registry.entityCount())
    }
}
