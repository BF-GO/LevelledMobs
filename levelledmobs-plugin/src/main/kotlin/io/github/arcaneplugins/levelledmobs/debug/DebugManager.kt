package io.github.arcaneplugins.levelledmobs.debug

import io.github.arcaneplugins.levelledmobs.LevelledMobs
import java.time.Duration
import java.time.Instant
import java.util.function.Supplier
import io.github.arcaneplugins.levelledmobs.LivingEntityInterface
import io.github.arcaneplugins.levelledmobs.rules.RuleInfo
import io.github.arcaneplugins.levelledmobs.util.Log
import io.github.arcaneplugins.levelledmobs.util.LocalizedMessages
import io.github.arcaneplugins.levelledmobs.util.MessageUtils.colorizeAll
import io.github.arcaneplugins.levelledmobs.wrappers.LivingEntityWrapper
import io.github.arcaneplugins.levelledmobs.wrappers.SchedulerResult
import io.github.arcaneplugins.levelledmobs.wrappers.SchedulerWrapper
import java.util.UUID
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import kotlin.math.floor

/**
 * Предоставляет логику для системы отладки.
 *
 * @author stumper66
 * @since 3.14.0
 */
class DebugManager {
    private val defaultPlayerDistance = 16
    var isEnabled = false
        private set
    var isTimerEnabled = false
        private set
    var bypassAllFilters = false
        private set
    private var timerEndTime: Instant? = null
    private var timerTask: SchedulerResult? = null
    val filterDebugTypes = mutableSetOf<DebugType>()
    val filterEntityTypes = mutableSetOf<EntityType>()
    val filterRuleNames = mutableSetOf<String>()
    val filterPlayerNames = mutableSetOf<String>()
    var playerThatEnabledDebug: Player? = null
    var listenFor = ListenFor.BOTH
    var outputType = OutputTypes.TO_CONSOLE
    var maxPlayerDistance: Int? = null
    var minYLevel: Int? = null
    var maxYLevel: Int? = null
    var disableAfter: Long? = null
    var disableAfterStr: String? = null
    var damageDebugOutputIsEnabled = false
        private set

    init {
        instance = this
        maxPlayerDistance = defaultPlayerDistance
    }

    fun enableDebug(
        sender: CommandSender,
        usetimer: Boolean,
        bypassFilters: Boolean
    ) {
        if (sender is Player) this.playerThatEnabledDebug = sender
        this.bypassAllFilters = bypassFilters
        this.isEnabled = true
        checkTimerSettings(usetimer)
    }

    fun disableDebug() {
        this.isEnabled = false
        this.isTimerEnabled = false
        toggleDamageDebugOutput(false)
        disableTimer()
    }

    private fun disableTimer() {
        isTimerEnabled = false

        if (this.timerTask == null) return

        timerTask!!.cancelTask()
        this.timerTask = null
    }

    private fun checkTimerSettings(useTimer: Boolean) {
        if (!isEnabled) return

        val canUseTimer = this.disableAfter != null && disableAfter!! > 0L
        if (!useTimer || !canUseTimer) {
            disableTimer()
            return
        }

        this.timerEndTime = Instant.now().plusMillis(disableAfter!!)

        if (!this.isTimerEnabled) {
            this.isTimerEnabled = true
            val wrapper = SchedulerWrapper { this.timerLoop() }
            this.timerTask = wrapper.runTaskTimerGlobal(20L, 20L)
        }
    }

