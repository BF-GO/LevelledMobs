package io.github.arcaneplugins.levelledmobs.managers

import io.github.arcaneplugins.levelledmobs.LevelInterface2
import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.LivingEntityInterface
import io.github.arcaneplugins.levelledmobs.MainCompanion
import io.github.arcaneplugins.levelledmobs.customdrops.EquippedItemsInfo
import io.github.arcaneplugins.levelledmobs.debug.DebugManager
import io.github.arcaneplugins.levelledmobs.debug.DebugType
import io.github.arcaneplugins.levelledmobs.enums.Addition
import io.github.arcaneplugins.levelledmobs.enums.AttributeNames
import io.github.arcaneplugins.levelledmobs.enums.InternalSpawnReason
import io.github.arcaneplugins.levelledmobs.enums.LevellableState
import io.github.arcaneplugins.levelledmobs.enums.MobCustomNameStatus
import io.github.arcaneplugins.levelledmobs.enums.MobTamedStatus
import io.github.arcaneplugins.levelledmobs.enums.NametagVisibilityEnum
import io.github.arcaneplugins.levelledmobs.events.MobPostLevelEvent
import io.github.arcaneplugins.levelledmobs.events.MobPreLevelEvent
import io.github.arcaneplugins.levelledmobs.events.SummonedMobPreLevelEvent
import io.github.arcaneplugins.levelledmobs.listeners.EntitySpawnListener
import io.github.arcaneplugins.levelledmobs.misc.EvaluationException
import io.github.arcaneplugins.levelledmobs.misc.NamespacedKeys
import io.github.arcaneplugins.levelledmobs.misc.PickedUpEquipment
import io.github.arcaneplugins.levelledmobs.misc.QueueItem
import io.github.arcaneplugins.levelledmobs.misc.StringReplacer
import io.github.arcaneplugins.levelledmobs.result.AdditionalLevelInformation
import io.github.arcaneplugins.levelledmobs.result.AttributePreMod
import io.github.arcaneplugins.levelledmobs.result.MinAndMaxHolder
import io.github.arcaneplugins.levelledmobs.result.NBTApplyResult
import io.github.arcaneplugins.levelledmobs.result.NametagResult
import io.github.arcaneplugins.levelledmobs.result.PlayerLevelSourceResult
import io.github.arcaneplugins.levelledmobs.result.PlayerNetherOrWorldSpawnResult
import io.github.arcaneplugins.levelledmobs.rules.CustomDropsRuleSet
import io.github.arcaneplugins.levelledmobs.rules.RulesManager
import io.github.arcaneplugins.levelledmobs.rules.strategies.RandomVarianceGenerator
import io.github.arcaneplugins.levelledmobs.rules.strategies.StrategyType
import io.github.arcaneplugins.levelledmobs.util.Log
import io.github.arcaneplugins.levelledmobs.util.LocalizedMessages
import io.github.arcaneplugins.levelledmobs.util.MiscUtils
import io.github.arcaneplugins.levelledmobs.util.MythicMobUtils
import io.github.arcaneplugins.levelledmobs.util.Utils
import io.github.arcaneplugins.levelledmobs.wrappers.LivingEntityWrapper
import io.github.arcaneplugins.levelledmobs.wrappers.SchedulerResult
import io.github.arcaneplugins.levelledmobs.wrappers.SchedulerWrapper
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.enchantments.EnchantmentTarget
import org.bukkit.entity.ChestedHorse
import org.bukkit.entity.Creeper
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Horse
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Vehicle
import org.bukkit.entity.Zombie
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.concurrent.TimeoutException
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt


/**
 * Генерирует уровни и управляет другими функциями, связанными с прокачкой мобов.
 *
 * @author lokka30, stumper66, CoolBoy, Esophose, 7smile7, Shevchik, Hugo5551, limzikiki
 * @since 2.4.0
 */
class LevelManager : LevelInterface2 {
    private var vehicleNoMultiplierItems = mutableListOf<Material>()
    val summonedOrSpawnEggs = ConcurrentHashMap.newKeySet<UUID>()

    private var hasMentionedNBTAPIMissing = false
    var doCheckMobHash = false
    private var lastLEWCacheClearing: Instant? = null
    val entitySpawnListener = EntitySpawnListener()
    private var cacheCleanupTask: SchedulerResult? = null
    private val attributeStringList = mutableMapOf<String, Attribute>()
    private val strategyPlaceholders = mutableMapOf<String, StrategyType>()
    /**
     * Следующим типам сущностей нельзя назначать уровень.
     */
    var forcedBlockedEntityTypes = mutableSetOf<EntityType>()

    fun load(){
        attributeStringList.putAll(mutableMapOf(
            "%max-health%" to Utils.getAttribute(AttributeNames.MAX_HEALTH)!!,
            "%movement-speed%" to Utils.getAttribute(AttributeNames.MOVEMENT_SPEED)!!,
            "%attack-damage%" to Utils.getAttribute(AttributeNames.ATTACK_DAMAGE)!!,
            "%follow-range%" to Utils.getAttribute(AttributeNames.FOLLOW_RANGE)!!,
            "%armor-bonus%" to Utils.getAttribute(AttributeNames.ARMOR)!!,
            "%armor-toughness%" to Utils.getAttribute(AttributeNames.ARMOR_TOUGHNESS)!!,
            "%attack-knockback%" to Utils.getAttribute(AttributeNames.ATTACK_KNOCKBACK)!!,
            "%knockback-resistance%" to Utils.getAttribute(AttributeNames.KNOCKBACK_RESISTANCE)!!,
            "%zombie-spawn-reinforcements%" to Utils.getAttribute(AttributeNames.SPAWN_REINFORCEMENTS)!!
        ))

        strategyPlaceholders.putAll(mutableMapOf(
            "%random%" to StrategyType.RANDOM,
            "%weighted-random%" to StrategyType.WEIGHTED_RANDOM,
            "%random-variance-mod%" to StrategyType.RANDOM_VARIANCE,
            "%custom-strategy%" to StrategyType.CUSTOM,
            "%distance-from-origin%" to StrategyType.SPAWN_DISTANCE,
            "%y-coordinate%" to StrategyType.Y_COORDINATE,
            "%player-variable-mod%" to StrategyType.PLAYER_VARIABLE
        ))

        this.vehicleNoMultiplierItems.addAll(mutableListOf(
            Material.SADDLE,
            Material.LEATHER_HORSE_ARMOR,
            Material.IRON_HORSE_ARMOR,
            Material.GOLDEN_HORSE_ARMOR,
            Material.DIAMOND_HORSE_ARMOR
        ))

        this.forcedBlockedEntityTypes.addAll(
            mutableListOf(
                EntityType.AREA_EFFECT_CLOUD,
                EntityType.ARMOR_STAND,
                EntityType.ARROW,
                EntityType.DRAGON_FIREBALL,
                EntityType.EGG,
                mapLegacyEntityTypeName("ENDER_CRYSTAL"),
                EntityType.ENDER_PEARL,
                mapLegacyEntityTypeName("ENDER_SIGNAL"),
                EntityType.EXPERIENCE_ORB,
                EntityType.FALLING_BLOCK,
                EntityType.FIREBALL,
                mapLegacyEntityTypeName("FIREWORK"),
                mapLegacyEntityTypeName("FISHING_HOOK"),
                EntityType.ITEM_FRAME,
                mapLegacyEntityTypeName("LEASH_HITCH"),
                mapLegacyEntityTypeName("LIGHTNING"),
                EntityType.LLAMA_SPIT,
                EntityType.MINECART,
                mapLegacyEntityTypeName("MINECART_CHEST"),
                mapLegacyEntityTypeName("MINECART_COMMAND"),
                mapLegacyEntityTypeName("MINECART_FURNACE"),
                mapLegacyEntityTypeName("MINECART_HOPPER"),
                mapLegacyEntityTypeName("MINECART_MOB_SPAWNER"),
                mapLegacyEntityTypeName("MINECART_TNT"),
                EntityType.PAINTING,
                mapLegacyEntityTypeName("PRIMED_TNT"),
                EntityType.SMALL_FIREBALL,
                EntityType.SNOWBALL,
                EntityType.SPECTRAL_ARROW,
                mapLegacyEntityTypeName("SPLASH_POTION"),
                mapLegacyEntityTypeName("THROWN_EXP_BOTTLE"),
                EntityType.TRIDENT,
                EntityType.UNKNOWN,
                EntityType.WITHER_SKULL,
                EntityType.SHULKER_BULLET,
                EntityType.PLAYER
            )
        )
    }

    private fun mapLegacyEntityTypeName(
        name: String
    ): EntityType{
        return if (LevelledMobs.instance.ver.minorVersion >= 21){
            when (name){
                "ENDER_CRYSTAL" -> EntityType.END_CRYSTAL
                "ENDER_SIGNAL" -> EntityType.EYE_OF_ENDER
                "FIREWORK" -> EntityType.FIREWORK_ROCKET
                "FISHING_HOOK" -> EntityType.FISHING_BOBBER
                "LEASH_HITCH" -> EntityType.LEASH_KNOT
                "LIGHTNING" -> EntityType.LIGHTNING_BOLT
                "MINECART_CHEST" -> EntityType.CHEST_MINECART
                "MINECART_COMMAND" -> EntityType.COMMAND_BLOCK_MINECART
                "MINECART_FURNACE" -> EntityType.FURNACE_MINECART
                "MINECART_HOPPER" -> EntityType.HOPPER_MINECART
                "MINECART_MOB_SPAWNER" -> EntityType.SPAWNER_MINECART
                "MINECART_TNT" -> EntityType.TNT_MINECART
                "PRIMED_TNT" -> EntityType.TNT
                "SPLASH_POTION" -> EntityType.SPLASH_POTION
                "THROWN_EXP_BOTTLE" -> EntityType.EXPERIENCE_BOTTLE
                else -> EntityType.UNKNOWN
            }
        }
        else
            EntityType.valueOf(name)
    }

    /**
     * Этот метод генерирует уровень для моба. Он использует стратегию назначения уровня, указанный
     * администратором через конфигурацию settings.yml.
     *
     *
     * Потокобезопасность предусмотрена, но не проверена.
     *
     * @param lmEntity сущность, генерирующая уровень для
     * @return уровень для сущности
     */
    override fun generateLevel(lmEntity: LivingEntityWrapper): Int {
        return generateLevel(lmEntity, -1, -1)
    }

