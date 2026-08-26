package io.github.arcaneplugins.levelledmobs

import io.github.arcaneplugins.levelledmobs.commands.CommandHandler
import io.github.arcaneplugins.levelledmobs.customdrops.CustomDropsHandler
import io.github.arcaneplugins.levelledmobs.debug.DebugManager
import io.github.arcaneplugins.levelledmobs.listeners.BlockPlaceListener
import io.github.arcaneplugins.levelledmobs.listeners.ChunkLoadListener
import io.github.arcaneplugins.levelledmobs.listeners.EntityDamageDebugListener
import io.github.arcaneplugins.levelledmobs.listeners.EntityDamageListener
import io.github.arcaneplugins.levelledmobs.listeners.EntityDeathListener
import io.github.arcaneplugins.levelledmobs.listeners.EntityTransformListener
import io.github.arcaneplugins.levelledmobs.listeners.PlayerDeathListener
import io.github.arcaneplugins.levelledmobs.listeners.PlayerInteractEventListener
import io.github.arcaneplugins.levelledmobs.managers.EssentialsIntegration
import io.github.arcaneplugins.levelledmobs.managers.LevelManager
import io.github.arcaneplugins.levelledmobs.managers.MobDataManager
import io.github.arcaneplugins.levelledmobs.managers.MobsQueueManager
import io.github.arcaneplugins.levelledmobs.managers.NametagQueueManager
import io.github.arcaneplugins.levelledmobs.managers.NotifyManager
import io.github.arcaneplugins.levelledmobs.managers.PlaceholderApiIntegration
import io.github.arcaneplugins.levelledmobs.misc.FileLoader
import io.github.arcaneplugins.levelledmobs.misc.YmlParsingHelper
import io.github.arcaneplugins.levelledmobs.nametag.Definitions
import io.github.arcaneplugins.levelledmobs.nametag.NmsMappings
import io.github.arcaneplugins.levelledmobs.nametag.ServerVersionInfo
import io.github.arcaneplugins.levelledmobs.rules.RulesManager
import io.github.arcaneplugins.levelledmobs.rules.RulesParser
import io.github.arcaneplugins.levelledmobs.rules.strategies.RandomLevellingStrategy
import io.github.arcaneplugins.levelledmobs.util.ConfigUtils
import io.github.arcaneplugins.levelledmobs.util.Log
import io.github.arcaneplugins.levelledmobs.util.LocalizedMessages
import io.github.arcaneplugins.levelledmobs.util.MessageUtils
import io.github.arcaneplugins.levelledmobs.util.QuickTimer
import io.github.arcaneplugins.levelledmobs.util.Utils
import io.github.arcaneplugins.levelledmobs.wrappers.LivingEntityWrapper
import java.time.Instant
import java.util.Random
import java.util.Stack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import org.bukkit.plugin.java.JavaPlugin


/**
 * Это основной класс плагина. Bukkit при запуске вызовет onLoad и onEnable, и
 * onDisable при выключении.
 *
 * @author lokka30, stumper66
 * @since 1.0
 */
class LevelledMobs : JavaPlugin() {
    val levelInterface: LevelInterface2 = LevelManager()
    val levelManager = LevelManager()
    val mobDataManager = MobDataManager()
    val customDropsHandler = CustomDropsHandler()
    val chunkLoadListener = ChunkLoadListener()
    val blockPlaceListener = BlockPlaceListener()
    val playerInteractEventListener = PlayerInteractEventListener()
    val playerDeathListener = PlayerDeathListener()
    val entityDamageListener = EntityDamageListener()
    val entityTransformListener = EntityTransformListener()
    val entityDeathListener = EntityDeathListener()
    val mainCompanion = MainCompanion()
    val rulesParsingManager = RulesParser()
    val rulesManager = RulesManager()
    val mobsQueueManager = MobsQueueManager()
    val nametagQueueManager = NametagQueueManager()
    val attributeSyncObject = Any()
    val random = Random()
    var placeholderApiIntegration: PlaceholderApiIntegration? = null
        internal set
    val helperSettings = YmlParsingHelper(YamlConfiguration())
    var playerLevellingMinRelevelTime = 0L
        internal set
    var maxPlayersRecorded = 0
    val debugManager = DebugManager()
    val definitions = Definitions()
    val ver = ServerVersionInfo()

    // Конфигурация
    @Volatile
    var messagesCfg = YamlConfiguration()
        internal set
    val configUtils = ConfigUtils()

    // Разное
    val customMobGroups: Map<String, Set<String>>
        get() = rulesManager.customMobGroups
    var entityDamageDebugListener = EntityDamageDebugListener()
    private var loadTime = 0L
    val playerLevellingEntities = ConcurrentHashMap<UUID, Instant>()
    var cacheCheck: Stack<LivingEntityWrapper>? = null

    companion object {
        @JvmStatic
        lateinit var instance: LevelledMobs
            private set
    }

    override fun onLoad() {
        instance = this
        LocalizedMessages.initialize(this)
    }

