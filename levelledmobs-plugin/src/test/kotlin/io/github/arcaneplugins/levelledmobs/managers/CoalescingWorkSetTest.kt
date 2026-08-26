package io.github.arcaneplugins.levelledmobs.managers

import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoalescingWorkSetTest {
    @Test
    fun `duplicate UUID is accepted exactly once and can be rescheduled after completion`() {
        val work = CoalescingWorkSet<UUID>()
        val entityId = UUID.randomUUID()

        assertTrue(work.tryAcquire(entityId))
        repeat(20) { assertFalse(work.tryAcquire(entityId)) }
        assertEquals(1, work.size())
        assertEquals(20, work.coalescedCount())

        work.release(entityId)
        assertTrue(work.tryAcquire(entityId))
    }

    @Test
    fun `parallel duplicate events have one owner without state loss`() {
        val work = CoalescingWorkSet<UUID>()
        val entityId = UUID.randomUUID()
        val start = CountDownLatch(1)
        val accepted = AtomicInteger()
        val executor = Executors.newFixedThreadPool(8)

        try {
            repeat(128) {
                executor.submit {
                    start.await()
                    if (work.tryAcquire(entityId)) accepted.incrementAndGet()
                }
            }
            start.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
        }

        assertEquals(1, accepted.get())
        assertEquals(1, work.size())
        assertEquals(127, work.coalescedCount())
    }

    @Test
    fun `oldest pending age is observable`() {
        val clock = AtomicLong(1_000L)
        val work = CoalescingWorkSet<UUID>(clock::get)
        work.tryAcquire(UUID.randomUUID())
        clock.set(1_250L)

        assertEquals(250L, work.oldestAgeMillis())
    }

    @Test
    fun `stale completion cannot release a newer lease`() {
        val work = CoalescingWorkSet<UUID>()
        val entityId = UUID.randomUUID()
        val oldLease = requireNotNull(work.tryAcquireLease(entityId))

        work.clear()
        val newLease = requireNotNull(work.tryAcquireLease(entityId))
        work.release(oldLease)

        assertEquals(1, work.size())
        work.release(newLease)
        assertEquals(0, work.size())
    }
}
