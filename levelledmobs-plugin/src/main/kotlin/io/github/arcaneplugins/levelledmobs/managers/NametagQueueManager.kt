package io.github.arcaneplugins.levelledmobs.managers

import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.enums.NametagVisibilityEnum
import io.github.arcaneplugins.levelledmobs.misc.QueueItem
import io.github.arcaneplugins.levelledmobs.nametag.NametagSender
import io.github.arcaneplugins.levelledmobs.nametag.NametagSenderHandler
import io.github.arcaneplugins.levelledmobs.result.NametagResult
import io.github.arcaneplugins.levelledmobs.util.LibsDisguisesUtils
import io.github.arcaneplugins.levelledmobs.util.LocalizedMessages
import io.github.arcaneplugins.levelledmobs.util.MessageUtils
import io.github.arcaneplugins.levelledmobs.util.Utils
import io.github.arcaneplugins.levelledmobs.wrappers.LivingEntityWrapper
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player

/**
 * Event-driven nametag dispatcher.
 *
 * Viewers come from Paper's track/untrack events. Updates for the same mob are
 * coalesced for one tick and executed on the mob's owning region.
 */
class NametagQueueManager {
    private data class ViewerKey(val entityId: UUID, val playerId: UUID)

    private class PendingNametag(
        val item: QueueItem,
        players: Collection<Player>
    ) {
        val players = ConcurrentHashMap<UUID, Player>()
        val latestNametag = AtomicReference<NametagResult>(item.nametag!!)
        val completed = AtomicBoolean()

        init {
            players.forEach { this.players[it.uniqueId] = it }
        }
    }

    private var nametagSender: NametagSender? = null
    private var hasLibsDisguisesInstalled = false
    @Volatile private var acceptingWork = false

    private val trackedViewers = TrackedViewerRegistry<Player>()
    private val trackedEntities =
        ConcurrentHashMap<UUID, WeakReference<LivingEntity>>()
    private val pendingRefreshes = ConcurrentHashMap<UUID, PendingNametag>()
    private val pendingSince = ConcurrentHashMap<UUID, Long>()
    private val cooldownTasks = ConcurrentHashMap<ViewerKey, ScheduledTask>()
    private val targetedViewers = ConcurrentHashMap<UUID, UUID>()

    private val coalescedRefreshes = AtomicLong()
    private val processedRefreshes = AtomicLong()
    private val packetsSent = AtomicLong()
    private val packetWindowStartedAt = AtomicLong(System.currentTimeMillis())
    private val packetsInWindow = AtomicLong()
    @Volatile private var previousPacketsPerSecond = 0.0

    @Volatile var disableNametagJava = false
    @Volatile var disableNametagBedrock = false
    val nametagSenderHandler = NametagSenderHandler()

    fun load() {
        hasLibsDisguisesInstalled = ExternalCompatibilityManager.hasLibsDisguisesInstalled
        nametagSender = nametagSenderHandler.getCurrentUtil()
        onLoadOrReload()
    }

    fun onLoadOrReload() {
        disableNametagJava = LevelledMobs.instance.helperSettings.getBoolean(
            "disable-nametag-java", false
        )
        disableNametagBedrock = LevelledMobs.instance.helperSettings.getBoolean(
            "disable-nametag-bedrock", false
        )
    }

    val hasNametagSupport: Boolean
        get() = nametagSender != null

    fun start() {
        acceptingWork = true
    }

    fun stop() {
        acceptingWork = false
        pendingRefreshes.values.forEach(::complete)
        pendingRefreshes.clear()
        pendingSince.clear()
        cooldownTasks.values.forEach(ScheduledTask::cancel)
        cooldownTasks.clear()
        trackedViewers.clear()
        trackedEntities.clear()
        targetedViewers.clear()
    }

    /** Kept for compatibility with the removed polling queue watchdog. */
    fun taskChecker() = Unit

    fun getNumberQueued(): Int = pendingRefreshes.size

    fun getTrackedEntityCount(): Int = trackedViewers.entityCount()

    fun getCoalescedRefreshes(): Long = coalescedRefreshes.get()

    fun getProcessedRefreshes(): Long = processedRefreshes.get()

    fun getPacketsSent(): Long = packetsSent.get()

    fun getPacketsPerSecond(): Double {
        val now = System.currentTimeMillis()
        rollPacketWindow(now)
        val elapsed = (now - packetWindowStartedAt.get()).coerceAtLeast(1L)
        return if (packetsInWindow.get() == 0L) previousPacketsPerSecond
        else packetsInWindow.get() * 1000.0 / elapsed
    }

    fun getOldestPendingAgeMillis(): Long {
        val oldest = pendingSince.values.minOrNull() ?: return 0L
        return (System.currentTimeMillis() - oldest).coerceAtLeast(0L)
    }

    fun recordPacketSent() {
        packetsSent.incrementAndGet()
        packetsInWindow.incrementAndGet()
        rollPacketWindow(System.currentTimeMillis())
    }

