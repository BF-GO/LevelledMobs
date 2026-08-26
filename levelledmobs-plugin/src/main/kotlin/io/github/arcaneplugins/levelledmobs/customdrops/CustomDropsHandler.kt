package io.github.arcaneplugins.levelledmobs.customdrops

import io.github.arcaneplugins.levelledmobs.util.LocalizedMessages
import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.debug.DebugManager
import io.github.arcaneplugins.levelledmobs.managers.ExternalCompatibilityManager
import io.github.arcaneplugins.levelledmobs.enums.Addition
import io.github.arcaneplugins.levelledmobs.debug.DebugType
import io.github.arcaneplugins.levelledmobs.enums.DropInstanceBuildResult
import io.github.arcaneplugins.levelledmobs.misc.NamespacedKeys
import io.github.arcaneplugins.levelledmobs.result.PlayerLevelSourceResult
import io.github.arcaneplugins.levelledmobs.managers.MobDataManager
import io.github.arcaneplugins.levelledmobs.managers.NotifyManager
import io.github.arcaneplugins.levelledmobs.result.EvaluationResult
import io.github.arcaneplugins.levelledmobs.util.Log
import io.github.arcaneplugins.levelledmobs.util.MessageUtils.colorizeAll
import io.github.arcaneplugins.levelledmobs.util.PaperUtils
import io.github.arcaneplugins.levelledmobs.util.SpigotUtils
import io.github.arcaneplugins.levelledmobs.util.Utils
import io.github.arcaneplugins.levelledmobs.wrappers.LivingEntityWrapper
import io.github.arcaneplugins.levelledmobs.wrappers.SchedulerWrapper
import io.papermc.paper.datacomponent.DataComponentTypes
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.ThreadLocalRandom
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.inventory.EntityEquipment
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import org.bukkit.persistence.PersistentDataType

/**
 * Основной CustomDropsclass, содержащий полезные функции для анализа, создания экземпляров и многого другого.
 * пользовательские выпадающие предметы
 *
 * @author stumper66
 * @since 2.4.0
 */
class CustomDropsHandler {
    // обычные пользовательские дропы, определенные для типа моба
    private val customDropsitems = ConcurrentHashMap<EntityType, CustomDropInstance>()
    // обычные специальные дропы, определенные для типа моба, который является ребенком
    val customDropsitemsBabies = ConcurrentHashMap<EntityType, CustomDropInstance>()

    // используется только для встроенных универсальных групп
    private val customDropsitemsGroups = ConcurrentHashMap<String, CustomDropInstance>()

    // это сбросы, определенные таблицей сбросов
    val customDropIDs: MutableMap<String, CustomDropInstance> =
        ConcurrentSkipListMap(String.CASE_INSENSITIVE_ORDER)

    // сопоставления groupIds для удаления экземпляра
    private val groupIdToInstance: MutableMap<String, CustomDropInstance> =
        ConcurrentSkipListMap(String.CASE_INSENSITIVE_ORDER)

    // получить экземпляр отбрасывания из его идентификатора группы
    var customItemGroups: MutableMap<String, CustomDropInstance> = ConcurrentHashMap()

    // сопоставление groupid и grouplimits
    val groupLimitsMap: MutableMap<String, GroupLimits> =
        ConcurrentSkipListMap(String.CASE_INSENSITIVE_ORDER)
    val customDropsParser = CustomDropsParser(this)
    val externalCustomDrops: ExternalCustomDrops = ExternalCustomDropsImpl()
    var lmItemsParser: LMItemsParser? = null
        private set

    fun load(){
        if (ExternalCompatibilityManager.instance.doesLMIMeetVersionRequirement())
            this.lmItemsParser = LMItemsParser()
    }

    fun clearDrops(){
        customDropsitems.clear()
        customDropsitemsBabies.clear()
        customDropsitemsGroups.clear()
        customDropIDs.clear()
        groupIdToInstance.clear()
        customItemGroups.clear()
        groupLimitsMap.clear()
    }