    companion object{
        private lateinit var instance: DebugManager
        private val longMessagesMap = mutableMapOf<UUID, MutableList<Supplier<String>>>()
        private val lock = Any()

        fun log(
            debugType: DebugType,
            ruleInfo: RuleInfo,
            lmEntity: LivingEntityWrapper?,
            msg: Supplier<String?>
        ) {
            instance.logInstance(debugType, ruleInfo, lmEntity, null, null, msg.get()!!)
        }

        fun log(
            debugType: DebugType,
            ruleInfo: RuleInfo,
            lmInterface: LivingEntityInterface?,
            ruleResult: Boolean,
            msg: Supplier<String?>
        ) {
            instance.logInstance(debugType, ruleInfo, lmInterface, null, ruleResult, msg.get()!!)
        }

        fun log(
            debugType: DebugType,
            lmEntity: LivingEntityWrapper?,
            msg: Supplier<String?>
        ) {
            instance.logInstance(debugType, null, lmEntity, null, null, msg.get()!!)
        }

        fun logNoComma(
            debugType: DebugType,
            lmEntity: LivingEntityWrapper?,
            msg: Supplier<String?>
        ) {
            instance.logInstance(debugType, null, lmEntity, null, null, msg.get()!!, false)
        }

        fun log(
            debugType: DebugType,
            lmEntity: LivingEntityWrapper?,
            result: Boolean,
            msg: Supplier<String?>
        ) {
            instance.logInstance(debugType, null, lmEntity, null, result, msg.get()!!)
        }

        fun log(
            debugType: DebugType,
            entity: Entity?,
            result: Boolean,
            msg: Supplier<String?>
        ) {
            instance.logInstance(debugType, null, null, entity, result, msg.get()!!)
        }

        fun log(
            debugType: DebugType,
            entity: Entity?,
            msg: Supplier<String?>
        ) {
            instance.logInstance(debugType, null, null, entity, null, msg.get()!!)
        }

        /**
         * Отправляет отладочное сообщение на консоль, если это включено в настройках.
         *
         * @param debugType Ссылка на местонахождение журнала отладки, чтобы его можно было отследить.
         * назад легко
         * @param msg       Сообщение для отладки
         */
        fun log(debugType: DebugType, msg: Supplier<String?>) {
            val message = msg.get() ?: return
            instance.logInstance(debugType, null, null, null, null, message)
        }

        fun startLongDebugMessage(): UUID{
            val id = UUID.randomUUID()
            synchronized (lock){
                longMessagesMap[id] = mutableListOf()
            }

            return id
        }

        fun logLongMessage(id: UUID, message: Supplier<String>){
            if (!instance.isEnabled) return

            synchronized(lock) {
                longMessagesMap[id]?.add(message)
            }
        }

        fun endLongMessage(
            id: UUID,
            debugType: DebugType,
            lmEntity: LivingEntityWrapper?
        ){
            val messages: MutableList<Supplier<String>>?

            synchronized(lock){
                messages = longMessagesMap.remove(id)
            }

            if (messages == null) return
            val sb = StringBuilder()
            for (message in messages)
                sb.append(message.get())

            log(debugType, lmEntity){ sb.toString() }
        }
    }

    private fun logInstance(
        debugType: DebugType,
        ruleInfo: RuleInfo?,
        lmInterface: LivingEntityInterface?,
        entity: Entity?,
        ruleResult: Boolean?,
        origMsg: String,
        useComma: Boolean = true
    ) {
        if (!isEnabled) return
        var msg = origMsg

        // теперь вам нужно пройти все фильтры, если они настроены
        if (!bypassAllFilters) {
            if (filterDebugTypes.isNotEmpty() && !filterDebugTypes.contains(debugType)) return

            if (ruleInfo != null && filterRuleNames.isNotEmpty() &&
                !filterRuleNames.contains(ruleInfo.ruleName.replace(" ", "_")) ||
                ruleInfo == null && filterRuleNames.isNotEmpty()
            ) {
                return
            }

            if (filterEntityTypes.isNotEmpty()) {
                var et: EntityType? = null
                if (entity != null) et = entity.type
                else if (lmInterface != null) et = lmInterface.entityType
                if (!filterEntityTypes.contains(et)) return
            }

            var useEntity = entity
            if (lmInterface is LivingEntityWrapper) useEntity = lmInterface.livingEntity

            if (maxPlayerDistance != null && maxPlayerDistance!! > 0 && useEntity != null) {
                val players = getPlayers()
                var foundMatch = false
                if (players != null) {
                    for (player in players) {
                        if (player.world != useEntity.world) continue
                        val dist = player.location.distance(useEntity.location)
                        if (dist <= maxPlayerDistance!!) {
                            foundMatch = true
                            break
                        }
                    }
                }

                if (!foundMatch) return
            }

            if (ruleResult != null && listenFor != ListenFor.BOTH) {
                if (ruleResult && listenFor == ListenFor.FAILURE) return
                if (!ruleResult && listenFor == ListenFor.SUCCESS) return
            }

            if (useEntity != null) {
                if (minYLevel != null && useEntity.location.blockY < minYLevel!!) return
                if (maxYLevel != null && useEntity.location.blockY > maxYLevel!!) return
            }
        } // конец, обойти все

        if (ruleInfo != null){
            msg = if (origMsg.isEmpty())
                "(${ruleInfo.ruleName})"
            else
                "(${ruleInfo.ruleName}) $msg"
        }

        if (lmInterface != null){
            val lmEntity = lmInterface as? LivingEntityWrapper
            val useName = lmEntity?.nameIfBaby ?: lmInterface.typeName
            var lvl = lmEntity?.mobLevel
            if (lmInterface.summonedLevel != null) lvl = lmInterface.summonedLevel
            val lvlInfo = LocalizedMessages.text(
                if (lvl != null) "command.levelledmobs.debug.entity-level"
                else "command.levelledmobs.debug.entity-no-level",
                mapOf("level" to (lvl?.toString() ?: "-")),
                false
            )
            val addedComma = if (useComma) ", " else ""

            msg = if (msg.isEmpty())
                LocalizedMessages.text("command.levelledmobs.debug.entity-prefix", mapOf(
                    "entity" to useName,
                    "level" to lvlInfo
                ), false)
            else
                LocalizedMessages.text("command.levelledmobs.debug.entity-prefix", mapOf(
                    "entity" to useName,
                    "level" to lvlInfo
                ), false) + addedComma + msg
        }
        else if (entity != null){
            val addedComma = if (useComma) ", " else ""

            msg = if (msg.isEmpty())
                LocalizedMessages.text("command.levelledmobs.debug.entity-prefix", mapOf(
                    "entity" to entity.type.toString(),
                    "level" to ""
                ), false)
            else
                LocalizedMessages.text("command.levelledmobs.debug.entity-prefix", mapOf(
                    "entity" to entity.type.toString(),
                    "level" to ""
                ), false) + addedComma + msg
        }

        if (ruleResult != null) {
            if (msg.isEmpty())
                msg = LocalizedMessages.text("command.levelledmobs.debug.result", mapOf(
                    "result" to ruleResult.toString()
                ), false)
            else
                msg += LocalizedMessages.text("command.levelledmobs.debug.result-inline", mapOf(
                    "result" to ruleResult.toString()
                ), false)
        }

        val outputMessage = LocalizedMessages.text("command.levelledmobs.debug.output-line", mapOf(
            "type" to debugType.toString(),
            "message" to msg
        ))
        if (outputType == OutputTypes.TO_BOTH || outputType == OutputTypes.TO_CONSOLE)
            Log.inf(outputMessage)

        if (outputType == OutputTypes.TO_BOTH || outputType == OutputTypes.TO_CHAT) {
            if (playerThatEnabledDebug == null)
                Log.infKey("console.debug.no-chat-recipient")
            else {
                playerThatEnabledDebug!!.sendMessage(outputMessage)
            }
        }
    }