    private fun rollPacketWindow(now: Long) {
        val startedAt = packetWindowStartedAt.get()
        val elapsed = now - startedAt
        if (elapsed < 1000L || !packetWindowStartedAt.compareAndSet(startedAt, now)) return

        previousPacketsPerSecond = packetsInWindow.getAndSet(0L) * 1000.0 / elapsed
    }

    fun track(entity: LivingEntity, player: Player) {
        trackedViewers.track(entity.uniqueId, player.uniqueId, player)
        trackedEntities[entity.uniqueId] = WeakReference(entity)
    }

    fun untrack(entity: LivingEntity, player: Player) {
        val entityId = entity.uniqueId
        if (trackedViewers.untrack(entityId, player.uniqueId)) {
            trackedEntities.remove(entityId)
            targetedViewers.remove(entityId)
        }
        cooldownTasks.remove(ViewerKey(entityId, player.uniqueId))?.cancel()
    }

    fun clearPlayer(player: Player) {
        val playerId = player.uniqueId
        trackedViewers.removeViewer(playerId).forEach { entityId ->
            trackedEntities.remove(entityId)
            targetedViewers.remove(entityId)
        }
        cooldownTasks.entries.removeIf { (key, task) ->
            if (key.playerId == playerId) {
                task.cancel()
                true
            } else {
                false
            }
        }
    }

    fun clearEntity(entity: LivingEntity) {
        clearEntity(entity.uniqueId)
    }

    private fun clearEntity(entityId: UUID) {
        trackedViewers.removeEntity(entityId)
        trackedEntities.remove(entityId)
        targetedViewers.remove(entityId)
        cooldownTasks.entries.removeIf { (key, task) ->
            if (key.entityId == entityId) {
                task.cancel()
                true
            } else {
                false
            }
        }
    }

    fun getTrackedPlayers(entityId: UUID): MutableList<Player> =
        trackedViewers.values(entityId).toMutableList()

    fun setTarget(entity: LivingEntity, player: Player?) {
        if (player == null)
            targetedViewers.remove(entity.uniqueId)
        else
            targetedViewers[entity.uniqueId] = player.uniqueId
    }

    fun refreshAllTrackedEntities() {
        trackedEntities.forEach { (entityId, weakEntity) ->
            val entity = weakEntity.get()
            if (entity == null) {
                trackedEntities.remove(entityId, weakEntity)
                trackedViewers.removeEntity(entityId)
                return@forEach
            }

            entity.scheduler.run(
                LevelledMobs.instance,
                Consumer {
                    if (!entity.isValid) {
                        clearEntity(entityId)
                        return@Consumer
                    }
                    val wrapper = LivingEntityWrapper.getInstance(entity)
                    LevelledMobs.instance.levelManager.updateNametag(wrapper)
                    wrapper.free()
                },
                null
            )
        }
    }

    fun addToQueue(item: QueueItem) {
        if (!acceptingWork || nametagSender == null) return
        if (!item.lmEntity.shouldShowLMNametag) return
        if (item.lmEntity.nametagVisibilityEnum.contains(NametagVisibilityEnum.DISABLED)) return

        val requestedPlayers = item.players?.distinctBy(Player::getUniqueId).orEmpty()
        if (requestedPlayers.isEmpty()) return

        item.lmEntity.inUseCount.incrementAndGet()
        var shouldSchedule = false
        pendingRefreshes.compute(item.entityId) { _, current ->
            if (current == null) {
                shouldSchedule = true
                pendingSince[item.entityId] = System.currentTimeMillis()
                PendingNametag(item, requestedPlayers)
            } else {
                requestedPlayers.forEach { current.players[it.uniqueId] = it }
                current.latestNametag.set(item.nametag!!)
                coalescedRefreshes.incrementAndGet()
                item.lmEntity.free()
                current
            }
        }

        if (!shouldSchedule) return
        val pending = pendingRefreshes[item.entityId] ?: return
        item.lmEntity.livingEntity.scheduler.runDelayed(
            LevelledMobs.instance,
            Consumer {
                if (pendingRefreshes.remove(item.entityId, pending)) {
                    try {
                        processItem(pending)
                        processedRefreshes.incrementAndGet()
                    } catch (ex: Exception) {
                        val entityName = runCatching { item.lmEntity.nameIfBaby }
                            .getOrDefault(LocalizedMessages.text("display.unknown-entity", colorize = false))
                        io.github.arcaneplugins.levelledmobs.util.Log.sevKey(
                            "console.queue.nametag-processing-error",
                            mapOf("entity" to entityName)
                        )
                        ex.printStackTrace()
                    } finally {
                        complete(pending)
                    }
                } else {
                    complete(pending)
                }
            },
            Runnable { complete(pending) },
            1L
        )
    }

    private fun complete(pending: PendingNametag) {
        if (!pending.completed.compareAndSet(false, true)) return
        pendingRefreshes.remove(pending.item.entityId, pending)
        pendingSince.remove(pending.item.entityId)
        pending.item.lmEntity.free()
    }