    companion object{
        fun evaluateNumberFormula(
            numberFormula: String?,
            friendlyName: String,
            lmEntity: LivingEntityWrapper
        ): EvaluationResult {
            if (numberFormula.isNullOrEmpty()) return EvaluationResult(1.0, null)

            val formula = LevelledMobs.instance.levelManager.replaceStringPlaceholdersForFormulas(numberFormula, lmEntity)
            val evalResult = MobDataManager.evaluateExpression(formula)
            if (evalResult.hadError){
                NotifyManager.notifyOfErrorKey("console.formula.customdrop-error", mapOf(
                    "name" to friendlyName,
                    "entity" to lmEntity.nameIfBaby,
                    "level" to lmEntity.getMobLevel,
                    "error" to (evalResult.error ?: "-")
                ))
                DebugManager.log(DebugType.CUSTOM_DROPS_FORMULA, lmEntity){
                    val msg = if (formula == numberFormula)
                        LocalizedMessages.text("command.levelledmobs.debug.runtime.d002", mapOf("value-1" to (formula)), false)
                    else
                        LocalizedMessages.text("command.levelledmobs.debug.runtime.d003", mapOf("value-1" to (numberFormula)), false) +
                        LocalizedMessages.text("command.levelledmobs.debug.runtime.d004", mapOf("value-1" to (formula)), false)

                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d005", mapOf("value-1" to (evalResult.error), "value-2" to (msg)), false)}
            }

            DebugManager.log(DebugType.CUSTOM_DROPS_FORMULA, lmEntity){
                val msg = if (formula == numberFormula)
                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d006", mapOf("value-1" to (formula)), false)
                else
                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d007", mapOf("value-1" to (numberFormula)), false) +
                            LocalizedMessages.text("command.levelledmobs.debug.runtime.d008", mapOf("value-1" to (formula)), false)

                LocalizedMessages.text("command.levelledmobs.debug.runtime.d009", mapOf("value-1" to (friendlyName), "value-2" to (evalResult.result), "value-3" to (msg)), false)}

            return evalResult
        }
    }

    fun getCustomDropsitems(): MutableMap<EntityType, CustomDropInstance> {
        val drops = mutableMapOf<EntityType, CustomDropInstance>()
        drops.putAll(this.customDropsitems)

        for (entityType in externalCustomDrops.getCustomDrops().keys) {
            val dropInstance = externalCustomDrops.getCustomDrops()[entityType]!!
            if (!drops.containsKey(entityType)) {
                drops[entityType] = dropInstance
                continue
            }

            // объединить сторонний дроп с определенными для объекта
            // Сторонние настройки перетаскивания будут иметь приоритет над любыми конфликтующими
            val currentDropInstance = drops[entityType]
            currentDropInstance!!.combineDrop(dropInstance)

            if (dropInstance.overallChance != null) currentDropInstance.overallChance = dropInstance.overallChance
            currentDropInstance.overallPermissions.addAll(dropInstance.overallPermissions)
        }

        return drops
    }

    fun getCustomDropsitemsGroups(): MutableMap<String, CustomDropInstance> {
        val drops = mutableMapOf<String, CustomDropInstance>()
        drops.putAll(this.customItemGroups)
        drops.putAll(this.customDropsitemsGroups)

        for (groupName in externalCustomDrops.getCustomDropTables().keys) {
            val dropInstance: CustomDropInstance = externalCustomDrops.getCustomDropTables()[groupName]!!
            if (!drops.containsKey(groupName)) {
                drops[groupName] = dropInstance
                continue
            }

            // объединить сторонний дроп с определенными для объекта
            // Сторонние настройки перетаскивания будут иметь приоритет над любыми конфликтующими
            val currentDropInstance = drops[groupName]
            currentDropInstance!!.combineDrop(dropInstance)

            if (dropInstance.overallChance != null) currentDropInstance.overallChance = dropInstance.overallChance
            currentDropInstance.overallPermissions.addAll(dropInstance.overallPermissions)
        }

        return drops
    }

    fun addCustomDropItem(entityType: EntityType, customDropInstance: CustomDropInstance) {
        customDropsitems[entityType] = customDropInstance
    }

    fun addCustomDropGroup(groupName: String, customDropInstance: CustomDropInstance) {
        customDropsitemsGroups[groupName] = customDropInstance
    }

    fun getCustomItemDrops(
        lmEntity: LivingEntityWrapper,
        drops: MutableList<ItemStack>,
        equippedOnly: Boolean
    ): CustomDropResult {
        val processingInfo = CustomDropProcessingInfo()
        processingInfo.lmEntity = lmEntity
        processingInfo.equippedOnly = equippedOnly
        processingInfo.newDrops = drops
        processingInfo.equippedItemsInfo = EquippedItemsInfo.getEntityEquippedItems(lmEntity)

        val main = LevelledMobs.instance
        processingInfo.dropRules = main.rulesManager.getRuleUseCustomDropsForMob(lmEntity)
        synchronized(lmEntity.livingEntity.persistentDataContainer) {
            processingInfo.isSpawner = (lmEntity.pdc
                .has(NamespacedKeys.spawnReasonKey, PersistentDataType.STRING) &&
                    CreatureSpawnEvent.SpawnReason.SPAWNER.toString() == lmEntity.pdc
                        .get(NamespacedKeys.spawnReasonKey, PersistentDataType.STRING)
                    )
            if (lmEntity.pdc
                    .has(NamespacedKeys.keySpawnerCustomDropId, PersistentDataType.STRING)
            ) {
                processingInfo.customDropId = lmEntity.pdc
                    .get(NamespacedKeys.keySpawnerCustomDropId, PersistentDataType.STRING)
                processingInfo.hasCustomDropId = !processingInfo.customDropId.isNullOrEmpty()
            }
        }

        if (lmEntity.associatedPlayer != null) {
            processingInfo.wasKilledByPlayer = true
            processingInfo.mobKiller = lmEntity.associatedPlayer
        } else
            processingInfo.wasKilledByPlayer = false

        if (lmEntity.livingEntity.lastDamageCause != null)
            processingInfo.deathCause = lmEntity.livingEntity.lastDamageCause!!.cause.toString()

        if (!processingInfo.equippedOnly){
            processingInfo.addition =
                main.mobDataManager.getAdditionsForLevel(lmEntity, Addition.CUSTOM_ITEM_DROP, 2F).multiplierAmount
        }

        processingInfo.doNotMultiplyDrops = main.rulesManager.getRuleCheckIfNoDropMultiplierEntitiy(
            lmEntity
        )

        if (lmEntity.livingEntity.lastDamageCause != null) {
            val damageCause = lmEntity.livingEntity.lastDamageCause!!.cause
            processingInfo.deathByFire =
                (damageCause == EntityDamageEvent.DamageCause.FIRE ||
                        damageCause == EntityDamageEvent.DamageCause.FIRE_TICK ||
                        damageCause == EntityDamageEvent.DamageCause.LAVA)
        }

        val groupsList = mutableListOf<String>()
        for (group in lmEntity.getApplicableGroups()) {
            if (!getCustomDropsitemsGroups().containsKey(group))
                continue

            groupsList.add(group)
        }

        val buildResult = buildDropsListFromGroupsAndEntity(groupsList, processingInfo)
        if (buildResult != DropInstanceBuildResult.SUCCESSFUL) {
            // не имел общего шанса
            if (buildResult == DropInstanceBuildResult.DID_NOT_MAKE_CHANCE) {
                processingInfo.addDebugMessage(
                    DebugType.CUSTOM_DROPS,
                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d155", mapOf("value-1" to (processingInfo.overallChanceDebugMessage)), false)
                )
            }
            else {
                val mobKiller = if (processingInfo.mobKiller == null) "(null)" else processingInfo.mobKiller!!.name
                processingInfo.addDebugMessage(
                    DebugType.CUSTOM_DROPS,
                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d156", mapOf("value-1" to (mobKiller)), false)
                )
            }
            processingInfo.writeAnyDebugMessages()

            return CustomDropResult(processingInfo.stackToItem, processingInfo.hasOverride)
        }

        if (!equippedOnly) {
            processingInfo.addDebugMessage(
                DebugType.CUSTOM_DROPS,
                LocalizedMessages.text("command.levelledmobs.debug.runtime.d157", mapOf("value-1" to (processingInfo.overallChanceDebugMessage)), false)
            )

            DebugManager.log(DebugType.MOB_GROUPS, lmEntity){
                LocalizedMessages.text("command.levelledmobs.debug.runtime.d010", colorize = false) + lmEntity.getApplicableGroups().joinToString(LocalizedMessages.text("command.levelledmobs.debug.runtime.d011", colorize = false)) + "&7."
            }
        }

        getCustomItemsFromDropInstance(processingInfo) // полезная нагрузка

        val postCount = drops.size
        val showCustomEquips = main.debugManager.isDebugTypeEnabled(DebugType.CUSTOM_EQUIPS)
        val showCustomDrops = main.debugManager.isDebugTypeEnabled(DebugType.CUSTOM_DROPS)

        if (showCustomDrops || showCustomEquips) {
            if (equippedOnly && drops.isNotEmpty() && showCustomEquips) {
                if (lmEntity.getMobLevel > -1) {
                    processingInfo.addDebugMessage(
                        LocalizedMessages.text("command.levelledmobs.debug.runtime.d158", mapOf("value-1" to (lmEntity.typeName), "value-2" to (lmEntity.getMobLevel)), false)
                    )
                }
                else
                    processingInfo.addDebugMessage(LocalizedMessages.text("command.levelledmobs.debug.runtime.d159", mapOf("value-1" to (lmEntity.typeName)), false))

                val sb = StringBuilder()
                for (drop in drops) {
                    if (sb.isNotEmpty())sb.append(", ")

                    sb.append(drop.type.name)
                }
                processingInfo.addDebugMessage(LocalizedMessages.text("command.levelledmobs.debug.runtime.d160", mapOf("value-1" to (sb)), false))
            }
            else if (!equippedOnly && showCustomDrops) {
                processingInfo.addDebugMessage(
                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d161", mapOf("value-1" to (postCount)), false)
                )
            }

            processingInfo.writeAnyDebugMessages()
        }

        return CustomDropResult(processingInfo.stackToItem, processingInfo.hasOverride)
    }

    private fun buildDropsListFromGroupsAndEntity(
        groups: MutableList<String>,
        info: CustomDropProcessingInfo
    ): DropInstanceBuildResult {
        info.prioritizedDrops = mutableMapOf()
        info.hasOverride = false
        var usesGroupIds = false
        val alreadyProcessedIds = mutableSetOf<String>()

        val overrideNonDropTableDrops =
            info.dropRules != null && info.dropRules!!.chunkKillOptions!!.getDisableVanillaDrops()

        for (id in getDropIds(info)) {
            if (!customItemGroups.containsKey(id.trim())) {
                Log.warKey("console.customdrops.invalid-droptable-id", mapOf("id" to id))
                continue
            }

            alreadyProcessedIds.add(id)
            val dropInstance = customItemGroups[id.trim()]
            info.allDropInstances.add(dropInstance!!)

            for (baseItem in dropInstance.customItems) {
                processDropPriorities(baseItem, info)
            }

            if (dropInstance.utilizesGroupIds)
                usesGroupIds = true
            if (dropInstance.getOverrideStockDrops)
                info.hasOverride = true
        }

        if (!overrideNonDropTableDrops) {
            for (group in groups) {
                if (alreadyProcessedIds.contains(group)) continue

                val dropInstance = getCustomDropsitemsGroups()[group]
                info.allDropInstances.add(dropInstance!!)

                for (baseItem in dropInstance.customItems) {
                    processDropPriorities(baseItem, info)
                }

                if (dropInstance.utilizesGroupIds)
                    usesGroupIds = true
                if (dropInstance.getOverrideStockDrops)
                    info.hasOverride = true
            }

            val entityType = info.lmEntity!!.entityType
            val dropMap: Map<EntityType, CustomDropInstance> =
                if (info.lmEntity!!.isBabyMob && customDropsitemsBabies.containsKey(entityType)) customDropsitemsBabies else getCustomDropsitems()

            if (dropMap.containsKey(entityType)) {
                val dropInstance = dropMap[entityType]
                info.allDropInstances.add(dropInstance!!)

                for (baseItem in dropInstance.customItems) {
                    processDropPriorities(baseItem, info)
                }

                if (dropInstance.utilizesGroupIds)
                    usesGroupIds = true
                if (dropInstance.getOverrideStockDrops)
                    info.hasOverride = true
            }
        }

        if (usesGroupIds) {
            for (customDropBases in info.prioritizedDrops!!.values) {
                customDropBases.shuffle()
            }
        }

        if (!checkOverallPermissions(info))
            return DropInstanceBuildResult.PERMISSION_DENIED

        if (info.equippedOnly && !info.hasEquippedItems)
            return DropInstanceBuildResult.SUCCESSFUL

        return if (checkOverallChance(info)) DropInstanceBuildResult.SUCCESSFUL else DropInstanceBuildResult.DID_NOT_MAKE_CHANCE
    }

    private fun checkOverallPermissions(info: CustomDropProcessingInfo): Boolean {
        var hadAnyPerms = false
        for (dropInstance in info.allDropInstances) {
            if (dropInstance.overallPermissions.isEmpty())
                continue

            hadAnyPerms = true
            for (perm in dropInstance.overallPermissions) {
                if (info.mobKiller == null)continue

                val checkPerm = "LevelledMobs.permission.$perm"
                if (info.mobKiller!!.hasPermission(checkPerm))
                    return true
            }
        }

        return !hadAnyPerms
    }

    private fun getDropIds(
        processingInfo: CustomDropProcessingInfo
    ): MutableList<String> {
        val dropIds = mutableListOf<String>()
        if (processingInfo.dropRules != null) {
            for (id in processingInfo.dropRules!!.useDropTableIds) {
                dropIds.addAll(id.split(","))
            }
        }

        if (processingInfo.hasCustomDropId && !dropIds.contains(processingInfo.customDropId))
            dropIds.add(processingInfo.customDropId!!)

        return dropIds
    }

    private fun processDropPriorities(
        baseItem: CustomDropBase,
        processingInfo: CustomDropProcessingInfo
    ) {
        val priority = -baseItem.priority
        if (processingInfo.prioritizedDrops!!.containsKey(priority)) {
            processingInfo.prioritizedDrops!![priority]!!.add(baseItem)
        }
        else {
            val items = mutableListOf<CustomDropBase>()
            items.add(baseItem)
            processingInfo.prioritizedDrops!![priority] = items
        }

        if (baseItem is CustomDropItem
            && baseItem.equippedChance != null && !baseItem.equippedChance!!.isDefault
        )
            processingInfo.hasEquippedItems = true
    }

    private fun getCustomItemsFromDropInstance(
        info: CustomDropProcessingInfo
    ) {
        val dropLimitsReached = mutableListOf<UUID>()
        val defaultLimits = groupLimitsMap.getOrDefault("default", null)

        for (items in info.prioritizedDrops!!.values) {
            // циклически перебирать каждый выпадающий список, связанный с любыми идентификаторами групп

            val retriesHardcodedMax = 10
            var maxRetries = 1

            var i = 0
            while (i < maxRetries) {
                info.retryNumber = i

                for (drop in items) {
                    // перебрать все узлы в этом идентификаторе группы

                    if (drop.hasGroupId) {
                        info.dropInstance = groupIdToInstance[drop.groupId]
                        info.groupLimits = groupLimitsMap.getOrDefault(drop.groupId, defaultLimits)
                        maxRetries =
                            info.groupLimits?.retries?.coerceAtMost(retriesHardcodedMax) ?: 0
                    } else {
                        info.dropInstance = null
                        info.groupLimits = null
                    }

                    if (info.groupLimits != null) {
                        val groupLimits = info.groupLimits!!
                        val itemDroppedCount = info.getItemsDropsById(drop)
                        if (groupLimits.hasReachedCapPerItem(itemDroppedCount)) {
                            if (!dropLimitsReached.contains(drop.uid)) {
                                dropLimitsReached.add(drop.uid)
                                val itemDescription =
                                    if ((drop is CustomDropItem)) drop.material.name else "CustomCommand"
                                DebugManager.log(DebugType.GROUP_LIMITS, info.lmEntity) {
                                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d012", mapOf("value-1" to (groupLimits.capPerItem), "value-2" to (itemDescription)), false)
                                }
                            }
                            continue
                        }

                        val groupDroppedCount = info.getDropItemsCountForGroup(drop)
                        if (groupLimits.hasReachedCapTotal(groupDroppedCount)) {
                            DebugManager.log(DebugType.GROUP_LIMITS, info.lmEntity) {
                                LocalizedMessages.text("command.levelledmobs.debug.runtime.d013", mapOf("value-1" to (groupLimits.capTotal), "value-2" to (drop.groupId)), false)
                            }
                            return
                        }
                    }

                    // полезная нагрузка:
                    getDropsFromCustomDropItem(info, drop)
                }

                i++
            }
        } // следующая группа
    }

    private fun getDropsFromCustomDropItem(
        info: CustomDropProcessingInfo,
        dropBase: CustomDropBase
    ) {
        if (dropBase is CustomCommand && info.lmEntity!!.livingEntity
                .hasMetadata("noCommands") ||
            info.lmEntity!!.deathCause == EntityDamageEvent.DamageCause.VOID
        ){
            return
        }

        if (info.equippedOnly && dropBase is CustomCommand&& !dropBase.runOnSpawn)
            return
        if (!info.equippedOnly && dropBase.playerCausedOnly && (dropBase.causeOfDeathReqs == null
                    || dropBase.causeOfDeathReqs!!.isEmpty()) && !info.wasKilledByPlayer
        ){
            if (!info.equippedOnly && LevelledMobs.instance.debugManager.isDebugTypeEnabled(DebugType.CUSTOM_DROPS)) {
                val itemName = if (dropBase is CustomDropItem) dropBase.material.name else
                    LocalizedMessages.text("display.custom-command", colorize = false)
                info.addDebugMessage(
                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d162", mapOf("value-1" to (itemName), "value-2" to (info.deathCause)), false) +
                            LocalizedMessages.text("command.levelledmobs.debug.runtime.d163", colorize = false)
                )
            }
            return
        }

        if (dropBase.noSpawner && info.isSpawner) return
        if (shouldDenyDeathCause(dropBase, info)) return
        if (!madePlayerLevelRequirement(info, dropBase)) return

        if (dropBase.excludedMobs.contains(info.lmEntity!!.typeName)) {
            if (dropBase is CustomDropItem && !info.equippedOnly)
                info.addDebugMessage(LocalizedMessages.text("command.levelledmobs.debug.runtime.d164", mapOf("value-1" to (dropBase.material.name)), false))
            return
        }

        val main = LevelledMobs.instance
        var doDrop = dropBase.maxLevel <= -1 || info.lmEntity!!.getMobLevel <= dropBase.maxLevel
        if (dropBase.minLevel > -1 && info.lmEntity!!.getMobLevel < dropBase.minLevel)
            doDrop = false

        if (!doDrop) {
            if (dropBase is CustomDropItem) {
                if (!info.equippedOnly && main.debugManager.isDebugTypeEnabled(DebugType.CUSTOM_DROPS)) {
                    val itemStack: ItemStack =
                        if (info.deathByFire) getCookedVariantOfMeat(dropBase.itemStack!!)
                        else dropBase.itemStack!!

                    info.addDebugMessage(
                        LocalizedMessages.text("command.levelledmobs.debug.runtime.d165", mapOf("value-1" to (info.isSpawner), "value-2" to (itemStack.type.name)), false) +
                                LocalizedMessages.text("command.levelledmobs.debug.runtime.d166", mapOf("value-1" to (dropBase.minLevel), "value-2" to (dropBase.maxLevel), "value-3" to (dropBase.noSpawner)), false)
                    )
                }
            } else if (dropBase is CustomCommand) {
                info.addDebugMessage(
                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d167", mapOf("value-1" to (info.isSpawner), "value-2" to (dropBase.minLevel)), false) +
                            LocalizedMessages.text("command.levelledmobs.debug.runtime.d168", mapOf("value-1" to (dropBase.maxLevel), "value-2" to (dropBase.noSpawner)), false)
                )
            }
            return
        }

        if (!info.equippedOnly && dropBase is CustomDropItem)
            info.itemWasEquipped = isMobWearingOrHoldingItem(info, dropBase)

        // equip-chance и equip-drop-chance:
        if (!info.equippedOnly && dropBase is CustomDropItem) {
            if (!checkIfMadeEquippedDropChance(info, dropBase)) {
                if (main.debugManager.isDebugTypeEnabled(DebugType.CUSTOM_DROPS)) {
                    info.addDebugMessage(
                        LocalizedMessages.text("command.levelledmobs.debug.runtime.d169", mapOf("value-1" to (dropBase.itemStack?.type?.name)), false),
                    )
                }
                return
            }
        }

        if (!info.equippedOnly && !checkDropPermissions(info, dropBase))
            return

        val runOnSpawn = dropBase is CustomCommand && dropBase.runOnSpawn
        var didNotMakeChance = false
        var chanceRole = 0.0f

        if (!info.equippedOnly && dropBase.useChunkKillMax && info.wasKilledByPlayer
            && hasReachedChunkKillLimit(info.lmEntity!!)
        ) {
            if (dropBase is CustomDropItem) {
                info.addDebugMessage(
                    DebugType.CUSTOM_DROPS,
                        LocalizedMessages.text("command.levelledmobs.debug.runtime.d170", mapOf("value-1" to (dropBase.material.name), "value-2" to (dropBase.groupId)), false)
                )
            } else {
                info.addDebugMessage(
                    DebugType.CUSTOM_DROPS,
                        LocalizedMessages.text("command.levelledmobs.debug.runtime.d171", mapOf("value-1" to (dropBase.groupId)), false)
                )
            }

            return
        }

        var dropChance =
            if (dropBase.chance != null) dropBase.chance!!.getSlidingChance("chance-formula", info.lmEntity!!) else 0.0f
        dropChance = main.mobArchetypeManager.getArtifactChance(
            info.lmEntity!!.livingEntity, dropBase.groupId, dropChance
        )
        if ((!info.equippedOnly || runOnSpawn) && dropChance < 1.0f) {
            chanceRole =
                if (dropChance > 0.0f) ThreadLocalRandom.current().nextInt(0, 100001).toFloat() * 0.00001f else 0.0f
            if (1.0f - chanceRole >= dropChance)
                didNotMakeChance = true
        }

        if (didNotMakeChance && (!info.equippedOnly || runOnSpawn) && main.debugManager.isDebugTypeEnabled(DebugType.CUSTOM_DROPS)) {
            if (dropBase is CustomDropItem) {
                val itemStack: ItemStack =
                    if (info.deathByFire) getCookedVariantOfMeat(dropBase.itemStack!!)
                    else dropBase.itemStack!!

                info.addDebugMessage(
                    DebugType.CUSTOM_DROPS,
                        LocalizedMessages.text("command.levelledmobs.debug.runtime.d172", mapOf("value-1" to (itemStack.type.name), "value-2" to (dropBase.amountAsString), "value-3" to (dropBase.chance?.showMatchedChance())), false) +
                                LocalizedMessages.text("command.levelledmobs.debug.runtime.d173", mapOf("value-1" to (Utils.round(chanceRole.toDouble(), 4))), false)
                )
            } else {
                info.addDebugMessage(
                    DebugType.CUSTOM_DROPS,
                        LocalizedMessages.text("command.levelledmobs.debug.runtime.d174", mapOf("value-1" to (dropBase.chance?.showMatchedChance())), false) +
                                LocalizedMessages.text("command.levelledmobs.debug.runtime.d175", mapOf("value-1" to (Utils.round(chanceRole.toDouble(), 4))), false)
                )
            }
        }
        if ((!info.equippedOnly || runOnSpawn) && didNotMakeChance)
            return

        var maxDropGroup = 0
        if (info.groupLimits != null && info.groupLimits!!.hasCapSelect)
            maxDropGroup = info.groupLimits!!.capSelect.coerceAtLeast(0)
        else if (info.groupLimits == null)
            maxDropGroup = dropBase.maxDropGroup

        if (!info.equippedOnly && dropBase.hasGroupId) {
            // устаревший раздел, выполняется только в том случае, если использовалась старая «maxdropgroup».
            // вместо «group-limits.capSelect»
            val groupDroppedCount = info.getItemsDropsByGroup(dropBase)

            if (maxDropGroup in 1..groupDroppedCount
                || info.groupLimits == null && maxDropGroup == 0 && groupDroppedCount > 0
            ) {
                if (main.debugManager.isDebugTypeEnabled(DebugType.CUSTOM_DROPS)) {
                    if (dropBase is CustomDropItem) {
                        info.addDebugMessage(
                            LocalizedMessages.text("command.levelledmobs.debug.runtime.d176", mapOf("value-1" to (dropBase.material.name), "value-2" to (dropBase.groupId)), false) +
                                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d177", mapOf("value-1" to (info.getItemsDropsByGroup(dropBase)), "value-2" to (groupDroppedCount)), false)
                        )
                    } else {
                        info.addDebugMessage(
                            LocalizedMessages.text("command.levelledmobs.debug.runtime.d178", mapOf("value-1" to (info.getItemsDropsByGroup(dropBase))), false) +
                                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d179", mapOf("value-1" to (dropBase.maxDropGroup), "value-2" to (groupDroppedCount)), false)
                        )
                    }
                }
                return
            }
        }

        if (dropBase is CustomCommand) {
            // ----------------------------------------- Здесь команды выполняются, а затем функция возвращает ----------------------------------------------------
            var count = 0
            if (dropBase.hasGroupId && info.groupLimits != null) {
                count = info.getItemsDropsByGroup(dropBase)
                val groupLimits = info.groupLimits!!
                if (groupLimits.hasReachedCapTotal(count)) {
                    DebugManager.log(DebugType.GROUP_LIMITS, info.lmEntity) {
                        LocalizedMessages.text("command.levelledmobs.debug.runtime.d014", mapOf("value-1" to (groupLimits.capTotal), "value-2" to (dropBase.groupId)), false)
                    }
                    return
                }
            }

            executeCommand(dropBase, info)
            info.itemGotDropped(dropBase, 1)

            if (dropBase.hasGroupId) {
                if (main.debugManager.isDebugTypeEnabled(DebugType.CUSTOM_DROPS)) {
                    var msg = LocalizedMessages.text(
                        "command.levelledmobs.debug.runtime.d200",
                        mapOf(
                            "group" to dropBase.groupId,
                            "max-group" to dropBase.maxDropGroup,
                            "count" to count
                        ),
                        false
                    )
                    if (info.retryNumber > 0)
                        msg += LocalizedMessages.text(
                            "command.levelledmobs.debug.runtime.d202",
                            mapOf("retry" to info.retryNumber),
                            false
                        )

                    info.addDebugMessage(msg)
                }
            } else if (main.debugManager.isDebugTypeEnabled(DebugType.CUSTOM_DROPS)) {
                var msg = LocalizedMessages.text(
                    "command.levelledmobs.debug.runtime.d201",
                    mapOf(
                        "group" to dropBase.groupId,
                        "max-group" to dropBase.maxDropGroup
                    ),
                    false
                )

                if (info.retryNumber > 0)
                    msg += LocalizedMessages.text(
                        "command.levelledmobs.debug.runtime.d202",
                        mapOf("retry" to info.retryNumber),
                        false
                    )

                info.addDebugMessage(msg)
            }

            return
            // -----------------------------------------------------------------------------------------------------------------------------------------------
        }
        if (dropBase !is CustomDropItem) {
            Log.warKey("console.customdrops.unsupported-drop-type", mapOf("type" to dropBase.javaClass.name))
            return
        }

        if (!checkEquippedChances(info, dropBase)) return

        var newDropAmount = dropBase.amount
        var newDropAmountD: Double? = null

        if (dropBase.hasAmountRange) {
            val change = ThreadLocalRandom.current()
                .nextInt(0, dropBase.amountRangeMax - dropBase.amountRangeMin + 1)
            newDropAmount = dropBase.amountRangeMin + change
        }

        if (!dropBase.amountFormula.isNullOrEmpty()) {
            newDropAmountD =
                evaluateNumberFormula(
                    dropBase.amountFormula,
                    "amount-formula",
                    info.lmEntity!!
                ).result

            info.formulaResult = newDropAmountD
            newDropAmount = newDropAmountD.toInt()
        }

        if (dropBase.hasGroupId && info.groupLimits != null) {
            val gl = info.groupLimits!!

            if (gl.hasCapPerItem)
                newDropAmount = newDropAmount.coerceAtMost(gl.capPerItem)

            if (gl.hasCapTotal && dropBase.hasGroupId) {
                val hasDroppedSoFar = info.getDropItemsCountForGroup(dropBase)
                if (gl.capTotal - hasDroppedSoFar > gl.capTotal)
                    newDropAmount = gl.capTotal
            }
        }

        // если мы зайдём так далеко, то предмет выпадет
        if (dropBase.isExternalItem &&
            !main.mainCompanion.externalCompatibilityManager.doesLMIMeetVersionRequirement()
        )
            Log.warKey("console.customdrops.lm-items-missing")

        if (dropBase.isExternalItem && main.mainCompanion.externalCompatibilityManager.doesLMIMeetVersionRequirement())
            lmItemsParser!!.getExternalItem(dropBase, info)

        if (dropBase.itemStacks == null) return

        for (newItemPre in dropBase.itemStacks!!) {
            // будет только несколько элементов для поддерживаемых элементов LM Items.

            var newItem = newItemPre.clone()

            processEnchantmentChances(dropBase, newItem, info)

            if (info.deathByFire)
                newItem = getCookedVariantOfMeat(dropBase.itemStack!!)

            if (newDropAmount > 1 && !dropBase.isExternalItem) newItem.amount = newDropAmount

            if (!dropBase.noMultiplier && !info.doNotMultiplyDrops) {
                main.levelManager.multiplyDrop(info.lmEntity!!, newItem, info.addition)
                newDropAmount = newItem.amount
            }
            else if (newDropAmount > newItem.maxStackSize)
                newDropAmount = newItem.maxStackSize

            if (!dropBase.isExternalItem && newItem.amount != newDropAmount)
                newItem.amount = newDropAmount

            if (info.equippedOnly && main.debugManager.isDebugTypeEnabled(DebugType.CUSTOM_EQUIPS)) {
                val equippedChance =
                    if (dropBase.equippedChance != null) dropBase.equippedChance!!.showMatchedChance() else "0.0"
                info.addDebugMessage(DebugType.CUSTOM_EQUIPS,
                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d180", mapOf("value-1" to (newItem.type.name), "value-2" to (equippedChance)), false) +
                            LocalizedMessages.text("command.levelledmobs.debug.runtime.d181", mapOf("value-1" to (Utils.round(info.equippedChanceRole.toDouble(), 4))), false)
                )
            } else if (!info.equippedOnly && main.debugManager.isDebugTypeEnabled(DebugType.CUSTOM_DROPS)) {
                val retryMsg = if (info.retryNumber > 0) LocalizedMessages.text(
                    "command.levelledmobs.debug.runtime.d202",
                    mapOf("retry" to info.retryNumber),
                    false
                ) else ""
                var amountMsg = ""
                if (newDropAmountD != null) amountMsg = newDropAmountD.toString()
                else if (dropBase.externalAmount != null && dropBase.externalAmount!! > 0.0) dropBase.externalAmount.toString()
                else dropBase.amount.toString()

                info.addDebugMessage(
                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d182", mapOf("value-1" to (newItem.type.name), "value-2" to (dropBase.amountAsString), "value-3" to (amountMsg)), false) +
                            LocalizedMessages.text("command.levelledmobs.debug.runtime.d183", mapOf("value-1" to (dropBase.chance?.showMatchedChance())), false) +
                            LocalizedMessages.text("command.levelledmobs.debug.runtime.d184", mapOf("value-1" to (Utils.round(chanceRole.toDouble(), 4)), "value-2" to (retryMsg)), false)
                )
            }

            var damage: Int = dropBase.damage
            if (dropBase.hasDamageRange) {
                damage = ThreadLocalRandom.current()
                    .nextInt(dropBase.damageRangeMin, dropBase.damageRangeMax + 1)
            }

            if (damage > 0 || dropBase.lore != null || dropBase.customName != null) {
                val meta = newItem.itemMeta

                if (damage > 0 && meta is Damageable)
                    meta.damage = damage

                if (meta != null && dropBase.lore != null && dropBase.lore!!.isNotEmpty()) {
                    val newLore: MutableList<String> = ArrayList(dropBase.lore!!.size)

                    for (lorePre in dropBase.lore!!) {
                        var lore = lorePre
                        if (lore.contains("%")) {
                            lore = lore.replace("%player%", if (info.mobKiller == null) "" else info.mobKiller!!.name)
                            lore = main.levelManager.replaceStringPlaceholders(
                                lore,
                                info.lmEntity!!,
                                true,
                                info.mobKiller,
                                false
                            )
                        }

                        newLore.add(lore)

                        if (main.ver.isRunningPaper && main.mainCompanion.useAdventure)
                            PaperUtils.updateItemMetaLore(meta, newLore)
                        else
                            SpigotUtils.updateItemMetaLore(meta, newLore)
                    }
                }

                if (meta != null && dropBase.customName != null && dropBase.customName!!.isNotEmpty()) {
                    var customName = dropBase.customName!!.replace(
                        "%player%",
                        if (info.mobKiller == null) "" else info.mobKiller!!.name
                    )
                    customName = main.levelManager.replaceStringPlaceholders(
                        customName,
                        info.lmEntity!!,
                        true,
                        info.mobKiller,
                        false
                    )

                    if (main.ver.isRunningPaper && main.mainCompanion.useAdventure)
                        PaperUtils.updateItemDisplayName(meta, customName)
                    else
                        SpigotUtils.updateItemDisplayName(meta, colorizeAll(customName))
                }

                newItem.setItemMeta(meta)
            }

            if (!info.equippedOnly) info.itemGotDropped(dropBase, newDropAmount)

            info.newDrops!!.add(newItem)
            info.stackToItem.add(Utils.getPair(newItem, dropBase))
        }
    }

    private fun checkEquippedChances(
        info: CustomDropProcessingInfo,
        dropItem: CustomDropItem
    ): Boolean {
        if (!info.equippedOnly) return true
        val equippedChance = if (dropItem.equippedChance != null) dropItem.equippedChance!!.getSlidingChance(
            "equipped-formula", info.lmEntity!!
        ) else 0.0f
        if (equippedChance >= 1.0f) return true

        info.equippedChanceRole =
            if (equippedChance > 0.0f) ThreadLocalRandom.current().nextInt(0, 100001).toFloat() * 0.00001f else 0.0f

        if (equippedChance <= 0.0f || 1.0f - info.equippedChanceRole >= equippedChance) {
            if (LevelledMobs.instance.debugManager.isDebugTypeEnabled(DebugType.CUSTOM_EQUIPS)) {
                info.addDebugMessage(DebugType.CUSTOM_EQUIPS,
                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d185", mapOf("value-1" to (dropItem.material.name)), false) +
                            LocalizedMessages.text("command.levelledmobs.debug.runtime.d186", mapOf("value-1" to (dropItem.equippedChance?.showMatchedChance())), false) +
                            LocalizedMessages.text("command.levelledmobs.debug.runtime.d187", mapOf("value-1" to (Utils.round(info.equippedChanceRole.toDouble(), 4))), false)
                )
            }
            return false
        }

        return true
    }

    private fun checkOverallChance(info: CustomDropProcessingInfo): Boolean {
        for (dropInstance in info.allDropInstances) {
            if (dropInstance.overallChance == null || dropInstance.overallChance!!.isDefault ||
                dropInstance.overallChance!!.isAssuredChance
            ) {
                continue
            }

            synchronized(info.lmEntity!!.livingEntity.persistentDataContainer) {
                if (info.lmEntity!!.pdc
                        .has(NamespacedKeys.overallChanceKey, PersistentDataType.INTEGER)
                ) {
                    val value = info.lmEntity!!.pdc
                            .get(NamespacedKeys.overallChanceKey, PersistentDataType.INTEGER)

                    return value == 1
                }
            }

            // мы бросим кости, чтобы увидеть, получим ли мы вообще какой-либо дроп, и сохраним результат в PDC
            val chanceRole =
                ThreadLocalRandom.current().nextInt(0, 100001).toFloat() * 0.00001f
            val effectiveChance = dropInstance.overallChance!!.getSlidingChance(
                "overall-chance-formula",
                info.lmEntity!!
            )

            val madeChance = (1f - chanceRole) < effectiveChance
            info.overallChanceDebugMessage = " " + LocalizedMessages.text(
                "display.customdrops.chance-minimum",
                mapOf(
                    "minimum" to Utils.round(effectiveChance.toDouble(), 4),
                    "role" to Utils.round(chanceRole.toDouble(), 4)
                ),
                false
            )
            if (info.equippedOnly) {
                synchronized(info.lmEntity!!.livingEntity.persistentDataContainer) {
                    info.lmEntity!!.pdc
                        .set(
                            NamespacedKeys.overallChanceKey, PersistentDataType.INTEGER,
                            if (madeChance) 1 else 0
                        )
                }
            }

            return madeChance
        }

        return true
    }

    private fun processEnchantmentChances(
        dropItem: CustomDropItem,
        itemStack: ItemStack,
        info: CustomDropProcessingInfo
    ) {
        if (dropItem.enchantmentChances == null || dropItem.enchantmentChances!!.isEmpty) return

        val chances = dropItem.enchantmentChances!!
        val debugId = DebugManager.startLongDebugMessage()
        DebugManager.logLongMessage(debugId){ LocalizedMessages.text("command.levelledmobs.debug.runtime.d015", mapOf("value-1" to (itemStack.type.name)), false) }
        var isFirstEnchantment = true

        for (enchantment in chances.items.keys) {
            val opts = chances.options[enchantment]
            var madeAnyChance = false
            if (!isFirstEnchantment) DebugManager.logLongMessage(debugId){ "; " }
            DebugManager.logLongMessage(debugId){ LocalizedMessages.text("command.levelledmobs.debug.runtime.d016", mapOf("value-1" to (enchantment.key.value())), false) }

            if (isFirstEnchantment) isFirstEnchantment = false
            var enchantmentNumber = 0
            val levelsList = mutableListOf<Int>()
            levelsList.addAll(chances.items[enchantment]!!.keys)
            if (opts == null || opts.doShuffle) levelsList.shuffle()

            for (enchantLevel in levelsList) {
                val chanceValue = chances.items[enchantment]!![enchantLevel]!!
                if (chanceValue <= 0.0f) continue
                enchantmentNumber++

                val chanceRole =
                    ThreadLocalRandom.current().nextInt(0, 100001).toFloat() * 0.00001f
                val madeChance = 1.0f - chanceRole < chanceValue
                if (!madeChance) {
                    if (enchantmentNumber > 1) DebugManager.logLongMessage(debugId){ ", " }
                    DebugManager.logLongMessage(debugId){
                        LocalizedMessages.text("command.levelledmobs.debug.runtime.d017", mapOf("value-1" to (enchantLevel), "value-2" to (chanceRole), "value-3" to (chanceValue)), false)
                    }
                    continue
                }

                if (enchantmentNumber > 1) DebugManager.logLongMessage(debugId){ ", " }
                DebugManager.logLongMessage(debugId){
                    val msg = if (chanceValue >= 1f) LocalizedMessages.text("command.levelledmobs.debug.runtime.d018", mapOf("value-1" to (chanceValue)), false) else LocalizedMessages.text("command.levelledmobs.debug.runtime.d019", mapOf("value-1" to (chanceRole), "value-2" to (chanceValue)), false)
                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d020", mapOf("value-1" to (enchantLevel), "value-2" to (msg)), false)
                }

                if (itemStack.type == Material.ENCHANTED_BOOK) {
                    val meta = itemStack.itemMeta as EnchantmentStorageMeta
                    meta.addStoredEnchant(enchantment, enchantLevel, true)
                    itemStack.setItemMeta(meta)
                }
                else
                    itemStack.addUnsafeEnchantment(enchantment, enchantLevel)

                madeAnyChance = true
                break
            }

            if (!madeAnyChance && opts != null && opts.defaultLevel != null && opts.defaultLevel!! > 0) {
                if (itemStack.type == Material.ENCHANTED_BOOK) {
                    val meta = itemStack.itemMeta as EnchantmentStorageMeta
                    meta.addStoredEnchant(enchantment, opts.defaultLevel!!, true)
                    itemStack.setItemMeta(meta)
                }
                else
                    itemStack.addUnsafeEnchantment(enchantment, opts.defaultLevel!!)

                DebugManager.logLongMessage(debugId){ LocalizedMessages.text("command.levelledmobs.debug.runtime.d021", mapOf("value-1" to (opts.defaultLevel)), false) }
            }
        }

        DebugManager.endLongMessage(debugId, DebugType.ENCHANTMENT_CHANCES, info.lmEntity)
    }

    private fun hasReachedChunkKillLimit(lmEntity: LivingEntityWrapper): Boolean {
        val maximumDeathInChunkThreshold: Int = LevelledMobs.instance.rulesManager.getMaximumDeathInChunkThreshold(
            lmEntity
        )
        if (maximumDeathInChunkThreshold <= 0) return false

        return lmEntity.chunkKillcount >= maximumDeathInChunkThreshold
    }

    private fun shouldDenyDeathCause(
        dropBase: CustomDropBase,
        info: CustomDropProcessingInfo
    ): Boolean {
        if (dropBase.causeOfDeathReqs == null || info.deathCause == null)
            return false

        if (info.wasKilledByPlayer && dropBase.causeOfDeathReqs!!.isIncludedInList(
                "PLAYER_CAUSED",
                info.lmEntity
            )
        ) {
            return false
        }

        if (!Utils.isDamageCauseInModalList(dropBase.causeOfDeathReqs!!, info.deathCause!!)) {
            if (!info.equippedOnly && LevelledMobs.instance.debugManager.isDebugTypeEnabled(DebugType.CUSTOM_DROPS)) {
                val itemName = if (dropBase is CustomDropItem) dropBase.material.name else
                    LocalizedMessages.text("display.custom-command", colorize = false)
                info.addDebugMessage(
                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d188", mapOf("value-1" to (itemName), "value-2" to (info.deathCause)), false) +
                            LocalizedMessages.text("command.levelledmobs.debug.runtime.d189", mapOf("value-1" to (dropBase.causeOfDeathReqs)), false)
                )
            }

            return true
        }

        return false
    }

    private fun checkDropPermissions(
        info: CustomDropProcessingInfo,
        dropBase: CustomDropBase
    ): Boolean {
        if (info.equippedOnly || dropBase.permissions.isEmpty())
            return true

        val main = LevelledMobs.instance
        if (info.mobKiller == null) {
            if (main.debugManager.isDebugTypeEnabled(DebugType.CUSTOM_DROPS)) {
                val itemDescription = if ((dropBase is CustomDropItem)) dropBase.itemStack?.type?.name
                    else LocalizedMessages.text("display.custom-command", colorize = false)
                info.addDebugMessage(
                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d190", mapOf("value-1" to (itemDescription)), false)
                )
            }
            return false
        }

        var hadPermission = false
        for (perm in dropBase.permissions) {
            val permCheck = "levelledmobs.permission.$perm"
            if (info.mobKiller!!.hasPermission(permCheck)) {
                hadPermission = true
                break
            }
        }

        if (!hadPermission) {
            if (main.debugManager.isDebugTypeEnabled(DebugType.CUSTOM_DROPS)) {
                val msg = if ((dropBase is CustomDropItem)) dropBase.itemStack?.type?.name
                else LocalizedMessages.text("display.custom-command", colorize = false)
                info.addDebugMessage(
                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d191", mapOf("value-1" to (msg), "value-2" to (info.mobKiller?.name), "value-3" to (dropBase.permissions)), false)
                )
            }
            return false
        }

        return true
    }

    private fun checkIfMadeEquippedDropChance(
        info: CustomDropProcessingInfo,
        item: CustomDropItem
    ): Boolean {
        if (!item.onlyDropIfEquipped) return true
        if (!info.itemWasEquipped) return false

        if (item.equippedChance != null && item.equippedChance!!.isAssuredChance
            || !item.onlyDropIfEquipped
        ) {
            return true
        }

        return info.itemWasEquipped
    }

    @Suppress("UnstableApiUsage")
    private fun isMobWearingOrHoldingItem(
        info: CustomDropProcessingInfo,
        customDropItem: CustomDropItem
    ): Boolean {
        val item = customDropItem.itemStack ?: return false

        if (info.lmEntity == null) return false
        val equipment = info.lmEntity!!.livingEntity.equipment ?: return false

        if (!LevelledMobs.instance.ver.isRunningPaper) return isMobWearingItemSpigot(
                item, info, customDropItem, equipment
        )

        if (customDropItem.equipOnHelmet && item.isSimilar(equipment.helmet))
            return true

        val equippable = item.getData(DataComponentTypes.EQUIPPABLE)

        if (equippable != null) {
            if (equippable.slot() == EquipmentSlot.HEAD) {
                if (item.isSimilar(info.equippedItemsInfo?.helmet)) return true
                return item.isSimilar(equipment.helmet)
            }

            if (equippable.slot() == EquipmentSlot.CHEST) {
                if (item.isSimilar(info.equippedItemsInfo?.chestplate)) return true
                return item.isSimilar(equipment.chestplate)
            }

            if (equippable.slot() == EquipmentSlot.LEGS) {
                if (item.isSimilar(info.equippedItemsInfo?.leggings)) return true
                return item.isSimilar(equipment.leggings)
            }

            if (equippable.slot() == EquipmentSlot.FEET) {
                if (item.isSimilar(info.equippedItemsInfo?.boots)) return true
                return item.isSimilar(equipment.boots)
            }

            if (equippable.slot() == EquipmentSlot.HAND){
                if (item.isSimilar(info.equippedItemsInfo?.mainHand)) return true
                return item.isSimilar(equipment.itemInMainHand)
            }

            if (equippable.slot() == EquipmentSlot.OFF_HAND){
                if (item.isSimilar(info.equippedItemsInfo?.offhand)) return true
                return item.isSimilar(equipment.itemInOffHand)
            }
        }
        else{
            // Сюда помещаются неэкипируемые предметы.
            if (item.isSimilar(equipment.itemInMainHand)) return true
            return item.isSimilar(equipment.itemInOffHand)
        }

        return false
    }

    @Suppress("removal")
    private fun isMobWearingItemSpigot(
        item: ItemStack,
        info: CustomDropProcessingInfo,
        customDropItem: CustomDropItem,
        equipment: EntityEquipment
    ) : Boolean{
        if (customDropItem.equipOnHelmet && item.isSimilar(equipment.helmet))
            return true

        if (org.bukkit.enchantments.EnchantmentTarget.ARMOR_HEAD.includes(item.type)){
            if (item.isSimilar(info.equippedItemsInfo?.helmet)) return true
            return item.isSimilar(equipment.helmet)
        }
        if (org.bukkit.enchantments.EnchantmentTarget.ARMOR_TORSO.includes(item.type)){
            if (item.isSimilar(info.equippedItemsInfo?.chestplate)) return true
            return item.isSimilar(equipment.chestplate)
        }
        if (org.bukkit.enchantments.EnchantmentTarget.ARMOR_LEGS.includes(item.type)){
            if (item.isSimilar(info.equippedItemsInfo?.leggings)) return true
            return item.isSimilar(equipment.leggings)
        }
        if (org.bukkit.enchantments.EnchantmentTarget.ARMOR_FEET.includes(item.type)){
            if (item.isSimilar(info.equippedItemsInfo?.boots)) return true
            return item.isSimilar(equipment.boots)
        }

        if (item.isSimilar(info.equippedItemsInfo?.mainHand)) return true
        if (item.isSimilar(equipment.itemInMainHand)) return true
        if (item.isSimilar(info.equippedItemsInfo?.offhand)) return true

        return item.isSimilar(equipment.itemInOffHand)
    }

    private fun madePlayerLevelRequirement(
        info: CustomDropProcessingInfo,
        dropBase: CustomDropBase
    ): Boolean {
        val main = LevelledMobs.instance
        if (dropBase.playerLevelVariable != null && !info.equippedOnly && dropBase.playeerVariableMatches.isNotEmpty()) {
            val papiResult = Utils.removeColorCodes(
                ExternalCompatibilityManager.getPapiPlaceholder(
                    info.mobKiller, dropBase.playerLevelVariable!!, info.lmEntity?.invalidPlaceholderReplacement
                )
            )

            var foundMatch = false
            for (resultStr in dropBase.playeerVariableMatches) {
                if (Utils.matchWildcardString(papiResult, resultStr)) {
                    foundMatch = true
                    if (main.debugManager.isDebugTypeEnabled(DebugType.CUSTOM_DROPS)) {
                        if (dropBase is CustomDropItem) {
                            info.addDebugMessage(
                                LocalizedMessages.text("command.levelledmobs.debug.runtime.d192", mapOf("value-1" to (dropBase.material), "value-2" to (papiResult), "value-3" to (resultStr)), false)
                            )
                        } else {
                            info.addDebugMessage(
                                LocalizedMessages.text("command.levelledmobs.debug.runtime.d193", mapOf("value-1" to (resultStr)), false)
                            )
                        }
                    }
                    break
                }
            }

            if (!foundMatch) {
                if (main.debugManager.isDebugTypeEnabled(DebugType.CUSTOM_DROPS)) {
                    if (dropBase is CustomDropItem) {
                        info.addDebugMessage(
                            LocalizedMessages.text("command.levelledmobs.debug.runtime.d194", mapOf("value-1" to (dropBase.material), "value-2" to (papiResult)), false)
                        )
                    } else {
                        info.addDebugMessage(
                            LocalizedMessages.text("command.levelledmobs.debug.runtime.d195", mapOf("value-1" to (papiResult)), false),
                        )
                    }
                }
                return false
            }
        }

        if (!info.equippedOnly && (dropBase.minPlayerLevel > -1 || dropBase.maxPlayerLevel > -1)) {
            // проверьте, был ли уже кэширован результат переменной, и используйте его, если да
            val variableToUse =
                if (dropBase.playerLevelVariable.isNullOrEmpty()) "%level%" else dropBase.playerLevelVariable!!
            val levelToUse: Int
            if (info.playerLevelVariableCache.containsKey(variableToUse))
                levelToUse = info.playerLevelVariableCache[variableToUse]!!
            else {
                //levelToUse = main.levelManager.getPlayerLevelSourceNumber(info.mobKiller, variableToUse);
                val result: PlayerLevelSourceResult = main.levelManager.getPlayerLevelSourceNumber(
                    info.mobKiller, info.lmEntity!!, variableToUse
                )
                levelToUse = if (result.isNumericResult) result.numericResult.toInt() else 1
                info.playerLevelVariableCache[variableToUse] = levelToUse
            }

            if (dropBase.minPlayerLevel > 0 && levelToUse < dropBase.minPlayerLevel ||
                dropBase.maxPlayerLevel in 1..<levelToUse
            ) {
                if (main.debugManager.isDebugTypeEnabled(DebugType.CUSTOM_DROPS)) {
                    if (dropBase is CustomDropItem) {
                        info.addDebugMessage(
                            LocalizedMessages.text("command.levelledmobs.debug.runtime.d196", mapOf("value-1" to (info.lmEntity?.typeName), "value-2" to (dropBase.material), "value-3" to (levelToUse)), false) +
                                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d197", mapOf("value-1" to (dropBase.minPlayerLevel), "value-2" to (dropBase.maxPlayerLevel)), false),
                        )
                    } else {
                        info.addDebugMessage(
                            LocalizedMessages.text("command.levelledmobs.debug.runtime.d198", mapOf("value-1" to (info.lmEntity?.typeName), "value-2" to (levelToUse), "value-3" to (dropBase.minPlayerLevel)), false) +
                                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d199", mapOf("value-1" to (dropBase.maxPlayerLevel)), false)
                        )
                    }
                }
                return false
            }
        }

        return true
    }

    private fun executeCommand(
        customCommand: CustomCommand,
        info: CustomDropProcessingInfo
    ) {
        if (info.equippedOnly && !customCommand.runOnSpawn) return
        if (!info.equippedOnly && !customCommand.runOnDeath) return

        val main = LevelledMobs.instance
        for (commandPre in customCommand.commands) {
            var command = processRangedCommand(commandPre, customCommand)
            command = main.levelManager.replaceStringPlaceholders(
                command, info.lmEntity!!, false,
                info.lmEntity!!.associatedPlayer, false
            )
            var mobScale = ""
            var mobScaleRounded = ""
            if (customCommand.mobScale != null) {
                val newScale = info.lmEntity!!.getMobLevel.toFloat() * customCommand.mobScale!!.toFloat()
                mobScale = newScale.toString()
                mobScaleRounded = (Utils.round(newScale.toDouble()).toInt()).toString()
            }
            command = command.replace("%mob-scale%", mobScale)
            command = command.replace("%mob-scale-rounded%", mobScaleRounded)

            if (command.contains("%") && ExternalCompatibilityManager.hasPapiInstalled)
                command = ExternalCompatibilityManager.getPapiPlaceholder(
                    info.mobKiller,
                    command,
                    info.lmEntity?.invalidPlaceholderReplacement
                )

            val maxAllowedTimesToRun: Int = LevelledMobs.instance.helperSettings.getInt(
                "customcommand-amount-limit", 10
            )
            var timesToRun = customCommand.amount

            if (customCommand.hasAmountRange) {
                timesToRun = (main.random.nextInt(
                    customCommand.amountRangeMax - customCommand.amountRangeMin + 1
                )
                        + customCommand.amountRangeMin)
            }

            timesToRun = timesToRun.coerceAtMost(maxAllowedTimesToRun)

            val debugCommand = if (timesToRun > 1) LocalizedMessages.text(
                "command.levelledmobs.debug.runtime.d203",
                mapOf("count" to timesToRun),
                false
            ) else LocalizedMessages.text(
                "command.levelledmobs.debug.runtime.d204",
                colorize = false
            )
            val commandFinal = command
            DebugManager.log(DebugType.CUSTOM_COMMANDS, info.lmEntity) { debugCommand + commandFinal }

            val commandToRun = command
            val finalTimesToRun = timesToRun
            val scheduler = SchedulerWrapper {
                executeTheCommand(commandToRun, finalTimesToRun)
            }
            if (customCommand.delay > 0)
                scheduler.runGlobalDelayed(customCommand.delay.toLong())
            else
                scheduler.runGlobal()
        }
    }

    private fun executeTheCommand(command: String, timesToRun: Int) {
        repeat(timesToRun) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
        }
    }