    /**
     * Этот метод генерирует уровень для моба. Он использует стратегию назначения уровня, указанный
     * администратором через конфигурацию settings.yml.
     *
     *
     * Потокобезопасность предусмотрена, но не проверена.
     *
     * @param lmEntity     сущность, генерирующая уровень для
     * @param minLevel минимальный уровень, который будет использоваться для моба
     * @param maxLevel максимальный уровень, который будет использоваться для моба
     * @return уровень для сущности
     */
    override fun generateLevel(
        lmEntity: LivingEntityWrapper,
        minLevel: Int,
        maxLevel: Int
    ): Int {
        var useMinLevel = minLevel
        var useMaxLevel = maxLevel

        if (useMinLevel == -1 || useMaxLevel == -1) {
            val levels = getMinAndMaxLevels(lmEntity)
            if (useMinLevel == -1)
                useMinLevel = levels.minAsInt

            if (useMaxLevel == -1)
                useMaxLevel = levels.maxAsInt
        }

        val levellingStrategies = LevelledMobs.instance.rulesManager.getRuleLevellingStrategies(
            lmEntity
        )
        val customStrategies = LevelledMobs.instance.rulesManager.getRuleCustomStrategies(lmEntity)

        var numberResult = 0f
        val debugId = DebugManager.startLongDebugMessage()

        try{
            for ((count, strategy) in levellingStrategies.withIndex()){
                val result = strategy.generateNumber(lmEntity, useMinLevel, useMaxLevel)
                lmEntity.strategyResults[strategy.strategyType] = result

                DebugManager.logLongMessage(debugId){
                    if (count > 0) LocalizedMessages.text("command.levelledmobs.debug.runtime.d052", mapOf("value-1" to (strategy.strategyType), "value-2" to (result)), false)
                    else LocalizedMessages.text("command.levelledmobs.debug.runtime.d053", mapOf("value-1" to (strategy.strategyType), "value-2" to (result)), false)
                }

                numberResult += result
            }

            for ((count, strategy) in customStrategies.withIndex()){
                val result = strategy.generateNumber(lmEntity, useMinLevel, useMaxLevel)
                lmEntity.customStrategyResults[strategy.placeholderName] = result

                DebugManager.logLongMessage(debugId){
                    if (count > 0) LocalizedMessages.text("command.levelledmobs.debug.runtime.d054", mapOf("value-1" to (strategy.placeholderName), "value-2" to (result)), false)
                    else LocalizedMessages.text("command.levelledmobs.debug.runtime.d055", mapOf("value-1" to (strategy.placeholderName), "value-2" to (result)), false)
                }

                numberResult += result
            }
        }
        finally {
            DebugManager.endLongMessage(debugId, DebugType.STRATEGY_RESULT, lmEntity)
        }

        // если стратегия назначения уровня не была выбрана, мы просто используем случайное число между минимальным и максимальным значением.
        if (useMinLevel == useMaxLevel) return useMinLevel

        val generatedLevel = constructLevel(numberResult, lmEntity)
            .coerceAtMost(useMaxLevel)
            .coerceAtLeast(useMinLevel)

        return generatedLevel
    }

    private fun constructLevel(
        input: Float,
        lmEntity: LivingEntityWrapper
    ): Int{
        val formulaPre = LevelledMobs.instance.rulesManager.getRuleConstructLevel(lmEntity) ?: return input.roundToInt()
        val formula = replaceStringPlaceholdersForFormulas(formulaPre, lmEntity)
        val evalResult = MobDataManager.evaluateExpression(formula)
        if (evalResult.hadError){
            NotifyManager.notifyOfErrorKey("console.formula.construct-level-error", mapOf(
                "entity" to lmEntity.nameIfBaby,
                "error" to (evalResult.error ?: "-")
            ))
            DebugManager.log(DebugType.CONSTRUCT_LEVEL, lmEntity){
                val msg = if (formula == formulaPre)
                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d056", mapOf("value-1" to (formula)), false)
                else
                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d057", mapOf("value-1" to (formulaPre), "value-2" to (formula)), false)

                LocalizedMessages.text("command.levelledmobs.debug.runtime.d058", mapOf("value-1" to (evalResult.error), "value-2" to (msg)), false) }
            throw EvaluationException()
        }

        val result = floor(evalResult.result).toInt()

        DebugManager.log(DebugType.CONSTRUCT_LEVEL, lmEntity){
            val msg = if (formula == formulaPre)
                LocalizedMessages.text("command.levelledmobs.debug.runtime.d059", mapOf("value-1" to (formula)), false)
            else
                LocalizedMessages.text("command.levelledmobs.debug.runtime.d060", mapOf("value-1" to (formulaPre), "value-2" to (formula)), false)

            LocalizedMessages.text("command.levelledmobs.debug.runtime.d061", mapOf("value-1" to (result), "value-2" to (msg)), false)}
        return result
    }

    fun getPlayerLevelSourceNumber(
        player: Player?,
        lmEntity: LivingEntityWrapper,
        variableToUse: String
    ): PlayerLevelSourceResult {
        if (player == null) return PlayerLevelSourceResult(1f)

        val origLevelSource: Float
        var homeNameUsed = "spawn"

        if ("%level%".equals(variableToUse, ignoreCase = true))
            origLevelSource = player.level.toFloat()
        else if ("%exp%".equals(variableToUse, ignoreCase = true))
            origLevelSource = player.exp
        else if ("%exp-to-level%".equals(variableToUse, ignoreCase = true))
            origLevelSource = player.expToLevel.toFloat()
        else if ("%total-exp%".equals(variableToUse, ignoreCase = true))
            origLevelSource = player.totalExperience.toFloat()
        else if ("%world-time-ticks%".equals(variableToUse, ignoreCase = true))
            origLevelSource = player.world.time.toFloat()
        else if ("%home-distance%".equals(variableToUse, ignoreCase = true)
            || "%home-distance-with-bed%".equals(variableToUse, ignoreCase = true)
        ) {
            val allowBed = "%home-distance-with-bed%".equals(variableToUse, ignoreCase = true)
            val netherOrWorldSpawnResult: PlayerNetherOrWorldSpawnResult
            val result = ExternalCompatibilityManager.getPlayerHomeLocation(
                player, allowBed
            )
            if (result.homeNameUsed != null)
                homeNameUsed = result.homeNameUsed!!

            var useLocation = result.location
            if (useLocation == null || useLocation.world != player.world) {
                netherOrWorldSpawnResult = Utils.getPortalOrWorldSpawn(player)
                useLocation = netherOrWorldSpawnResult.location
                homeNameUsed = if (netherOrWorldSpawnResult.isWorldPortalLocation)
                    "world-portal"
                else if (netherOrWorldSpawnResult.isNetherPortalLocation)
                    "nether-portal"
                else
                    "spawn"
            }

            if (result.resultMessage != null)
                DebugManager.log(DebugType.PLAYER_LEVELLING, lmEntity) { result.resultMessage }

            origLevelSource = useLocation!!.distance(player.location).toFloat()
        } else if ("%bed-distance%".equals(variableToUse, ignoreCase = true)) {
            var useLocation = player.respawnLocation
            homeNameUsed = "bed"

            if (useLocation == null || useLocation.world !== player.world) {
                val result = Utils.getPortalOrWorldSpawn(player)
                useLocation = result.location
                homeNameUsed = if (result.isWorldPortalLocation)
                    "world-portal"
                else if (result.isNetherPortalLocation)
                    "nether-portal"
                else
                    "spawn"
            }

            origLevelSource = useLocation!!.distance(player.location).toFloat()
        } else {
            var usePlayerLevel = false
            var papiResult: String? = null

            if (ExternalCompatibilityManager.hasPapiInstalled) {
                papiResult = ExternalCompatibilityManager.getPapiPlaceholder(player, variableToUse, lmEntity.invalidPlaceholderReplacement)
                if (papiResult.isEmpty()) {
                    val l = player.location
                    DebugManager.log(DebugType.PLAYER_LEVELLING, lmEntity) {
                        LocalizedMessages.text("command.levelledmobs.debug.runtime.d062", mapOf("value-1" to (variableToUse), "value-2" to (player.name), "value-3" to (l.blockX), "value-4" to (l.blockY), "value-5" to (l.blockZ), "value-6" to (player.world.name)), false)
                    }
                    usePlayerLevel = true
                }
            } else {
                Log.warKey("console.integration.placeholderapi-missing", mapOf("variable" to variableToUse))
                usePlayerLevel = true
            }

            if (usePlayerLevel)
                origLevelSource = player.level.toFloat()
            else {
                val l = player.location
                if (papiResult.isNullOrEmpty()) {
                    origLevelSource = player.level.toFloat()
                    DebugManager.log(DebugType.PLAYER_LEVELLING, lmEntity) {
                        LocalizedMessages.text("command.levelledmobs.debug.runtime.d063", mapOf("value-1" to (variableToUse), "value-2" to (player.name), "value-3" to (l.blockX), "value-4" to (l.blockY), "value-5" to (l.blockZ), "value-6" to (player.world.name)), false)
                    }
                } else {
                    if (Utils.isDouble(papiResult)) {
                        origLevelSource = try {
                            papiResult.toFloat()
                        } catch (_: Exception) {
                            player.level.toFloat()
                        }
                    } else {
                        val result = PlayerLevelSourceResult(papiResult)
                        result.homeNameUsed = homeNameUsed
                        return result
                    }
                }
            }
        }

        val sourceResult = PlayerLevelSourceResult(origLevelSource)
        val maxRandomVariance = LevelledMobs.instance.rulesManager.getRuleMaxRandomVariance(lmEntity)

        if (maxRandomVariance != null) {
            sourceResult.randomVarianceResult = ThreadLocalRandom.current().nextInt(0, maxRandomVariance + 1).toFloat()
            if (ThreadLocalRandom.current().nextBoolean())
                sourceResult.randomVarianceResult = sourceResult.randomVarianceResult!! * -1
        }

        sourceResult.homeNameUsed = homeNameUsed
        return sourceResult
    }

    fun getMinAndMaxLevels(lmInterface: LivingEntityInterface): MinAndMaxHolder {
        // финальное EntityType entityType, финальное логическое значение isAdultEntity, финальная строка worldName
        // если вызывается из команды вызова, то lmEntity имеет значение null

        val main = LevelledMobs.instance
        var minLevel = main.rulesManager.getRuleMobMinLevel(lmInterface)
        var maxLevel = main.rulesManager.getRuleMobMaxLevel(lmInterface)

        maxLevel = maxLevel.coerceAtLeast(0)
        minLevel = minLevel.coerceAtMost(maxLevel)

        return MinAndMaxHolder(minLevel, maxLevel)
    }

