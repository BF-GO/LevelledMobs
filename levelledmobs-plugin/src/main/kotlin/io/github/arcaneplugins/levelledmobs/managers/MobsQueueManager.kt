package io.github.arcaneplugins.levelledmobs.managers

import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.debug.DebugManager
import io.github.arcaneplugins.levelledmobs.debug.DebugType
import io.github.arcaneplugins.levelledmobs.misc.EvaluationException
import io.github.arcaneplugins.levelledmobs.misc.QueueItem
import io.github.arcaneplugins.levelledmobs.util.LocalizedMessages
import java.util.UUID
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/**
 * Coalesces mob levelling work and executes it on the mob's owning region.
 *
 * A UUID has at most one scheduled or running job. This replaces the polling
 * worker threads which could queue the same mob once per nearby player.
 */
class MobsQueueManager {
    private val pendingWork = CoalescingWorkSet<UUID>()
    @Volatile private var acceptingWork = false

    var ignoreMobsWithNoPlayerContext = false

    fun start() {
        acceptingWork = true
    }

    fun stop() {
        acceptingWork = false
        pendingWork.clear()
    }

    fun getNumberQueued(): Int = pendingWork.size()

    fun getCoalescedJobs(): Long = pendingWork.coalescedCount()

    fun getOldestPendingAgeMillis(): Long = pendingWork.oldestAgeMillis()

    fun clearQueue() {
        pendingWork.clear()
    }

    /** Kept for binary/source compatibility with the old watchdog. */
    fun taskChecker() = Unit

    fun addToQueue(item: QueueItem) {
        if (!acceptingWork) return

        item.lmEntity.inUseCount.incrementAndGet()
        val lease = pendingWork.tryAcquireLease(item.entityId)
        if (lease == null) {
            item.lmEntity.free()
            return
        }

        val finished = AtomicBoolean()
        val finish = Runnable {
            if (!finished.compareAndSet(false, true)) return@Runnable
            pendingWork.release(lease)
            item.lmEntity.free()
        }

        item.lmEntity.livingEntity.scheduler.run(
            LevelledMobs.instance,
            Consumer {
                try {
                    processItem(item)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                } finally {
                    finish.run()
                }
            },
            finish
        )
    }

    private fun processItem(item: QueueItem) {
        if (!item.lmEntity.isPopulated) return

        if (ignoreMobsWithNoPlayerContext && item.lmEntity.associatedPlayer == null) {
            DebugManager.log(DebugType.PLAYER_CONTEXT, item.lmEntity) {
                val locationStr = LocalizedMessages.text("command.levelledmobs.debug.runtime.d095", mapOf("value-1" to item.lmEntity.location.blockX), false) +
                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d096", mapOf("value-1" to item.lmEntity.location.blockY), false) +
                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d097", mapOf("value-1" to item.lmEntity.location.blockZ), false) +
                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d098", mapOf("value-1" to item.lmEntity.location.world.name), false)
                LocalizedMessages.text("command.levelledmobs.debug.runtime.d099", mapOf("value-1" to item.lmEntity.nameIfBaby, "value-2" to locationStr), false)
            }
            return
        }

        try {
            LevelledMobs.instance.levelManager.entitySpawnListener.processMob(item.lmEntity, item.event)
        } catch (_: EvaluationException) {
            // The expression engine already logged the actionable error.
        } catch (_: TimeoutException) {
            DebugManager.log(DebugType.APPLY_LEVEL_RESULT, item.lmEntity, false) {
                LocalizedMessages.text("command.levelledmobs.debug.runtime.d100", colorize = false)
            }
        }
    }
}