    private fun getPlayers(): MutableList<Player>? {
        if (filterPlayerNames.isEmpty())
            return Bukkit.getOnlinePlayers().toMutableList()

        val players = mutableListOf<Player>()
        for (playerName in filterPlayerNames) {
            val player = Bukkit.getPlayer(playerName)
            if (player != null) players.add(player)
        }

        return if (players.isEmpty()) null else players
    }

    fun getDebugStatus(): String {
        val status = LocalizedMessages.text(
            if (isEnabled) "display.enabled" else "display.disabled", colorize = false
        )
        val sb = StringBuilder(
            LocalizedMessages.text(
                "command.levelledmobs.debug.status-heading",
                mapOf("status" to status), colorize = false
            )
        )
        if (isEnabled) {
            if (isTimerEnabled) {
                sb.append(LocalizedMessages.text(
                    "command.levelledmobs.debug.status-time-left",
                    mapOf("time" to getTimeRemaining()), colorize = false
                ))
            }
        }

        if (!bypassAllFilters && !hasFiltering()) return sb.toString()
        sb.append(LocalizedMessages.text(
            "command.levelledmobs.debug.status-filters-heading", colorize = false
        ))

        if (bypassAllFilters) {
            sb.append(LocalizedMessages.text(
                "command.levelledmobs.debug.status-filters-bypassed", colorize = false
            ))
            return sb.toString()
        }

        if (filterDebugTypes.isNotEmpty()) {
            sb.append(LocalizedMessages.text(
                "command.levelledmobs.debug.status-debug-types",
                mapOf("value" to filterDebugTypes), colorize = false
            ))
        }

        if (filterEntityTypes.isNotEmpty()) {
            sb.append(LocalizedMessages.text(
                "command.levelledmobs.debug.status-entity-types",
                mapOf("value" to filterEntityTypes), colorize = false
            ))
        }

        if (filterRuleNames.isNotEmpty()) {
            sb.append(LocalizedMessages.text(
                "command.levelledmobs.debug.status-rule-names",
                mapOf("value" to filterRuleNames), colorize = false
            ))
        }

        if (filterPlayerNames.isNotEmpty()) {
            sb.append(LocalizedMessages.text(
                "command.levelledmobs.debug.status-player-names",
                mapOf("value" to filterPlayerNames), colorize = false
            ))
        }

        if (listenFor != ListenFor.BOTH) {
            sb.append(LocalizedMessages.text(
                "command.levelledmobs.debug.status-listen-for",
                mapOf("value" to listenFor.name.lowercase()), colorize = false
            ))
        }

        if (maxPlayerDistance != null) {
            sb.append(LocalizedMessages.text(
                "command.levelledmobs.debug.status-max-distance",
                mapOf("value" to maxPlayerDistance), colorize = false
            ))
        }

        if (minYLevel != null) {
            sb.append(LocalizedMessages.text(
                "command.levelledmobs.debug.status-min-y",
                mapOf("value" to minYLevel), colorize = false
            ))
        }

        if (maxYLevel != null) {
            sb.append(LocalizedMessages.text(
                if (minYLevel != null) "command.levelledmobs.debug.status-max-y-inline"
                else "command.levelledmobs.debug.status-max-y",
                mapOf("value" to maxYLevel), colorize = false
            ))
        }

        if (outputType != OutputTypes.TO_CONSOLE) {
            sb.append(LocalizedMessages.text(
                "command.levelledmobs.debug.status-output",
                mapOf("value" to outputType.name.lowercase()), colorize = false
            ))
        }

        return sb.toString()
    }