    // Это устанавливает уровневый currentDrops на уровневого моба, который только что умер.
    fun setLevelledItemDrops(
        lmEntity: LivingEntityWrapper,
        currentDrops: MutableList<ItemStack>,
        disableItemBoost: Boolean
    ) {
        val vanillaDrops = currentDrops.size
        // сюда входят животные с грудью, седла и доспехи на ездовых существах.
        //val dropsToMultiply = getDropsToMultiply(lmEntity, currentDrops)
        val customDrops = mutableListOf<ItemStack>()
        //currentDrops.clear()

        val main = LevelledMobs.instance
        val doNotMultiplyDrops = disableItemBoost ||
                main.rulesManager.getRuleCheckIfNoDropMultiplierEntitiy(lmEntity)
        var hasOverride = false

        if (lmEntity.lockedCustomDrops != null || main.rulesManager.getRuleUseCustomDropsForMob(lmEntity).useDrops) {
            // пользовательский дроп также умножается в обработчике пользовательского дропа.
            val dropResult = main.customDropsHandler.getCustomItemDrops(
                lmEntity,
                customDrops, false
            )

            val mmInfo = MythicMobUtils.getMythicMobInfo(lmEntity)
            if (mmInfo != null && mmInfo.preventOtherDrops)
                hasOverride = true

            if (dropResult.hasOverride) hasOverride = true
            if (hasOverride) removeVanillaDrops(lmEntity, currentDrops)

        }

        var additionUsed = 0

        if (!doNotMultiplyDrops && currentDrops.isNotEmpty()) {
            // Добавляйте currentDrops за значение уровня.
            val additionValue = main.mobDataManager.getAdditionsForLevel(
                lmEntity,
                Addition.CUSTOM_ITEM_DROP, 2.0f
            ).multiplierAmount
            if (additionValue == Float.MIN_VALUE) {
                DebugManager.log(DebugType.SET_LEVELLED_ITEM_DROPS, lmEntity) {
                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d064", colorize = false)
                }
                removeVanillaDrops(lmEntity, currentDrops)
                return
            }

            additionUsed = additionValue.roundToInt()
            val itemsToNotMultiply = mutableListOf<ItemStack>()

            if (lmEntity.livingEntity.equipment != null){
                // убедитесь, что мы не умножаем ничего, что он подобрал
                itemsToNotMultiply.addAll(removePickedUpItems(lmEntity, currentDrops))
            }

            // Изменить текущие падения
            for (currentDrop in currentDrops) {
                var skipItem = false
                val iterator = itemsToNotMultiply.iterator()
                while (iterator.hasNext()){
                    val itemToSkip = iterator.next()
                    if (itemToSkip.isSimilar(currentDrop)){
                        skipItem = true
                        iterator.remove()
                    }
                }

                if (!skipItem) multiplyDrop(lmEntity, currentDrop, additionValue)
            }
        }

        if (customDrops.isNotEmpty()) currentDrops.addAll(customDrops)

        val nameWithOverride = if (hasOverride) " (override), " else ""
        val additionUsedFinal = additionUsed
        DebugManager.log(DebugType.SET_LEVELLED_ITEM_DROPS, lmEntity) {
            LocalizedMessages.text("command.levelledmobs.debug.runtime.d065", mapOf("value-1" to (nameWithOverride), "value-2" to (vanillaDrops), "value-3" to (currentDrops.size), "value-4" to (additionUsedFinal)), false)
        }
    }

    fun multiplyDrop(
        lmEntity: LivingEntityWrapper,
        currentDrop: ItemStack,
        addition: Float,
    ) {
        if (LevelledMobs.instance.mobDataManager.isLevelledDropManaged(currentDrop.type)) {
            DebugManager.log(DebugType.SET_LEVELLED_ITEM_DROPS, lmEntity) { LocalizedMessages.text("command.levelledmobs.debug.runtime.d066", colorize = false) }
            return
        }

        val oldAmount = currentDrop.amount
        val useAmount = ((currentDrop.amount + (currentDrop.amount.toFloat() * addition)).roundToInt())
            .coerceAtMost(currentDrop.maxStackSize)

        currentDrop.amount = useAmount
        DebugManager.log(DebugType.SET_LEVELLED_ITEM_DROPS, lmEntity) {
            LocalizedMessages.text("command.levelledmobs.debug.runtime.d067", mapOf("value-1" to (currentDrop.type), "value-2" to (oldAmount), "value-3" to (addition)), false) +
                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d068", mapOf("value-1" to (currentDrop.amount)), false)
        }
    }

    private fun removePickedUpItems(
        lmEntity: LivingEntityWrapper,
        drops: MutableList<ItemStack>
    ): MutableList<ItemStack>{
        val removedItems = mutableListOf<ItemStack>()
        val pickedUpItems = PickedUpEquipment(lmEntity).getMobPickedUpItems()
        if (pickedUpItems.isEmpty()) return removedItems

        val iteratorPickedUpItems = pickedUpItems.listIterator()

        while (iteratorPickedUpItems.hasNext()) {
            val foundItem = iteratorPickedUpItems.next()
            for (mobItem in drops) {
                if (mobItem.isSimilar(foundItem)) {
                    iteratorPickedUpItems.remove()
                    removedItems.add(mobItem)
                    break
                }
            }
        }

        return removedItems
    }

    fun removeVanillaDrops(
        lmEntity: LivingEntityWrapper,
        drops: MutableList<ItemStack>
    ) {
        var hadSaddle = false
        val itemsToKeep = mutableListOf<ItemStack>()

        if (lmEntity.livingEntity is ChestedHorse
            && (lmEntity.livingEntity as ChestedHorse).isCarryingChest
        ) {
            val inv = (lmEntity.livingEntity as ChestedHorse).inventory
            itemsToKeep.add(ItemStack(Material.CHEST))
            for (item in inv.contents){
                if (item != null) itemsToKeep.add(item)
            }
        } else if (lmEntity.livingEntity is Vehicle) {
            for (itemStack in drops) {
                if (itemStack.type == Material.SADDLE) {
                    hadSaddle = true
                    break
                }
            }
        }

        if (LevelledMobs.instance.ver.isRunningPaper) {
            val pickedUpItems = PickedUpEquipment(lmEntity).getMobPickedUpItems()
            itemsToKeep.addAll(pickedUpItems)
        }

        drops.clear()
        drops.addAll(itemsToKeep)
        if (hadSaddle) drops.add(ItemStack(Material.SADDLE))
    }

    //Вычисляет XP, выпадающий при смерти уровневого моба.
    fun getLevelledExpDrops(
        lmEntity: LivingEntityWrapper,
        xp: Double
    ): Int {
        if (lmEntity.isLevelled) {
            val dropAddition: Float = LevelledMobs.instance.mobDataManager.getAdditionsForLevel(
                lmEntity,
                Addition.CUSTOM_XP_DROP, 3.0f
            ).multiplierAmount
            var newXp = 0.0

            if (dropAddition == Float.MIN_VALUE) {
                DebugManager.log(DebugType.SET_LEVELLED_XP_DROPS, lmEntity) {
                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d069", mapOf("value-1" to (xp)), false)
                }
                return 0
            }

            if (dropAddition > -1) newXp = (xp + (xp * dropAddition)).roundToInt().toDouble()

            val newXpFinal = newXp.toInt()
            DebugManager.log(DebugType.SET_LEVELLED_XP_DROPS, lmEntity) {
                LocalizedMessages.text("command.levelledmobs.debug.runtime.d070", mapOf("value-1" to (xp), "value-2" to (newXpFinal)), false)
            }
            return newXp.toInt()
        }
        else
            return xp.toInt()
    }

    fun getNametag(
        lmEntity: LivingEntityWrapper,
        isDeathNametag: Boolean
    ): NametagResult {
        return getNametag(lmEntity, isDeathNametag, false)
    }

    fun getNametag(
        lmEntity: LivingEntityWrapper,
        isDeathNametag: Boolean,
        preserveMobName: Boolean
    ): NametagResult {
        var usePreserveMobName = preserveMobName
        var nametag: StringReplacer
        var customDeathMessage: String? = null
        val main = LevelledMobs.instance
        if (isDeathNametag)
            nametag = StringReplacer(main.rulesManager.getRuleNametagCreatureDeath(lmEntity))
        else {
            checkLockedNametag(lmEntity)

            val nametagText =
                if (lmEntity.lockedNametag == null || lmEntity.lockedNametag!!.isEmpty()) main.rulesManager.getRuleNametag(
                    lmEntity
                ) else lmEntity.lockedNametag!!
            nametag = StringReplacer(nametagText)
        }

        if ("disabled".equals(nametag.text, ignoreCase = true) || "none".equals(nametag.text, ignoreCase = true))
            return NametagResult(null)

        if (isDeathNametag) {
            val deathMessage = main.rulesManager.getDeathMessage(lmEntity)
            if (!deathMessage.isNullOrEmpty()) {
                nametag = StringReplacer(deathMessage.replace("%death_nametag%", nametag.text))
                val player = lmEntity.associatedPlayer
                nametag.replace("%player%", if (player != null) player.name + "&r" else "")
                nametag.text = replaceStringPlaceholders(nametag.text, lmEntity, true, player, false)
                usePreserveMobName = true

                customDeathMessage =
                    if (nametag.contains("{DisplayName}")) main.rulesManager.getRuleNametagCreatureDeath(lmEntity) else nametag.text
            }
        }

        // игнорировать, если «отключено»
        if (nametag.isEmpty) {
            val useCustomNameForNametags = main.helperSettings.getBoolean(
                "use-customname-for-mob-nametags"
            )
            return if (useCustomNameForNametags)
                NametagResult(lmEntity.typeName)
            else {
                @Suppress("DEPRECATION")
                NametagResult(lmEntity.livingEntity.customName) // CustomName может иметь значение null, так и должно быть.
            }
        }
        if (!lmEntity.isLevelled) nametag.text = ""

        return updateNametag(lmEntity, nametag, usePreserveMobName, customDeathMessage)
    }

