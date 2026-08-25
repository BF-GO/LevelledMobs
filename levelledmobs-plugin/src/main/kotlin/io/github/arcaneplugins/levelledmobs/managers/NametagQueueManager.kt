package io.github.arcaneplugins.levelledmobs.managers

import java.time.Instant
import java.util.WeakHashMap
import java.util.concurrent.LinkedBlockingQueue
import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.misc.NametagTimerChecker
import io.github.arcaneplugins.levelledmobs.misc.QueueItem
import io.github.arcaneplugins.levelledmobs.nametag.NametagSender
import io.github.arcaneplugins.levelledmobs.nametag.NametagSenderHandler
import io.github.arcaneplugins.levelledmobs.result.NametagResult
import io.github.arcaneplugins.levelledmobs.enums.NametagVisibilityEnum
import io.github.arcaneplugins.levelledmobs.util.LibsDisguisesUtils
import io.github.arcaneplugins.levelledmobs.util.Log
import io.github.arcaneplugins.levelledmobs.util.LocalizedMessages
import io.github.arcaneplugins.levelledmobs.util.MessageUtils
import io.github.arcaneplugins.levelledmobs.util.Utils
import io.github.arcaneplugins.levelledmobs.wrappers.LivingEntityWrapper
import io.github.arcaneplugins.levelledmobs.wrappers.SchedulerWrapper
import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask

/**
 * Ставит обновления тегов мобов в очередь, чтобы их можно было применить в фоновом потоке.
 *
 * @author stumper66
 * @since 3.0.0
 */
class NametagQueueManager {
    private var isRunning = false
    private var doThread = false
    private var nametagSender: NametagSender? = null
    private var hasLibsDisguisesInstalled = false
    var disableNametagJava = false
    var disableNametagBedrock = false
    var queueTask: BukkitTask? = null
    private val queue = LinkedBlockingQueue<QueueItem>()
    val nametagSenderHandler = NametagSenderHandler()
    private val queueLock = Any()

    fun load(){
        hasLibsDisguisesInstalled = ExternalCompatibilityManager.hasLibsDisguisesInstalled
        this.nametagSender = nametagSenderHandler.getCurrentUtil()
        onLoadOrReload()
    }

    fun onLoadOrReload(){
        this.disableNametagJava = LevelledMobs.instance.helperSettings.getBoolean(
            "disable-nametag-java", false
        )
        this.disableNametagBedrock = LevelledMobs.instance.helperSettings.getBoolean(
            "disable-nametag-bedrock", false
        )
    }

    val hasNametagSupport: Boolean
        get() = this.nametagSender != null

    fun start() {
        // фолия будет работать напрямую
        doThread = true
        if (LevelledMobs.instance.ver.isRunningFolia) return

        if (isRunning) return

        isRunning = true

        val scheduler = SchedulerWrapper {
            var hadError = false
            try {
                mainThread()
            } catch (e: Exception) {
                if (e !is InterruptedException){
                    hadError = true
                    e.printStackTrace()
                }
            }
            if (hadError)
                Log.sevKey("console.queue.nametag-stopped-error")
            else
                Log.infKey("console.queue.nametag-stopped")

            isRunning = false
        }
        scheduler.run()
        this.queueTask = scheduler.bukkitTask
    }

    fun stop() {
        doThread = false
    }

    fun taskChecker(){
        val qt = queueTask ?: return

        val queueSize = getNumberQueued()

        if (queueSize < 1000 && !qt.isCancelled || Bukkit.getScheduler().isCurrentlyRunning(qt.taskId)) return
        val status = if (qt.isCancelled) LocalizedMessages.text("display.task.cancelled", colorize = false)
        else if (queueSize < 1000) LocalizedMessages.text("display.task.not-running", colorize = false)
        else LocalizedMessages.text("display.task.queue-size", mapOf("size" to queueSize.toString()), false)

        Log.warKey("console.queue.nametag-restarting", mapOf("status" to status))
        qt.cancel()
        isRunning = false
        start()
    }

