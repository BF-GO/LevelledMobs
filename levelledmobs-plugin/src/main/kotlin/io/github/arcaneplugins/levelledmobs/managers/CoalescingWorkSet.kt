package io.github.arcaneplugins.levelledmobs.managers

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Thread-safe ownership set for work which may only be pending once per key. */
internal class CoalescingWorkSet<K>(
    private val clock: () -> Long = System::currentTimeMillis
) {
    class Lease<K>(val key: K, val acquiredAt: Long)

    private val pending = ConcurrentHashMap<K, Lease<K>>()
    private val coalesced = AtomicLong()

    fun tryAcquireLease(key: K): Lease<K>? {
        val lease = Lease(key, clock())
        if (pending.putIfAbsent(key, lease) == null)
            return lease

        coalesced.incrementAndGet()
        return null
    }

    fun tryAcquire(key: K): Boolean = tryAcquireLease(key) != null

    fun release(lease: Lease<K>) {
        pending.remove(lease.key, lease)
    }

    fun release(key: K) {
        pending.remove(key)
    }

    fun clear() = pending.clear()

    fun size(): Int = pending.size

    fun coalescedCount(): Long = coalesced.get()

    fun oldestAgeMillis(): Long {
        val oldest = pending.values.minOfOrNull(Lease<K>::acquiredAt) ?: return 0L
        return (clock() - oldest).coerceAtLeast(0L)
    }
}