    fun updateNametag(
        lmEntity: LivingEntityWrapper,
        nametag: StringReplacer,
        preserveMobName: Boolean,
        customDeathMessage: String?
    ): NametagResult {
        if (nametag.isEmpty) {
            val result = NametagResult(nametag.text)
            result.customDeathMessage = customDeathMessage
            return result
        }

        checkLockedNametag(lmEntity)
        val overridenName = if (lmEntity.lockedOverrideName == null) LevelledMobs.instance.rulesManager.getRuleEntityOverriddenName(
            lmEntity,
            false
        ) else lmEntity.lockedOverrideName!!

        replaceStringPlaceholders(nametag, lmEntity, false, null, preserveMobName)

        var indicatorStr = ""
        var colorOnly = ""

        if (nametag.text.contains("%health-indicator%") ||
            nametag.text.contains("%health-indicator-color%")
        ) {
            val indicator = LevelledMobs.instance.rulesManager.getRuleNametagIndicator(lmEntity)

            if (indicator != null) {
                val result = indicator.formatHealthIndicator(lmEntity)
                indicatorStr = result.formattedString + "&r"
                colorOnly = result.colorOnly
            }
        }

        nametag.replace("%health-indicator%", indicatorStr)
        nametag.replace("%health-indicator-color%", colorOnly)

        if (nametag.text.contains("%") && ExternalCompatibilityManager.hasPapiInstalled)
            nametag.text = ExternalCompatibilityManager.getPapiPlaceholder(
                lmEntity.associatedPlayer,
                nametag.text,
                lmEntity.invalidPlaceholderReplacement
            )

        val result = NametagResult(nametag.text)
        // это поле используется только для отправки бейджей клиенту
        result.overriddenName = overridenName
        result.customDeathMessage = customDeathMessage
        result.killerMob = lmEntity.livingEntity

        return result
    }

    private fun checkLockedNametag(lmEntity: LivingEntityWrapper) {
        synchronized(lmEntity.livingEntity.persistentDataContainer) {
            val doLockSettings: Int?
            if (lmEntity.pdc
                .has(NamespacedKeys.lockSettings, PersistentDataType.INTEGER)
            ) {
                doLockSettings = lmEntity.pdc
                    .get(NamespacedKeys.lockSettings, PersistentDataType.INTEGER)
                if (doLockSettings == null || doLockSettings != 1)
                    return
            } else {
                return
            }

            if (lmEntity.pdc
                    .has(NamespacedKeys.lockedNametag, PersistentDataType.STRING)
            ) {
                lmEntity.lockedNametag = lmEntity.pdc
                    .get(NamespacedKeys.lockedNametag, PersistentDataType.STRING)
            }
            if (lmEntity.pdc
                    .has(NamespacedKeys.lockedNameOverride, PersistentDataType.STRING)
            ) {
                lmEntity.lockedOverrideName = lmEntity.pdc
                    .get(NamespacedKeys.lockedNameOverride, PersistentDataType.STRING)
            }
        }
    }

    private fun getAttributesCache(lmEntity: LivingEntityWrapper, str: StringReplacer){
        if (lmEntity.attributeValuesCache != null) return

        val whichOnes = mutableListOf<Attribute>()
        for (placeholder in attributeStringList){
            if (str.text.contains(placeholder.key))
                whichOnes.add(placeholder.value)
        }

        MobDataManager.instance.getAllAttributeValues(lmEntity, whichOnes)
    }

    fun replaceStringPlaceholdersForFormulas(
        text: String,
        lmEntity: LivingEntityWrapper
    ): String{
        val str = StringReplacer(text)

        if (str.text.contains("%level-ratio%", ignoreCase = true)){
            val mobLevel = lmEntity.mobLevel ?: lmEntity.getMobLevel
            val maxLevel = RulesManager.instance.getRuleMobMaxLevel(lmEntity).toFloat()
            val minLevel = RulesManager.instance.getRuleMobMinLevel(lmEntity).toFloat()
            if (mobLevel == 0 || maxLevel == 0f){
                str.replace("%level-ratio%", "0")
                DebugManager.log(DebugType.LEVEL_RATIO, lmEntity){ LocalizedMessages.text("command.levelledmobs.debug.runtime.d071", colorize = false) }
            }
            else{
                val newValue: Float
                val part1 = (mobLevel.toFloat() - minLevel)
                val part2 = (maxLevel - minLevel)
                newValue = if (part2 == 0f)
                    1f
                else if (part1 == 0f)
                    0f
                else
                    (part1 / part2).coerceAtLeast(0f)

                str.replace("%level-ratio%", newValue.toString())
                DebugManager.log(DebugType.LEVEL_RATIO, lmEntity){
                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d072", mapOf("value-1" to (mobLevel.toFloat()), "value-2" to (minLevel), "value-3" to (maxLevel), "value-4" to (minLevel), "value-5" to (newValue)), false)
                }
            }
        }

        str.replaceIfExists("%ranged-attack-damage%"){
            if (lmEntity.rangedDamage != null) lmEntity.rangedDamage.toString() else "0"
        }

        for (placeholder in strategyPlaceholders){
            str.replaceIfExists(placeholder.key){
                lmEntity.strategyResults.getOrDefault(placeholder.value, 0f).toString()
            }
        }

        for (placeholder in RulesManager.instance.allCustomStrategyPlaceholders){
            str.replaceIfExists(placeholder){
                lmEntity.customStrategyResults.getOrDefault(placeholder, 0f).toString()
            }
        }

        getAttributesCache(lmEntity, str)

        str.replaceIfExists("%distance-from-spawn%"){ lmEntity.distanceFromSpawn.toString() }
        str.replace("%hotspots-mod%", "0")
        str.replace("%barricades-mod%", "0")
        str.replaceIfExists("%creeper-blast-damage%"){
            val creeper = lmEntity.livingEntity as? Creeper
            return@replaceIfExists creeper?.explosionRadius?.toString() ?: "0"
        }

        for (placeholder in attributeStringList){
            str.replaceIfExists(placeholder.key){ lmEntity.attributeValuesCache?.get(placeholder.value)?.baseValue.toString() }
        }

        str.replaceIfExists("%item-drop%"){ "1" }
        str.replaceIfExists("%xp-drop%"){ "1" }

        if (!str.text.contains("%")) return str.text

        return replaceStringPlaceholders(
            str, lmEntity, true, lmEntity.associatedPlayer, true
        )
    }

    fun replaceStringPlaceholders(
        text: String,
        lmEntity: LivingEntityWrapper,
        usePAPI: Boolean,
        player: Player?,
        preserveMobName: Boolean
    ): String {
        return replaceStringPlaceholders(
            StringReplacer(text),
            lmEntity,
            usePAPI,
            player,
            preserveMobName
        )
    }

    @Suppress("DEPRECATION")
    private fun replaceStringPlaceholders(
        text: StringReplacer,
        lmEntity: LivingEntityWrapper,
        usePAPI: Boolean,
        player: Player?,
        preserveMobName: Boolean
    ): String {
        val maxHealth: Double = getMobAttributeValue(lmEntity)
        val entityHealth: Double = getMobHealth(lmEntity)
        val entityHealthRounded = if (entityHealth < 1.0 && entityHealth > 0.0) 1 else Utils.round(entityHealth).toInt()
        val roundedMaxHealth: String = Utils.round(maxHealth).toString()
        val roundedMaxHealthInt = (Utils.round(maxHealth).toInt()).toString()
        val percentHealthTemp = getEntityHealthRemaining(lmEntity, entityHealth, maxHealth)
        val percentHealth = if (percentHealthTemp < 1.0) 1 else percentHealthTemp.toInt()
        val playerId = player?.uniqueId?.toString() ?: ""
        val playerName = player?.name ?: ""
        val rm = LevelledMobs.instance.rulesManager

        var tieredPlaceholder = rm.getRuleTieredPlaceholder(lmEntity)
        if (tieredPlaceholder == null) tieredPlaceholder = ""

        // заменить эти плейсхолдеры ;)
        text.replaceIfExists("%displayname%") {
            val overridenName = if (lmEntity.lockedOverrideName == null)
                rm.getRuleEntityOverriddenName(lmEntity, false)
            else
                lmEntity.lockedOverrideName

            if (!overridenName.isNullOrEmpty() && lmEntity.livingEntity.customName == null)
                return@replaceIfExists overridenName

            if (lmEntity.livingEntity.customName != null)
                return@replaceIfExists if (LevelledMobs.instance.ver.isRunningPaper) "{CustomName}" else lmEntity.livingEntity.customName
            return@replaceIfExists if (preserveMobName)
                "{DisplayName}"
            else
                Utils.capitalize(lmEntity.typeName.replace("_", " "))
        }
        text.replace("%mob-lvl%", lmEntity.getMobLevel)
        text.replace(
            "%entity-name%",
            Utils.capitalize(lmEntity.typeName.replace("_", " "))
        )
        text.replace("%entity-name-raw%", lmEntity.typeName)
        text.replace("%entity-health%", Utils.round(entityHealth))
        text.replace("%entity-health-rounded%", entityHealthRounded)
        text.replaceIfExists("%entity-health-rounded-up%"){ ceil(entityHealth).toInt().toString() }
        text.replace("%entity-max-health%", roundedMaxHealth)
        text.replace("%entity-max-health-rounded%", roundedMaxHealthInt)
        text.replaceIfExists("%entity-max-health-rounded-up%"){ ceil(maxHealth).toInt().toString() }
        getHealthPercentRemaining(entityHealth, maxHealth, text)
        text.replaceIfExists("%base-health%"){
            val baseHealth = lmEntity.livingEntity.getAttribute(Utils.getAttribute(AttributeNames.MAX_HEALTH)!!)?.baseValue
            if (baseHealth != null) return@replaceIfExists baseHealth.toString()
            else return@replaceIfExists "0"
        }
        text.replace("%heart-symbol%", "❤")
        text.replace("%tiered%", tieredPlaceholder)
        text.replace("%wg-region%", lmEntity.wgRegionName)
        text.replace("%world%", lmEntity.worldName)
        text.replaceIfExists("%location%") {
                "${lmEntity.livingEntity.location.blockX} " +
                "${lmEntity.livingEntity.location.blockY} " +
                "${lmEntity.livingEntity.location.blockZ}"
        }
        text.replaceIfExists("%min-level%"){ rm.getRuleMobMinLevel(lmEntity).toString() }
        text.replaceIfExists("%max-level%"){ rm.getRuleMobMaxLevel(lmEntity).toString() }
        text.replace("%health%-percent%", percentHealth)
        text.replace("%x%", lmEntity.livingEntity.location.blockX)
        text.replace("%y%", lmEntity.livingEntity.location.blockY)
        text.replace("%z%", lmEntity.livingEntity.location.blockZ)
        text.replace("%player-uuid%", playerId)
        text.replace("%player%", playerName)
        if (text.contains("%rand_"))
            RandomVarianceGenerator.generateVariance(lmEntity, text)

        for (placeholder in ExternalCompatibilityManager.instance.externalPluginPlaceholders){
            text.replaceIfExists(placeholder.key){ placeholder.value.getPlaceholder(lmEntity) }
        }

        if (usePAPI && text.contains("%") && ExternalCompatibilityManager.hasPapiInstalled)
            text.text = ExternalCompatibilityManager.getPapiPlaceholder(player, text.text, lmEntity.invalidPlaceholderReplacement)

        return text.text
    }