    fun addToQueue(item: QueueItem) {
        if (!doThread) return
        if (!item.lmEntity.shouldShowLMNametag) return
        if (Bukkit.getOnlinePlayers().isEmpty()) return
        if (item.lmEntity.nametagVisibilityEnum.contains(NametagVisibilityEnum.DISABLED))
            return

        item.lmEntity.inUseCount.getAndIncrement()

        if (LevelledMobs.instance.ver.isRunningFolia){
            // фолия бежит напрямую
            preProcessItem(item)
        }
        else{
            synchronized(queueLock){
                queue.offer(item)
            }
        }
    }

    fun getNumberQueued(): Int{
        val size: Int
        synchronized(queueLock){
            size = queue.size
        }

        return size
    }

    private fun mainThread() {
        while (doThread) {
            val item: QueueItem?

            synchronized(queueLock){
                item = queue.poll()
            }
            if (item == null) {
                Thread.sleep(2L)
                continue
            }

            val scheduler = SchedulerWrapper(
                item.lmEntity.livingEntity
            ) {
                preProcessItem(item)
                item.lmEntity.free()
            }

            scheduler.runDirectlyInBukkit = true
            scheduler.entity = item.lmEntity.livingEntity
            item.lmEntity.inUseCount.getAndIncrement()
            scheduler.run()
        }

        isRunning = false
    }

    private fun preProcessItem(item: QueueItem) {
        if (!item.lmEntity.isPopulated) {
            item.lmEntity.free()
            return
        }

        var lastEntityType: String? = null
        try {
            lastEntityType = item.lmEntity.nameIfBaby
            processItem(item)
        } catch (ex: Exception) {
            val entityName = lastEntityType ?: LocalizedMessages.text("display.unknown-entity", colorize = false)

            Log.sevKey("console.queue.nametag-processing-error", mapOf("entity" to entityName))
            ex.printStackTrace()
        } finally {
            item.lmEntity.free()
        }
    }

    private fun processItem(item: QueueItem) {
        if (this.nametagSender == null) {
            // это произойдет, если версия Minecraft не поддерживается напрямую NMS.
            return
        }

        val nametagTimerResetTime = item.lmEntity.getNametagCooldownTime()

        if (nametagTimerResetTime > 0L && !item.nametag!!.isNullOrEmpty) {
            synchronized(NametagTimerChecker.nametagTimer_Lock) {
                val nametagCooldownQueue: Map<Player, WeakHashMap<LivingEntity, Instant>> =
                    LevelledMobs.instance.nametagTimerChecker.nametagCooldownQueue
                if (item.lmEntity.playersNeedingNametagCooldownUpdate != null) {
                    // запишите, какие игроки должны получить время восстановления для этого моба
                    // public Map<Player, WeakHashMap<LivingEntity, Instant>> nametagCooldownQueue;
                    for (player in item.lmEntity.playersNeedingNametagCooldownUpdate!!) {
                        if (!nametagCooldownQueue.containsKey(player))
                            continue

                        nametagCooldownQueue[player]?.set(item.lmEntity.livingEntity, Instant.now())
                        LevelledMobs.instance.nametagTimerChecker.cooldownTimes[item.lmEntity.livingEntity] =
                            item.lmEntity.getNametagCooldownTime()
                    }

                    // если у кого-либо из игроков уже есть кулдаун этого моба, не удаляйте кулдаун
                    for ((player, value) in nametagCooldownQueue) {
                        if (item.lmEntity.playersNeedingNametagCooldownUpdate!!.contains(player))
                            continue

                        if (value.containsKey(item.lmEntity.livingEntity))
                            item.lmEntity.playersNeedingNametagCooldownUpdate!!.add(player)
                    }
                } else {
                    // если есть какие-либо кулдауны, мы будем их использовать
                    for ((key, value) in nametagCooldownQueue) {
                        if (value.containsKey(item.lmEntity.livingEntity)) {
                            if (item.lmEntity.playersNeedingNametagCooldownUpdate == null)
                                item.lmEntity.playersNeedingNametagCooldownUpdate = HashSet()

                            item.lmEntity.playersNeedingNametagCooldownUpdate!!.add(key)
                        }
                    }
                }
            }
        }
        else if (item.lmEntity.playersNeedingNametagCooldownUpdate != null)
            item.lmEntity.playersNeedingNametagCooldownUpdate = null

        val main = LevelledMobs.instance
        synchronized(NametagTimerChecker.entityTarget_Lock) {
            if (main.nametagTimerChecker.entityTargetMap.containsKey(
                    item.lmEntity.livingEntity
                )
            ) {
                if (item.lmEntity.playersNeedingNametagCooldownUpdate == null)
                    item.lmEntity.playersNeedingNametagCooldownUpdate = mutableSetOf()

                item.lmEntity.playersNeedingNametagCooldownUpdate!!.add(
                    main.nametagTimerChecker.entityTargetMap[item.lmEntity.livingEntity]!!
                )
            }
        }

        if (!item.lmEntity.isPopulated)
            return

        if (main.helperSettings.getBoolean(
                "assert-entity-validity-with-nametag-packets"
            ) && !item.lmEntity.livingEntity.isValid
        ) {
            return
        }

        updateNametag(item.lmEntity, item.nametag!!, item.players!!)
    }