    private fun hasFiltering(): Boolean {
        return (filterDebugTypes.isNotEmpty() ||
                filterEntityTypes.isNotEmpty() ||
                filterRuleNames.isNotEmpty() ||
                filterPlayerNames.isNotEmpty() || listenFor != ListenFor.BOTH || outputType != OutputTypes.TO_CONSOLE ||
                    maxPlayerDistance == null || maxPlayerDistance != 0 || minYLevel != null || maxYLevel != null
                )
    }

    fun resetFilters() {
        filterDebugTypes.clear()
        filterEntityTypes.clear()
        filterRuleNames.clear()
        filterPlayerNames.clear()
        listenFor = ListenFor.BOTH
        outputType = OutputTypes.TO_CONSOLE
        maxPlayerDistance = defaultPlayerDistance
        minYLevel = null
        maxYLevel = null
        disableAfter = null
        disableAfterStr = null
    }

    enum class ListenFor {
        FAILURE, SUCCESS, BOTH
    }

    enum class OutputTypes {
        TO_CONSOLE, TO_CHAT, TO_BOTH
    }

    fun isDebugTypeEnabled(debugType: DebugType): Boolean {
        if (!this.isEnabled) return false

        return filterDebugTypes.isEmpty() || filterDebugTypes.contains(debugType)
    }

    private fun timerLoop() {
        if (Instant.now().isAfter(this.timerEndTime)) {
            disableDebug()

            val msg = LocalizedMessages.text("command.levelledmobs.debug.timer-elapsed")
            if (outputType == OutputTypes.TO_CONSOLE || outputType == OutputTypes.TO_BOTH)
                Log.inf(msg)

            if ((outputType == OutputTypes.TO_CHAT || outputType == OutputTypes.TO_BOTH)
                && playerThatEnabledDebug != null
            ) {
                val player = playerThatEnabledDebug!!
                player.scheduler.run(
                    LevelledMobs.instance,
                    { player.sendMessage(msg) },
                    null
                )
            }
        }
    }

    fun timerWasChanged(
        useTimer: Boolean
    ) {
        checkTimerSettings(isTimerEnabled || useTimer)
    }

    private fun getTimeRemaining(): String? {
        if (!isEnabled || disableAfter == null || disableAfter!! <= 0 || timerEndTime == null) return null

        val duration = Duration.between(Instant.now(), timerEndTime)
        val secondsLeft = duration.seconds.toInt()
        if (secondsLeft < 60)
            return LocalizedMessages.text("display.duration.seconds", mapOf(
                "count" to secondsLeft.toString()
            ), false)
        else if (secondsLeft < 3600) {
            val minutes = floor(secondsLeft.toDouble() / 60.0).toInt()
            val newSeconds = secondsLeft % 60
            return LocalizedMessages.text("display.duration.minutes-seconds", mapOf(
                "minutes" to minutes.toString(),
                "seconds" to newSeconds.toString()
            ), false)
        }

        return secondsLeft.toString()
    }

    fun toggleDamageDebugOutput(doEnable: Boolean){
        if (doEnable) {
            if (damageDebugOutputIsEnabled) return
            // мы загрузим и выгрузим этот прослушиватель на основе вышеуказанных настроек при перезагрузке
            damageDebugOutputIsEnabled = true
            Bukkit.getPluginManager().registerEvents(LevelledMobs.instance.entityDamageDebugListener, LevelledMobs.instance)
        }
        else{
            if (!damageDebugOutputIsEnabled) return
            damageDebugOutputIsEnabled = false
            HandlerList.unregisterAll(LevelledMobs.instance.entityDamageDebugListener)
        }
    }
}