    private fun getEntityHealthRemaining(
        lmEntity: LivingEntityWrapper,
        entityHealth: Double,
        maxHealth: Double
    ): Double{
        try{
            // столкнулись с проблемой, когда моб сообщает о здоровье «Бесконечности»
            // и вызовет исключение, обнаруженное ниже
            return (entityHealth / maxHealth * 100.0).roundToInt().toDouble()
        }
        catch (e: IllegalArgumentException){
            var entityName = lmEntity.nameIfBaby
            if (ExternalCompatibilityManager.hasMythicMobsInstalled
                && ExternalCompatibilityManager.isMythicMob(lmEntity)
            ) {
                entityName = ExternalCompatibilityManager.getMythicMobInternalName(lmEntity) +
                        " (${lmEntity.nameIfBaby})"
            }

            Log.sevKey("console.entity.health-error", mapOf(
                "error" to (e.message ?: "-"),
                "entity" to entityName,
                "location" to lmEntity.locationStr,
                "world" to lmEntity.worldName,
                "health" to entityHealth.toString(),
                "max-health" to maxHealth.toString()
            ))
            return 0.0
        }
    }

    private fun getHealthPercentRemaining(
        currentHealth: Double,
        maxHealth: Double,
        text: StringReplacer
    ){
        val start = text.text.indexOf("%entity-max-health-percent")
        if (start < 0) return

        val end = text.text.indexOf("%", start + 25)
        if (end < 0) return

        val percentHealth = (currentHealth.toFloat() / maxHealth.toFloat() * 100f)
        val fullText = text.text.substring(start, end)
        val optional = fullText.substring(fullText.length - 2)
        var digits = 2
        if (optional[0] == '-' && optional[1].isDigit())
            digits = optional[1].digitToInt()

        text.text = text.text.replace(fullText, (
            if (digits == 0) percentHealth.roundToInt().toString()
            else Utils.round(percentHealth.toDouble(), digits).toString()
        ))
    }

    fun updateNametagWithDelay(lmEntity: LivingEntityWrapper) {
        val scheduler = SchedulerWrapper(lmEntity.livingEntity) {
            updateNametag(lmEntity)
            lmEntity.free()
        }

        lmEntity.inUseCount.getAndIncrement()
        scheduler.runDelayed(1L)
    }

    fun updateNametag(lmEntity: LivingEntityWrapper) {
        val players = LevelledMobs.instance.nametagQueueManager.getTrackedPlayers(
            lmEntity.livingEntity.uniqueId
        )
        if (players.isEmpty()) return
        val nametag = getNametag(lmEntity, isDeathNametag = false, preserveMobName = true)

        val queueItem = QueueItem(
            lmEntity,
            nametag,
            players
        )

        LevelledMobs.instance.nametagQueueManager.addToQueue(queueItem)
    }

    fun updateNametag(
        lmEntity: LivingEntityWrapper,
        nametag: NametagResult,
        players: MutableList<Player>
    ) {
        LevelledMobs.instance.nametagQueueManager.addToQueue(QueueItem(lmEntity, nametag, players))
    }

    fun startEventDrivenNametagUpdates() {
        Log.infKey("console.lifecycle.starting-nametag-task")

        val main = LevelledMobs.instance
        this.doCheckMobHash = main.helperSettings.getBoolean("check-mob-hash", true)
        val cachePeriod = main.helperSettings.getIntTimeUnitMS(
            "lew-cache-clear-period", 180000L
        ) ?: 180000L
        cacheCleanupTask = SchedulerWrapper(::checkLEWCache)
            .runTaskTimerAsynchronously(cachePeriod, cachePeriod)
    }

    @Deprecated("Nametags are updated from entity tracking events")
    fun startNametagAutoUpdateTask() {
        startEventDrivenNametagUpdates()
    }

    private fun checkLEWCache() {
        if (lastLEWCacheClearing == null) {
            lastLEWCacheClearing = Instant.now()
            return
        }

        val duration = lastLEWCacheClearing!!.until(Instant.now(), ChronoUnit.MILLIS)
        val configDuration = LevelledMobs.instance.helperSettings.getIntTimeUnitMS(
             "lew-cache-clear-period", 180000L
        )!!

        if (duration >= configDuration) {
            DebugManager.log(DebugType.DEVELOPER_LEW_CACHE) {
                LocalizedMessages.text("command.levelledmobs.debug.runtime.d073", mapOf("value-1" to (configDuration)), false) + LivingEntityWrapper.getLEWDebug()
            }

            lastLEWCacheClearing = Instant.now()
            LivingEntityWrapper.clearCache()
        }
    }

    fun handleTrackedEntity(entity: Entity, player: Player) {
        val entityToPlayer = mutableMapOf<LivingEntityWrapper, MutableList<Player>>()
        checkEntity(entity, player, entityToPlayer)
        for ((lmEntity, players) in entityToPlayer) {
            checkEntityForPlayerLevelling(lmEntity, players)
            lmEntity.free()
        }
    }

    private fun checkEntity(
        entity: Entity,
        player: Player,
        entityToPlayer: MutableMap<LivingEntityWrapper, MutableList<Player>>
    ) {
        if (!entity.isValid) return  // асинхронная задача, объект может исчезнуть во время работы

        // Моб должен быть живым существом, которое... живое.
        if (entity !is LivingEntity || entity is Player || !entity.isValid)
            return
        var wrapperHasReference = false
        val lmEntity = LivingEntityWrapper.getInstance(entity)
        lmEntity.associatedPlayer = player
        if (doCheckMobHash && Utils.checkIfMobHashChanged(lmEntity)) {
            lmEntity.reEvaluateLevel = true
            lmEntity.isRulesForceAll = true
            lmEntity.wasPreviouslyLevelled = lmEntity.isLevelled
        }

        val main = LevelledMobs.instance
        if (lmEntity.isLevelled) {
            val internalSpawnReason = lmEntity.spawnReason.getInternalSpawnReason(lmEntity)
            var skipLevelling = (internalSpawnReason == InternalSpawnReason.LM_SPAWNER ||
                    internalSpawnReason == InternalSpawnReason.LM_SUMMON
                    )
            if (main.rulesManager.isPlayerLevellingEnabled() && !lmEntity.isRulesForceAll && !checkIfReadyForRelevelling(
                    lmEntity
                )
            ) {
                skipLevelling = true
            }
            if (main.rulesManager.isPlayerLevellingEnabled() && !skipLevelling) {
                val hasKey = entityToPlayer.containsKey(lmEntity)
                val players = if (hasKey) entityToPlayer[lmEntity]!! else mutableListOf()
                players.add(player)
                if (!hasKey)
                    entityToPlayer[lmEntity] = players

                wrapperHasReference = true
            }

            if (!lmEntity.isPopulated) {
                if (!wrapperHasReference) lmEntity.free()
                return
            }

            val nametagVisibilityEnums = lmEntity.nametagVisibilityEnum
            val nametagVisibleTime = lmEntity.getNametagCooldownTime()
            if (nametagVisibleTime > 0L &&
                nametagVisibilityEnums.contains(NametagVisibilityEnum.TARGETED) &&
                lmEntity.livingEntity.hasLineOfSight(player)
            ) {
                if (lmEntity.playersNeedingNametagCooldownUpdate == null) {
                    lmEntity.playersNeedingNametagCooldownUpdate = HashSet()
                }
                lmEntity.playersNeedingNametagCooldownUpdate!!.add(player)
            }

            checkLevelledEntity(lmEntity, player)
        } else {
            // Custom/spawn-egg mobs are deliberately processed after their spawn delay.
            if (entity.ticksLived < 30) {
                lmEntity.free()
                return
            }
            val wasBabyMob: Boolean
            synchronized(lmEntity.livingEntity.persistentDataContainer) {
                wasBabyMob = lmEntity.pdc
                    .has(NamespacedKeys.wasBabyMobKey, PersistentDataType.INTEGER)
            }
            if (lmEntity.isPopulated
            ) { // хак, позволяющий предотвратить нулевое исключение, о котором было сообщено
                val levellableState = main.levelInterface.getLevellableState(
                    lmEntity
                )
                if (!lmEntity.isBabyMob && wasBabyMob && levellableState == LevellableState.ALLOWED) {
                    // если моб в какой-то момент был ребенком, постарел и теперь имеет право на прокачку, мы присвоим ему уровень сейчас
                    DebugManager.log(DebugType.ENTITY_MISC, lmEntity) {
                        ("&b" + lmEntity.typeName
                                + LocalizedMessages.text("command.levelledmobs.debug.runtime.d074", colorize = false))
                    }

                    main.mobsQueueManager.addToQueue(QueueItem(lmEntity, null))
                }
                else if (levellableState == LevellableState.ALLOWED)
                    main.mobsQueueManager.addToQueue(QueueItem(lmEntity, null))
            }
        }

        if (!wrapperHasReference) lmEntity.free()
    }

    private fun checkIfReadyForRelevelling(lmEntity: LivingEntityWrapper): Boolean {
        val opts = LevelledMobs.instance.rulesManager.getRulePlayerLevellingOptions(lmEntity)
        if (opts?.preserveEntityTime == null) return true

        if (!lmEntity.pdc.has(NamespacedKeys.lastDamageTime, PersistentDataType.LONG))
            return true

        val lastLevelledTime: Long = lmEntity.pdc.get(NamespacedKeys.lastDamageTime, PersistentDataType.LONG)
            ?: return true

        val levelledTime = Instant.ofEpochMilli(lastLevelledTime)
        return Utils.getMillisecondsFromInstant(levelledTime) > opts.preserveEntityTime!!
    }

    private fun checkEntityForPlayerLevelling(
        lmEntity: LivingEntityWrapper,
        players: List<Player>
    ) {
        val mob = lmEntity.livingEntity
        var sortedPlayersSequence = players
            .asSequence()
            .filter { p: Player -> p.world == mob.world }
            .filter { p: Player -> p.gameMode != GameMode.SPECTATOR }
            .map { p: Player -> Pair(mob.location.distanceSquared(p.location), p) }
            .sortedBy { it.first }
            .map { it.second }

        if (MainCompanion.instance.excludePlayersInCreative){
            sortedPlayersSequence = sortedPlayersSequence.filter {
                p: Player -> p.gameMode != GameMode.CREATIVE
            }
        }

        val sortedPlayers = sortedPlayersSequence.toMutableList()
        var closestPlayer: Player? = null
        for (player in sortedPlayers) {
            if (ExternalCompatibilityManager.isMobOfCitizens(player))
                continue

            closestPlayer = player
            break
        }

        if (closestPlayer == null) return

        // если игрок вошел в систему менее 5 секунд, игнорируйте
        val logonTime =  MainCompanion.instance.getRecentlyJoinedPlayerLogonTime(closestPlayer)
        if (logonTime != null) {
            if (Utils.getMillisecondsFromInstant(logonTime) < 5000L)
                return

            MainCompanion.instance.removeRecentlyJoinedPlayer(closestPlayer)
        }

        if (doesMobNeedRelevelling(lmEntity, closestPlayer)) {
            lmEntity.pendingPlayerIdToSet = closestPlayer.uniqueId.toString()
            lmEntity.associatedPlayer = closestPlayer
            lmEntity.reEvaluateLevel = true
            LevelledMobs.instance.mobsQueueManager.addToQueue(QueueItem(lmEntity, null))
        }
    }