    private fun processItem(pending: PendingNametag) {
        val lmEntity = pending.item.lmEntity
        if (!lmEntity.isPopulated) return

        val main = LevelledMobs.instance
        if (main.helperSettings.getBoolean("assert-entity-validity-with-nametag-packets") &&
            !lmEntity.livingEntity.isValid
        ) return

        val nametag = pending.latestNametag.get()
        val forcedPlayers = lmEntity.playersNeedingNametagCooldownUpdate
            ?.associateBy(Player::getUniqueId)
            ?.toMutableMap()
            ?: mutableMapOf()
        lmEntity.playersNeedingNametagCooldownUpdate = null

        if (lmEntity.nametagVisibilityEnum.contains(NametagVisibilityEnum.TRACKING)) {
            val targetId = targetedViewers[lmEntity.livingEntity.uniqueId]
            if (targetId != null) {
                pending.players[targetId]?.let { forcedPlayers[targetId] = it }
                trackedViewers.get(lmEntity.livingEntity.uniqueId, targetId)
                    ?.let { forcedPlayers[targetId] = it }
            }
        }

        val baseAlwaysVisible =
            !nametag.isNullOrEmpty && lmEntity.livingEntity.isCustomNameVisible ||
                lmEntity.nametagVisibilityEnum.contains(NametagVisibilityEnum.ALWAYS_ON)
        val cooldownMillis = lmEntity.getNametagCooldownTime()
        val entityId = lmEntity.livingEntity.uniqueId

        pending.players.values.forEach { player ->
            if (!trackedViewers.contains(entityId, player.uniqueId)) return@forEach
            if (shouldSkipPlayer(player)) return@forEach
            val forceVisible = forcedPlayers.containsKey(player.uniqueId)
            nametagSender!!.sendNametag(
                lmEntity.livingEntity,
                nametag,
                player,
                baseAlwaysVisible || forceVisible
            )
            if (forceVisible && cooldownMillis > 0L && !nametag.isNullOrEmpty)
                scheduleCooldown(lmEntity.livingEntity, player, cooldownMillis)
        }

        forcedPlayers.values.forEach { player ->
            if (!trackedViewers.contains(entityId, player.uniqueId) ||
                pending.players.containsKey(player.uniqueId) || shouldSkipPlayer(player)
            )
                return@forEach
            nametagSender!!.sendNametag(lmEntity.livingEntity, nametag, player, true)
            if (cooldownMillis > 0L && !nametag.isNullOrEmpty)
                scheduleCooldown(lmEntity.livingEntity, player, cooldownMillis)
        }

        if (hasLibsDisguisesInstalled && LibsDisguisesUtils.isMobUsingLibsDisguises(lmEntity)) {
            val useNametag = nametag.nametag?.let {
                MessageUtils.colorizeAll(
                    nametag.nametagNonNull
                        .replace("{DisplayName}", Utils.capitalize(lmEntity.typeName.replace("_", " ")))
                        .replace("{CustomName}", lmEntity.livingEntity.customName ?: "")
                )
            }
            LibsDisguisesUtils.updateLibsDisguiseNametag(lmEntity, useNametag)
        }
    }

    private fun scheduleCooldown(entity: LivingEntity, player: Player, cooldownMillis: Long) {
        val key = ViewerKey(entity.uniqueId, player.uniqueId)
        val ticks = NametagTiming.cooldownTicks(cooldownMillis)
        val taskReference = AtomicReference<ScheduledTask?>()
        val task = entity.scheduler.runDelayed(
            LevelledMobs.instance,
            Consumer { scheduledTask ->
                if (cooldownTasks.remove(key, scheduledTask))
                    expireCooldown(entity, player, cooldownMillis)
            },
            Runnable {
                taskReference.get()?.let { cooldownTasks.remove(key, it) }
            },
            ticks
        ) ?: return
        taskReference.set(task)
        cooldownTasks.put(key, task)?.cancel()
    }

    private fun expireCooldown(entity: LivingEntity, player: Player, cooldownMillis: Long) {
        if (!trackedViewers.contains(entity.uniqueId, player.uniqueId) || !entity.isValid) return

        if (targetedViewers[entity.uniqueId] == player.uniqueId) {
            scheduleCooldown(entity, player, cooldownMillis)
            return
        }

        val wrapper = LivingEntityWrapper.getInstance(entity)
        if (!wrapper.isLevelled) {
            wrapper.free()
            return
        }
        val nametag = LevelledMobs.instance.levelManager.getNametag(
            wrapper,
            isDeathNametag = false,
            preserveMobName = true
        )
        val alwaysVisible =
            !nametag.isNullOrEmpty && entity.isCustomNameVisible ||
                wrapper.nametagVisibilityEnum.contains(NametagVisibilityEnum.ALWAYS_ON)
        nametagSender?.sendNametag(entity, nametag, player, alwaysVisible)
        wrapper.free()
    }

    private fun shouldSkipPlayer(player: Player): Boolean {
        val isBedrock = player.uniqueId.mostSignificantBits == 0L
        return disableNametagBedrock && isBedrock || disableNametagJava && !isBedrock
    }
}