    @Suppress("DEPRECATION")
    private fun updateNametag(
        lmEntity: LivingEntityWrapper,
        nametag: NametagResult,
        players: MutableList<Player>
    ) {
        val loopCount = if (lmEntity.playersNeedingNametagCooldownUpdate == null) 1 else 2

        for (i in 0 until loopCount) {
            // снова зациклится для обновления с перезарядкой именного тега только для указанных игроков

            val nametagVisibilityEnum = lmEntity.nametagVisibilityEnum
            val doAlwaysVisible = i == 1 || !nametag.isNullOrEmpty && lmEntity.livingEntity.isCustomNameVisible ||
                    nametagVisibilityEnum.contains(NametagVisibilityEnum.ALWAYS_ON)

            if (i == 0) {
                // эти игроки не всегда получают метки с именами, если для моба не настроено всегда включение
                for (player in players) {
                    if (lmEntity.playersNeedingNametagCooldownUpdate != null
                        && lmEntity.playersNeedingNametagCooldownUpdate!!.contains(player)
                    ) {
                        continue
                    }
                    
                    /** Отключить, если Java или Bedrock */
                    if (disableNametagBedrock && isBedrock(player)) return
                    if (disableNametagJava && !isBedrock(player)) return

                    nametagSender!!.sendNametag(
                        lmEntity.livingEntity, nametag, player,
                        doAlwaysVisible
                    )
                }
            } else {
                // эти игроки всегда получают бейджики с именами
                for (player in lmEntity.playersNeedingNametagCooldownUpdate!!) {

                    /** Отключить, если Java или Bedrock */
                    if (disableNametagBedrock && isBedrock(player)) return
                    if (disableNametagJava && !isBedrock(player)) return


                    nametagSender!!.sendNametag(lmEntity.livingEntity, nametag, player, true)
                }
            }

            if (hasLibsDisguisesInstalled && LibsDisguisesUtils.isMobUsingLibsDisguises(lmEntity)) {
                var useNametag: String? = null
                if (nametag.nametag != null){
                    useNametag = MessageUtils.colorizeAll(nametag.nametagNonNull
                        .replace("{DisplayName}", Utils.capitalize(lmEntity.typeName.replace("_", " ")))
                        .replace("{CustomName}", lmEntity.livingEntity.customName ?: ""))
                }

                LibsDisguisesUtils.updateLibsDisguiseNametag(lmEntity, useNametag)
            }
        }
    }

    private fun isBedrock(player: Player) : Boolean {
        return player.uniqueId.mostSignificantBits == 0L
    }
}