    private fun checkLevelledEntity(
        lmEntity: LivingEntityWrapper,
        player: Player
    ) {
        if (!lmEntity.livingEntity.isValid) return

        val main = LevelledMobs.instance

        @Suppress("DEPRECATION")
        if (lmEntity.isRulesForceAll) {
            main.mobsQueueManager.addToQueue(QueueItem(lmEntity, null))
        } else if (lmEntity.livingEntity.customName != null
            && main.rulesManager.getRuleMobCustomNameStatus(lmEntity)
            === MobCustomNameStatus.NOT_NAMETAGGED
        ) {
            // у моба есть бейдж, но он уровневый, поэтому мы его удалим
            main.levelInterface.removeLevel(lmEntity)
        } else if (lmEntity.isMobTamed
            && main.rulesManager.getRuleMobTamedStatus(lmEntity) === MobTamedStatus.NOT_TAMED
        ) {
            // моб приручен с помощью уровня, но правила этого не позволяют, удалите уровень
            main.levelInterface.removeLevel(lmEntity)
        } else if (lmEntity.livingEntity.isValid) {
            // PlayerTrackEntityEvent already guarantees that this viewer tracks the mob.
            val nametag = main.levelManager.getNametag(lmEntity, isDeathNametag = false, preserveMobName = true)
            main.nametagQueueManager.addToQueue(
                QueueItem(lmEntity, nametag, mutableListOf(player))
            )
        }
    }

    private fun doesMobNeedRelevelling(
        lmEntity: LivingEntityWrapper,
        player: Player
    ): Boolean {
        val mob = lmEntity.livingEntity
        val main = LevelledMobs.instance

        if (lmEntity.spawnReason.getInternalSpawnReason(lmEntity) == InternalSpawnReason.LM_SUMMON)
            return false

        val mobId = mob.uniqueId
        if (main.playerLevellingMinRelevelTime > 0L && main.playerLevellingEntities.containsKey(mobId)) {
            val lastCheck = main.playerLevellingEntities[mobId]
            val duration = Duration.between(lastCheck, Instant.now())

            if (duration.toMillis() < main.playerLevellingMinRelevelTime)
                return false
        }

        val playerId: String?
        if (main.playerLevellingMinRelevelTime > 0L)
            main.playerLevellingEntities[mobId] = Instant.now()

        synchronized(mob.persistentDataContainer) {
            if (!mob.persistentDataContainer
                    .has(
                        NamespacedKeys.playerLevellingId,
                        PersistentDataType.STRING
                    )
            ) {
                return true
            }
            playerId = mob.persistentDataContainer
                .get(
                    NamespacedKeys.playerLevellingId,
                    PersistentDataType.STRING
                )
        }

        if (playerId == null && main.playerLevellingMinRelevelTime <= 0L)
            return true
        else if (playerId == null || player.uniqueId.toString() != playerId)
            return true

        val opts = main.rulesManager.getRulePlayerLevellingOptions(lmEntity)
        if (player.uniqueId.toString() == playerId && opts != null && opts.getRecheckPlayers) {
            val previousResult: String =
                lmEntity.pdc.get(NamespacedKeys.playerLevellingSourceNumber, PersistentDataType.STRING)
                    ?: return true
            val variableToUse =
                if (opts.variable.isNullOrEmpty()) "%level%" else opts.variable!!
            val result = getPlayerLevelSourceNumber(player, lmEntity, variableToUse)
            val sourceNumberStr =
                if (result.isNumericResult) result.numericResult.toString() else result.stringResult

            return previousResult != sourceNumberStr
        }

        return player.uniqueId.toString() != playerId
    }

    fun stopNametagAutoUpdateTask() {
        LevelledMobs.instance.nametagQueueManager.stop()
        cacheCleanupTask?.cancelTask()

        if (!LevelledMobs.instance.nametagQueueManager.hasNametagSupport)
            return

    }

    private fun applyLevelledAttributes(
        lmEntity: LivingEntityWrapper,
        additions: MutableList<Addition>,
        nbtDatas: MutableList<String>
    ) {
        if (!lmEntity.isLevelled) return
        val modInfo = mutableListOf<AttributePreMod>()

        if (lmEntity.attributeValuesCache.isNullOrEmpty())
            MobDataManager.instance.getAllAttributeValues(lmEntity)

        for (addition in additions){
            val attribute = when (addition) {
                Addition.ATTRIBUTE_MAX_HEALTH -> Utils.getAttribute(AttributeNames.MAX_HEALTH)
                Addition.ATTRIBUTE_ATTACK_DAMAGE -> Utils.getAttribute(AttributeNames.ATTACK_DAMAGE)
                Addition.ATTRIBUTE_MOVEMENT_SPEED -> Utils.getAttribute(AttributeNames.MOVEMENT_SPEED)
                Addition.ATTRIBUTE_HORSE_JUMP_STRENGTH -> Utils.getAttribute(AttributeNames.JUMP_STRENGTH)
                Addition.ATTRIBUTE_ARMOR_BONUS -> Utils.getAttribute(AttributeNames.ARMOR)
                Addition.ATTRIBUTE_ARMOR_TOUGHNESS -> Utils.getAttribute(AttributeNames.ARMOR_TOUGHNESS)
                Addition.ATTRIBUTE_KNOCKBACK_RESISTANCE -> Utils.getAttribute(AttributeNames.KNOCKBACK_RESISTANCE)
                Addition.ATTRIBUTE_FLYING_SPEED -> Utils.getAttribute(AttributeNames.FLYING_SPEED)
                Addition.ATTRIBUTE_ATTACK_KNOCKBACK -> Utils.getAttribute(AttributeNames.ATTACK_KNOCKBACK)
                Addition.ATTRIBUTE_FOLLOW_RANGE -> Utils.getAttribute(AttributeNames.FOLLOW_RANGE)
                Addition.MOB_SCALE -> Utils.getAttribute(AttributeNames.MOB_SCALE)
                Addition.ATTRIBUTE_ZOMBIE_SPAWN_REINFORCEMENTS -> {
                    if (lmEntity.spawnReason.getMinecraftSpawnReason(lmEntity) == CreatureSpawnEvent.SpawnReason.REINFORCEMENTS)
                        null
                    else
                        Utils.getAttribute(AttributeNames.SPAWN_REINFORCEMENTS)
                }
                else -> throw IllegalStateException(
                    LocalizedMessages.text(
                        "console.internal.addition-must-be-attribute",
                        colorize = false
                    )
                )
            }

            if (attribute == null) continue

            val result = MobDataManager.instance.prepareSetAttributes(lmEntity, attribute, addition)
            if (result != null) modInfo.add(result)
        }

        val scheduler = SchedulerWrapper(lmEntity.livingEntity){
            MobDataManager.instance.setAttributeMods(lmEntity, modInfo)

            if (lmEntity.lockEntitySettings) {
                lmEntity.pdc
                    .set(NamespacedKeys.lockSettings, PersistentDataType.INTEGER, 1)
                if (lmEntity.lockedNametag != null) {
                    lmEntity.pdc
                        .set(
                            NamespacedKeys.lockedNametag, PersistentDataType.STRING,
                            lmEntity.lockedNametag!!
                        )
                }
                if (lmEntity.lockedOverrideName != null) {
                    lmEntity.pdc
                        .set(
                            NamespacedKeys.lockedNameOverride, PersistentDataType.STRING,
                            lmEntity.lockedOverrideName!!
                        )
                }
            }

            applyNbtData(lmEntity, nbtDatas)

            if (lmEntity.livingEntity is Creeper)
                lmEntity.main.levelManager.applyCreeperBlastRadius(lmEntity)

            lmEntity.free()
        }

        lmEntity.inUseCount.getAndIncrement()
        scheduler.runDirectlyInFolia = true
        scheduler.run()
    }

    private fun applyCreeperBlastRadius(lmEntity: LivingEntityWrapper) {
        val creeper = lmEntity.livingEntity as Creeper
        val main = LevelledMobs.instance

        val tuning = main.rulesManager.getFineTuningAttributes(lmEntity)
        if (tuning == null) {
            // убедитесь, что взрыв крипера происходит по умолчанию в ванильном случае на случай повторного уровня и т. д.
            if (creeper.explosionRadius != 3)
                creeper.explosionRadius = 3

            DebugManager.log(DebugType.CREEPER_BLAST_RADIUS, lmEntity) { LocalizedMessages.text("command.levelledmobs.debug.runtime.d075", colorize = false) }
            return
        }

        val maxRadius = main.rulesManager.getRuleCreeperMaxBlastRadius(lmEntity)
        val damage = main.mobDataManager.getAdditionsForLevel(
            lmEntity,
            Addition.CREEPER_BLAST_DAMAGE, 3f
        ).multiplierAmount

        if (damage == 0.0f) return

        var blastRadius = 3 + floor(damage).toInt()

        blastRadius = blastRadius
            .coerceAtLeast(0)
            .coerceAtMost(maxRadius)

        val blastRadiusFinal = blastRadius
        DebugManager.log(DebugType.CREEPER_BLAST_RADIUS, lmEntity) {
            LocalizedMessages.text("command.levelledmobs.debug.runtime.d076", mapOf("value-1" to (Utils.round(damage.toDouble(), 3)), "value-2" to (maxRadius), "value-3" to (blastRadiusFinal)), false)
        }

        creeper.explosionRadius = blastRadius
    }