    override fun onEnable() {
        val timer = QuickTimer()

        this.ver.load()
        if (ver.minecraftVersion.isLessThan("1.21.1"))
            Log.sevKey("console.lifecycle.unsupported-minecraft")

        NmsMappings.load()
        this.definitions.load()
        CommandHandler.load()
        EssentialsIntegration.load()
        this.nametagQueueManager.load()
        this.mainCompanion.load()
        (this.levelInterface as LevelManager).load()
        if (!mainCompanion.loadFiles()) {
            // произошла фатальная ошибка при чтении необходимых файлов
            Bukkit.getPluginManager().disablePlugin(this)
            return
        }
        definitions.useTranslationComponents = helperSettings.getBoolean(
            "use-translation-components", true
        )
        definitions.setUseLegacySerializer(
            helperSettings.getBoolean("use-legacy-serializer", true)
        )
        nametagQueueManager.nametagSenderHandler.refresh()
        mainCompanion.registerListeners()

        Log.infKey("console.lifecycle.misc-procedures")
        if (nametagQueueManager.hasNametagSupport) {
            levelManager.startEventDrivenNametagUpdates()
        }

        prepareToLoadCustomDrops()
        mainCompanion.checkSettingsWithMaxPlayerOptions()
        mainCompanion.startCleanupTask()
        mainCompanion.setupMetrics()
        mainCompanion.checkUpdates()

        loadTime += timer.timer
        Log.infKey("console.lifecycle.startup-complete", mapOf("time" to loadTime.toString()))
    }

    override fun onDisable() {
        val disableTimer = QuickTimer()
        disableTimer.start()

        levelManager.stopNametagAutoUpdateTask()
        mainCompanion.shutDownAsyncTasks()

        Log.infKey("console.lifecycle.shutdown-complete", mapOf("time" to disableTimer.timer.toString()))
    }

    private fun prepareToLoadCustomDrops(){
        if (Bukkit.getPluginManager().getPlugin("LM_Items") == null &&
            mainCompanion.showCustomDrops) {
            customDropsHandler.customDropsParser.showCustomDropsDebugInfo(null)
        }
    }

    fun reloadLM(sender: CommandSender, onComplete: Runnable? = null) {
        Bukkit.getGlobalRegionScheduler().execute(this) {
            reloadLMOnGlobal(sender)
            onComplete?.run()
        }
    }

    private fun reloadLMOnGlobal(sender: CommandSender) {
        NotifyManager.clearLastError()
        mainCompanion.errorMessages.clear()
        customDropsHandler.customDropsParser.invalidExternalItems.clear()
        var reloadStartedMsg = messagesCfg.getStringList(
            "command.levelledmobs.reload.started"
        )
        reloadStartedMsg = Utils.replaceAllInList(
            reloadStartedMsg, "%prefix%",
            configUtils.prefix
        )
        reloadStartedMsg = Utils.colorizeAllInList(reloadStartedMsg)
        sendReloadMessages(sender, reloadStartedMsg)

        mainCompanion.reloadSender = sender
        mainCompanion.loadFiles()
        mainCompanion.checkListenersWithVariablePriorities()
        chunkLoadListener.load()
        nametagQueueManager.onLoadOrReload()
        RandomLevellingStrategy.clearCache()

        var reloadFinishedMsg = messagesCfg.getStringList(
            "command.levelledmobs.reload.finished"
        )
        reloadFinishedMsg = Utils.replaceAllInList(
            reloadFinishedMsg, "%prefix%",
            configUtils.prefix
        )
        reloadFinishedMsg = Utils.colorizeAllInList(reloadFinishedMsg)

        if (helperSettings.getBoolean("ensure-mobs-are-levelled-on-chunk-load")
            && !configUtils.chunkLoadListenerWasEnabled
        ) {
            configUtils.chunkLoadListenerWasEnabled = true
            Bukkit.getPluginManager().registerEvents(chunkLoadListener, this)
        } else if (!helperSettings.getBoolean( "ensure-mobs-are-levelled-on-chunk-load")
            && configUtils.chunkLoadListenerWasEnabled
        ) {
            configUtils.chunkLoadListenerWasEnabled = false
            HandlerList.unregisterAll(chunkLoadListener)
        }

        rulesManager.clearTempDisabledRulesCounts()
        definitions.useTranslationComponents = helperSettings.getBoolean(
            "use-translation-components", true
        )
        definitions.setUseLegacySerializer(
            helperSettings.getBoolean(
                "use-legacy-serializer", true
            )
        )
        mainCompanion.checkSettingsWithMaxPlayerOptions()
        nametagQueueManager.nametagSenderHandler.refresh()
        nametagQueueManager.refreshAllTrackedEntities()

        sendReloadMessages(sender, reloadFinishedMsg)

        if (sender is Player) runForSender(sender) {
            if (customDropsHandler.customDropsParser.hadParsingError)
                LocalizedMessages.send(sender, "other.customdrops-parse-error")
            if (mainCompanion.hadRulesLoadError)
                sender.sendMessage(FileLoader.getFileLoadErrorMessage())
            if (mainCompanion.errorMessages.isNotEmpty())
                LocalizedMessages.send(sender, "other.errors-reported")
        }
    }

    private fun sendReloadMessages(sender: CommandSender, messages: List<String>) {
        runForSender(sender) { messages.forEach(sender::sendMessage) }
    }

    private fun runForSender(sender: CommandSender, action: Runnable) {
        if (sender is Player && !Bukkit.isOwnedByCurrentRegion(sender))
            sender.scheduler.run(this, { action.run() }, null)
        else
            action.run()
    }
}
