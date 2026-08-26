package io.github.arcaneplugins.levelledmobs.managers

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Concurrent UUID registry; values are only dereferenced on their owner scheduler. */
internal class TrackedViewerRegistry<V> {
    private val viewers = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, V>>()

    fun track(entityId: UUID, viewerId: UUID, viewer: V) {
        viewers.compute(entityId) { _, current ->
            val entityViewers = current ?: ConcurrentHashMap()
            entityViewers[viewerId] = viewer
            entityViewers
        }
    }

    /** Returns true when the entity no longer has viewers. */
    fun untrack(entityId: UUID, viewerId: UUID): Boolean {
        var empty = true
        viewers.computeIfPresent(entityId) { _, entityViewers ->
            entityViewers.remove(viewerId)
            empty = entityViewers.isEmpty()
            if (empty) null else entityViewers
        }
        return empty
    }

    /** Returns entity IDs whose final viewer was removed. */
    fun removeViewer(viewerId: UUID): Set<UUID> {
        val emptied = HashSet<UUID>()
        viewers.keys.forEach { entityId ->
            viewers.computeIfPresent(entityId) { _, entityViewers ->
                entityViewers.remove(viewerId)
                if (entityViewers.isEmpty()) {
                    emptied.add(entityId)
                    null
                } else {
                    entityViewers
                }
            }
        }
        return emptied
    }

    fun removeEntity(entityId: UUID) {
        viewers.remove(entityId)
    }

    fun get(entityId: UUID, viewerId: UUID): V? = viewers[entityId]?.get(viewerId)

    fun contains(entityId: UUID, viewerId: UUID): Boolean =
        viewers[entityId]?.containsKey(viewerId) == true

    fun values(entityId: UUID): List<V> = viewers[entityId]?.values?.toList().orEmpty()

    fun entityCount(): Int = viewers.size

    fun clear() = viewers.clear()
}