    /**
     * Добавьте настроенное оборудование к уровневому мобу LivingEntity должна быть уровневым мобом
     *
     *
     * Потокобезопасность неизвестна.
     *
     * @param lmEntity уровневый моб, к которому можно применить уровневое снаряжение
     * @param level    уровень уровневого моба
     */
    private fun applyLevelledEquipment(
        lmEntity: LivingEntityWrapper,
        level: Int
    ) {
        if (!lmEntity.isLevelled) {
            // если вы вызываете моба, и ему не назначается уровень из-за правила конфигурации (например, зомби-детеныши освобождены)
            // тогда мы будем здесь с сущностью без уровня
            return
        }
        if (level < 1) return

        // Пользовательские Drops должны быть включены.
        val customDropsRuleSet: CustomDropsRuleSet = LevelledMobs.instance.rulesManager.getRuleUseCustomDropsForMob(lmEntity)
        if (!customDropsRuleSet.useDrops) return

        if (Bukkit.isOwnedByCurrentRegion(lmEntity.livingEntity)){
            applyLevelledEquipmentNonAsync(lmEntity, customDropsRuleSet)
            return
        }

        val scheduler = SchedulerWrapper {
            applyLevelledEquipmentNonAsync(lmEntity, customDropsRuleSet)
            lmEntity.free()
        }

        lmEntity.inUseCount.getAndIncrement()
        scheduler.entity = lmEntity.livingEntity
        scheduler.run()
    }

    private fun applyLevelledEquipmentNonAsync(
        lmEntity: LivingEntityWrapper,
        customDropsRuleSet: CustomDropsRuleSet
    ) {
        val mmInfo = MythicMobUtils.getMythicMobInfo(lmEntity)
        if (mmInfo != null && mmInfo.preventRandomEquipment)
            return

        val main = LevelledMobs.instance
        val items = mutableListOf<ItemStack>()
        val dropResult = main.customDropsHandler.getCustomItemDrops(
            lmEntity,
            items, true
        )
        if (items.isEmpty()) return

        val equipment = lmEntity.livingEntity.equipment ?: return

        if (lmEntity.lockEntitySettings && customDropsRuleSet.useDropTableIds.isNotEmpty()) {
            val customDrops = customDropsRuleSet.useDropTableIds.joinToString(";")
            lmEntity.pdc.set(NamespacedKeys.lockedDropRules, PersistentDataType.STRING, customDrops)
            if (customDropsRuleSet.chunkKillOptions!!.getDisableVanillaDrops()) lmEntity.pdc
                .set(NamespacedKeys.lockedDropRulesOverride, PersistentDataType.INTEGER, 1)
        }

        var hadMainItem = false
        var hadPlayerHead = false
        val equippedItemsInfo = EquippedItemsInfo()
        val equippedCountPerGroup = mutableMapOf<String?, Int>()
        var equippedSoFar = 0

        dropResult.stackToItem.shuffle()

        for ((itemStack, item) in dropResult.stackToItem) {
            val material = itemStack.type
            val groupLimits = main.customDropsHandler.getGroupLimits(item)
            val hasEquipLimits = item.hasGroupId && groupLimits != null && groupLimits.hasCapEquipped

            if (hasEquipLimits) {
                if (equippedCountPerGroup.containsKey(item.groupId))
                    equippedSoFar = equippedCountPerGroup[item.groupId]!!

                if (groupLimits.hasReachedCapEquipped(equippedSoFar)) {
                    DebugManager.log(DebugType.GROUP_LIMITS, lmEntity) {
                        LocalizedMessages.text("command.levelledmobs.debug.runtime.d077", mapOf("value-1" to (groupLimits.capEquipped), "value-2" to (material), "value-3" to (item.groupId)), false)
                    }
                    continue
                }
            }

            if (EnchantmentTarget.ARMOR_FEET.includes(material)) {
                equipment.setBoots(itemStack, true)
                equipment.bootsDropChance = 0f
                equippedItemsInfo.boots = item.itemStack
            } else if (EnchantmentTarget.ARMOR_LEGS.includes(material)) {
                equipment.setLeggings(itemStack, true)
                equipment.leggingsDropChance = 0f
                equippedItemsInfo.leggings = item.itemStack
            } else if (EnchantmentTarget.ARMOR_TORSO.includes(material)) {
                equipment.setChestplate(itemStack, true)
                equipment.chestplateDropChance = 0f
                equippedItemsInfo.chestplate = item.itemStack
            } else if (EnchantmentTarget.ARMOR_HEAD.includes(material)
                || material.name.endsWith("_HEAD") || (item.equipOnHelmet
                        && !hadPlayerHead)
            ) {
                equipment.setHelmet(itemStack, true)
                equipment.helmetDropChance = 0f
                equippedItemsInfo.helmet = item.itemStack
                if (material == Material.PLAYER_HEAD)
                    hadPlayerHead = true
            } else {
                if (!hadMainItem) {
                    equipment.setItemInMainHand(itemStack)
                    equipment.itemInMainHandDropChance = 0f
                    equippedItemsInfo.mainHand = item.itemStack
                    hadMainItem = true
                } else if (item.equipOffhand) {
                    equipment.setItemInOffHand(itemStack)
                    equipment.itemInOffHandDropChance = 0f
                    equippedItemsInfo.offhand = item.itemStack
                }
            }

            equippedSoFar++

            if (hasEquipLimits)
                equippedCountPerGroup[item.groupId] = equippedSoFar
        }

        equippedItemsInfo.saveEquipment(lmEntity)
    }

    private fun getMobAttributeValue(lmEntity: LivingEntityWrapper): Double {
        var result = 0.0
        synchronized(LevelledMobs.instance.attributeSyncObject) {
            val attrib = lmEntity.livingEntity
                .getAttribute(Utils.getAttribute(AttributeNames.MAX_HEALTH)!!)
            if (attrib != null)
                result = attrib.value
        }

        return result
    }

    private fun getMobHealth(lmEntity: LivingEntityWrapper): Double {
        val result: Double
        synchronized(LevelledMobs.instance.attributeSyncObject) {
            result = lmEntity.livingEntity.health
        }

        return result
    }

    override fun getLevellableState(
        lmInterface: LivingEntityInterface
    ): LevellableState {
        /*
        Некоторые типы сущностей принудительно запрещены независимо от пользовательских настроек.
        Та же проверка выполняется в getLevellableState(EntityType), но здесь она должна пройти
        до всех остальных проверок.
         */
        val main = LevelledMobs.instance
        if (forcedBlockedEntityTypes.contains(lmInterface.entityType))
            return LevellableState.DENIED_FORCE_BLOCKED_ENTITY_TYPE

        if (lmInterface.getApplicableRules().isEmpty())
            return LevellableState.DENIED_NO_APPLICABLE_RULES

        if (!main.rulesManager.getRuleIsMobAllowedInEntityOverride(lmInterface))
            return LevellableState.DENIED_CONFIGURATION_BLOCKED_ENTITY_TYPE

        if (main.rulesManager.getRuleMobMaxLevel(lmInterface) < 1)
            return LevellableState.DENIED_LEVEL_0

        if (lmInterface !is LivingEntityWrapper)
            return LevellableState.ALLOWED

        if (lmInterface.isMobOfExternalType) {
            lmInterface.invalidateCache()

            if (!main.rulesManager.getRuleIsMobAllowedInEntityOverride(lmInterface))
                return LevellableState.DENIED_CONFIGURATION_BLOCKED_ENTITY_TYPE
        }

        /*
        Проверка условий, запрещающих назначение уровня.
         */
        // Мобы с именами.
        @Suppress("DEPRECATION")
        if (lmInterface.livingEntity.customName != null &&
            main.rulesManager.getRuleMobCustomNameStatus(lmInterface)
            == MobCustomNameStatus.NOT_NAMETAGGED
        ) {
            return LevellableState.DENIED_CONFIGURATION_CONDITION_NAMETAGGED
        }

        return LevellableState.ALLOWED
    }

    override fun getLevellableState(livingEntity: LivingEntity): LevellableState {
        val lmEntity = LivingEntityWrapper.getInstance(livingEntity)
        try {
            return getLevellableState(lmEntity)
        }
        finally {
            lmEntity.free()
        }
    }