    private fun processRangedCommand(
        command: String,
        cc: CustomCommand
    ): String {
        if (cc.rangedEntries.isEmpty()) return command

        var newCommand = command

        for ((rangedKey, rangedValue) in cc.rangedEntries) {
            if (!rangedValue.contains("-")) {
                newCommand = newCommand.replace("%$rangedKey%", rangedValue)
                continue
            }

            val nums = rangedValue.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (nums.size != 2) continue

            if (!Utils.isInteger(nums[0].trim()) || !Utils.isInteger(
                    nums[1].trim())
            ) {
                continue
            }
            var min = nums[0].trim().toInt()
            val max = nums[1].trim().toInt()
            if (max < min) min = max

            val rangedNum = LevelledMobs.instance.random.nextInt(max - min + 1) + min
            newCommand = newCommand.replace("%$rangedKey%", rangedNum.toString())
        }

        return newCommand
    }

    private fun getCookedVariantOfMeat(itemStack: ItemStack): ItemStack {
        return when (itemStack.type) {
            Material.BEEF -> ItemStack(Material.COOKED_BEEF)
            Material.CHICKEN -> ItemStack(Material.COOKED_CHICKEN)
            Material.COD -> ItemStack(Material.COOKED_COD)
            Material.MUTTON -> ItemStack(Material.COOKED_MUTTON)
            Material.PORKCHOP -> ItemStack(Material.COOKED_PORKCHOP)
            Material.RABBIT -> ItemStack(Material.COOKED_RABBIT)
            Material.SALMON -> ItemStack(Material.COOKED_SALMON)
            else -> itemStack
        }
    }

    fun setDropInstanceFromId(groupId: String, dropInstance: CustomDropInstance) {
        groupIdToInstance[groupId] = dropInstance
    }

    fun getGroupLimits(dropBase: CustomDropBase): GroupLimits? {
        val limitsDefault = groupLimitsMap["default"]

        if (!dropBase.hasGroupId || !groupLimitsMap.containsKey(dropBase.groupId))
            return limitsDefault

        return groupLimitsMap[dropBase.groupId]
    }

    fun clearGroupIdMappings() {
        groupIdToInstance.clear()
    }
}