    /**
     * Этот метод применяет уровень к целевому мобу.
     *
     *
     * Метод можно вызвать независимо от того, имеет моб уровень или нет.
     *
     *
     * Этот метод НЕ проверяет, можно ли назначить сущности уровень. Предполагается, что плагины следят за этим.
     * случае (если они не намерены иначе).
     *
     *
     * Настоятельно рекомендуется оставить bypassLimits = false, если только желаемое поведение не
     * переопределить ограничения, заданные пользователем.
     *
     *
     * Потокобезопасность предусмотрена, но не проверена.
     *
     * @param lmEntity                   целевой моб
     * @param level                      уровень, который должен быть у моба
     * @param isSummoned                 если моб был создан LevelledMobs, а не сервером
     * @param bypassLimits               должен ли LM игнорировать максимальный уровень и т. д.
     * @param additionalLevelInformation используется для определения исходного события
     */
    override fun applyLevelToMob(
        lmEntity: LivingEntityWrapper,
        level: Int,
        isSummoned: Boolean,
        bypassLimits: Boolean,
        additionalLevelInformation: MutableSet<AdditionalLevelInformation>?
    ) {
        // этот поток работает асинхронно.  при добавлении каких-либо функций убедитесь, что их можно запустить таким образом
        val main = LevelledMobs.instance

        if (!Bukkit.isOwnedByCurrentRegion(lmEntity.livingEntity)){
            val scheduler = SchedulerWrapper(lmEntity.livingEntity) {
                applyLevelToMob(lmEntity, level, isSummoned, bypassLimits, additionalLevelInformation)
                lmEntity.free()
            }
            lmEntity.inUseCount.getAndIncrement()
            scheduler.run()
            return
        }

        var useLevel = level
        if (useLevel <= 0)
            useLevel = generateLevel(lmEntity)

        lmEntity.setMobPrelevel(useLevel)

        assert(bypassLimits || isSummoned || getLevellableState(lmEntity) == LevellableState.ALLOWED)
        var skipLMNametag = false

        if (lmEntity.livingEntity.isInsideVehicle
            && main.rulesManager.getRulePassengerMatchLevel(lmEntity)
            && lmEntity.livingEntity.vehicle is LivingEntity
        ) {
            // лицо является пассажиром. возьмите уровень объекта «транспортное средство»
            val vehicle = LivingEntityWrapper.getInstance(
                lmEntity.livingEntity.vehicle as LivingEntity
            )
            if (vehicle.isLevelled) {
                useLevel = vehicle.getMobLevel
                lmEntity.setMobPrelevel(useLevel)
            }

            vehicle.free()
        }

        if (isSummoned) {
            lmEntity.spawnReason.setInternalSpawnReason(lmEntity, InternalSpawnReason.LM_SUMMON, true)
            val summonedMobPreLevelEvent = SummonedMobPreLevelEvent(
                lmEntity.livingEntity, useLevel
            )
            Bukkit.getPluginManager().callEvent(summonedMobPreLevelEvent)

            if (summonedMobPreLevelEvent.isCancelled) return
        } else {
            val mobPreLevelEvent = MobPreLevelEvent(
                lmEntity.livingEntity, useLevel, MobPreLevelEvent.LevelCause.NORMAL,
                additionalLevelInformation
            )

            Bukkit.getPluginManager().callEvent(mobPreLevelEvent)
            if (mobPreLevelEvent.isCancelled) return

            useLevel = mobPreLevelEvent.level
            lmEntity.setMobPrelevel(useLevel)
            if (!mobPreLevelEvent.showLMNametag) {
                skipLMNametag = true
                lmEntity.shouldShowLMNametag = false
            }
        }

        var hasNoLevelKey = false
        if (!isSummoned) {
            synchronized(lmEntity.livingEntity.persistentDataContainer) {
                hasNoLevelKey = lmEntity.pdc
                    .has(NamespacedKeys.noLevelKey, PersistentDataType.STRING)
            }
        }

        if (hasNoLevelKey) {
            DebugManager.log(
                DebugType.APPLY_LEVEL_RESULT,
                lmEntity,
                false
            ) { LocalizedMessages.text("command.levelledmobs.debug.runtime.d078", colorize = false) }
            return
        }

        synchronized(lmEntity.livingEntity.persistentDataContainer) {
            lmEntity.pdc.set(NamespacedKeys.levelKey, PersistentDataType.INTEGER, useLevel)
            lmEntity.pdc.set(
                NamespacedKeys.mobHash,
                PersistentDataType.STRING,
                main.rulesManager.currentRulesHash
            )
        }
        lmEntity.invalidateCache()

        val nbtDatas =
            if (lmEntity.nbtData != null && lmEntity.nbtData!!.isNotEmpty())
                lmEntity.nbtData
            else
                main.rulesManager.getRuleNbtData(lmEntity)

        if (nbtDatas!!.isNotEmpty() && !ExternalCompatibilityManager.hasNbtApiInstalled) {
            if (!hasMentionedNBTAPIMissing) {
                Log.warKey(if (isSummoned)
                    "console.nbt.api-required-summon"
                else
                    "console.nbt.api-required-customdrops")
                hasMentionedNBTAPIMissing = true
            }
            nbtDatas.clear()
        }

        lmEntity.lockEntitySettings = main.rulesManager.getRuleDoLockEntity(lmEntity)
        if (lmEntity.lockEntitySettings && lmEntity.isNewlySpawned) {
            lmEntity.lockedNametag = main.rulesManager.getRuleNametag(lmEntity)
            lmEntity.lockedOverrideName = main.rulesManager.getRuleEntityOverriddenName(
                lmEntity,
                true
            )
        }

        val doSkipLMNametag = skipLMNametag

        try{
            applyLevelToMob2(lmEntity, nbtDatas, doSkipLMNametag)

            val levelCause =
                if (isSummoned) MobPostLevelEvent.LevelCause.SUMMONED
                else MobPostLevelEvent.LevelCause.NORMAL
            Bukkit.getPluginManager()
                .callEvent(MobPostLevelEvent(lmEntity, levelCause, additionalLevelInformation))

            val sb = StringBuilder().append(
                LocalizedMessages.text(
                    "command.levelledmobs.debug.runtime.d205",
                    mapOf("world" to lmEntity.worldName, "level" to useLevel),
                    false
                )
            )
            if (isSummoned)
                sb.append(LocalizedMessages.text(
                    "command.levelledmobs.debug.runtime.d206",
                    colorize = false
                ))
            if (bypassLimits)
                sb.append(LocalizedMessages.text("display.limit-bypass", colorize = false))

            DebugManager.log(DebugType.APPLY_LEVEL_RESULT, lmEntity, true, sb::toString)
        }
        catch (_: TimeoutException){
            DebugManager.log(DebugType.APPLY_LEVEL_RESULT, lmEntity, false){
                LocalizedMessages.text("command.levelledmobs.debug.runtime.d079", colorize = false)
            }
        }
    }

    private fun applyLevelToMob2(
        lmEntity: LivingEntityWrapper,
        nbtDatas: MutableList<String>,
        doSkipLMNametag: Boolean
    ) {
        applyAttribs(lmEntity, nbtDatas)

        if (!doSkipLMNametag)
            LevelledMobs.instance.levelManager.updateNametagWithDelay(lmEntity)
        
        LevelledMobs.instance.levelManager.applyLevelledEquipment(lmEntity, lmEntity.getMobLevel)
    }

    private fun applyAttribs(
        lmEntity: LivingEntityWrapper,
        nbtDatas: MutableList<String>
    ) {
        val main = LevelledMobs.instance
        val attribs = mutableListOf(
            Addition.ATTRIBUTE_ATTACK_DAMAGE,
            Addition.ATTRIBUTE_MAX_HEALTH,
            Addition.ATTRIBUTE_MOVEMENT_SPEED,
            Addition.ATTRIBUTE_ARMOR_BONUS,
            Addition.ATTRIBUTE_ARMOR_TOUGHNESS,
            Addition.ATTRIBUTE_ATTACK_KNOCKBACK,
            Addition.ATTRIBUTE_FLYING_SPEED,
            Addition.ATTRIBUTE_KNOCKBACK_RESISTANCE,
            Addition.ATTRIBUTE_FOLLOW_RANGE,
            Addition.MOB_SCALE
        )

        if (lmEntity.livingEntity is Zombie)
            attribs.add(Addition.ATTRIBUTE_ZOMBIE_SPAWN_REINFORCEMENTS)
        else if (main.ver.useNewHorseJumpAttrib && lmEntity.livingEntity is Horse)
            attribs.add(Addition.ATTRIBUTE_HORSE_JUMP_STRENGTH)

        main.levelManager.applyLevelledAttributes(lmEntity, attribs, nbtDatas)
    }

    private fun applyNbtData(
        lmEntity: LivingEntityWrapper,
        nbtDatas: MutableList<String>
    ){
        if (nbtDatas.isEmpty()) return
        var hadSuccess = false
        val allResults = mutableListOf<NBTApplyResult>()

        for (nbtData: String in nbtDatas) {
            val result: NBTApplyResult = NBTManager.applyNBTDataMob(
                lmEntity,
                nbtData
            )
            if (result.hadException) {
                if (lmEntity.summonedSender == null) {
                    Log.warKey("console.nbt.apply-error", mapOf(
                        "nbt" to nbtData,
                        "entity" to lmEntity.nameIfBaby,
                        "error" to (result.exceptionMessage ?: "-")
                    ))
                } else {
                    LocalizedMessages.send(lmEntity.summonedSender!!, "command.levelledmobs.summon.nbt-error", mapOf(
                        "entity" to lmEntity.nameIfBaby,
                        "error" to (result.exceptionMessage ?: "-")
                    ))
                }
            } else {
                hadSuccess = true
                allResults.add(result)
            }
        }

        if (hadSuccess) {
            DebugManager.log(DebugType.NBT_APPLICATION, lmEntity, true) {
                LocalizedMessages.text("command.levelledmobs.debug.runtime.d080", mapOf("value-1" to (MiscUtils.getNBTDebugMessage(allResults))), false)
            }
        }
    }

    /**
     * Проверьте, является ли LivingEntity уровневым мобом или нет. Это определяется *после*
     * MobPreLevelEvent.
     *
     *
     * Потокобезопасность предусмотрена, но не проверена.
     *
     * @param livingEntity живое существо для проверки
     * @return есть ли у моба уровень
     */
    override fun isLevelled(livingEntity: LivingEntity): Boolean {
        return try {
            synchronized(livingEntity.persistentDataContainer) {
                livingEntity.persistentDataContainer
                    .has(NamespacedKeys.levelKey, PersistentDataType.INTEGER)
            }
        } catch (_: ConcurrentModificationException) {
            Log.warKey("console.concurrency.level-check-failed")
            false
        }
    }

    /**
     * Получите уровень уровневого моба.
     *
     *
     * Потокобезопасность предусмотрена, но не проверена.
     *
     * @param livingEntity уровневый моб, чтобы получить уровень
     * @return уровень моба
     */
    override fun getLevelOfMob(livingEntity: LivingEntity): Int {
        synchronized(livingEntity.persistentDataContainer) {
            if (!livingEntity.persistentDataContainer
                    .has(NamespacedKeys.levelKey, PersistentDataType.INTEGER)
            ) {
                return -1
            }
            return livingEntity.persistentDataContainer
                    .get(NamespacedKeys.levelKey, PersistentDataType.INTEGER)!!
        }
    }

    /**
     * Снять уровень моба.
     *
     * @param lmEntity уровневый моб до снятия уровня
     */
    override fun removeLevel(lmEntity: LivingEntityWrapper) {
        assert(lmEntity.isLevelled)
        // удалить значение PDC
        val main = LevelledMobs.instance
        synchronized(lmEntity.livingEntity.persistentDataContainer) {
            if (lmEntity.pdc.has(NamespacedKeys.levelKey, PersistentDataType.INTEGER))
                lmEntity.pdc.remove(NamespacedKeys.levelKey)

            if (lmEntity.pdc
                .has(NamespacedKeys.overridenEntityNameKey, PersistentDataType.STRING)
            ) {
                lmEntity.pdc.remove(NamespacedKeys.overridenEntityNameKey)
            }
        }

        // сбросить атрибуты
        synchronized(main.attributeSyncObject) {
            for (attributeName in AttributeNames.entries) {
                val attribute = Utils.getAttribute(attributeName) ?: continue
                val attInst = lmEntity.livingEntity.getAttribute(attribute) ?: continue

                val existingMods =
                    Collections.enumeration(attInst.modifiers)
                while (existingMods.hasMoreElements()) {
                    val existingMod = existingMods.nextElement()

                    if (main.mobDataManager.vanillaMultiplierNames.containsKey(existingMod.name)) continue
                    attInst.removeModifier(existingMod)
                }
            }
        }

        if (lmEntity.livingEntity is Creeper)
            (lmEntity.livingEntity as Creeper).explosionRadius = 3

        lmEntity.invalidateCache()

        // обновить бейдж
        main.levelManager.updateNametag(lmEntity)
    }

    override fun removeLevel(livingEntity: LivingEntity) {
        val lmEntity = LivingEntityWrapper.getInstance(livingEntity)
        removeLevel(lmEntity)
        lmEntity.free()
    }

    override fun getMobNametag(livingEntity: LivingEntity): String? {
        val lmEntity = LivingEntityWrapper.getInstance(livingEntity)
        try {
            return getNametag(lmEntity, false).nametag
        }
        finally {
            lmEntity.free()
        }
    }
}
