package io.github.arcaneplugins.levelledmobs.rules

import io.github.arcaneplugins.levelledmobs.annotations.DoNotMerge
import io.github.arcaneplugins.levelledmobs.annotations.DoNotShow
import io.github.arcaneplugins.levelledmobs.annotations.DoNotShowFalse
import io.github.arcaneplugins.levelledmobs.annotations.ExcludeFromHash
import io.github.arcaneplugins.levelledmobs.annotations.RuleFieldInfo
import io.github.arcaneplugins.levelledmobs.enums.ExternalCompatibility
import io.github.arcaneplugins.levelledmobs.enums.MobCustomNameStatus
import io.github.arcaneplugins.levelledmobs.enums.MobTamedStatus
import io.github.arcaneplugins.levelledmobs.enums.NametagVisibilityEnum
import io.github.arcaneplugins.levelledmobs.enums.RuleType
import io.github.arcaneplugins.levelledmobs.enums.VanillaBonusEnum
import io.github.arcaneplugins.levelledmobs.misc.CachedModalList
import io.github.arcaneplugins.levelledmobs.rules.strategies.CustomStrategy
import io.github.arcaneplugins.levelledmobs.rules.strategies.LevellingStrategy
import io.github.arcaneplugins.levelledmobs.rules.strategies.StrategyType
import io.github.arcaneplugins.levelledmobs.util.Log
import io.github.arcaneplugins.levelledmobs.util.LocalizedMessages
import java.util.Objects
import java.util.TreeMap
import org.bukkit.Particle
import org.bukkit.block.Biome
import org.bukkit.generator.structure.Structure
import kotlin.collections.iterator


/**
 * Содержит правила, проанализированные из rules.yml, для составления списка правил.
 *
 * @author stumper66
 * @since 3.0.0
 */
@Suppress("UNCHECKED_CAST")
class RuleInfo(
    @DoNotMerge @ExcludeFromHash @DoNotShow
    internal var ruleName: String = "Без имени"
) {
    @field:DoNotMerge @field:DoNotShow
    var ruleIsEnabled = true
    @field:RuleFieldInfo("display.rules.fields.construct-level", RuleType.APPLY_SETTING)
    var constructLevel: String? = null
    @field:DoNotShowFalse @field:DoNotMerge @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.is-temp-disabled", RuleType.MISC)
    var isTempDisabled = false
    @field:DoNotShowFalse @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.use-no-spawner-particles", RuleType.APPLY_SETTING)
    var useNoSpawnerParticles: Boolean? = null
    @field:RuleFieldInfo("display.rules.fields.baby-mobs-inherit-adult-settings", RuleType.APPLY_SETTING)
    var babyMobsInheritAdultSetting: Boolean? = null
    @field:RuleFieldInfo("display.rules.fields.mob-level-inheritance", RuleType.APPLY_SETTING)
    var mobLevelInheritance: Boolean? = null
    @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.use-custom-drops", RuleType.APPLY_SETTING)
    var customDropsUseForMobs: Boolean? = null
    @field:RuleFieldInfo("display.rules.fields.stop-proessing-rules", RuleType.APPLY_SETTING)
    var stopProcessingRules: Boolean? = null
    @field:ExcludeFromHash @field:DoNotShow
    var mergeEntityNameOverrides: Boolean? = null
    @field:RuleFieldInfo("display.rules.fields.passenger-match-level", RuleType.APPLY_SETTING)
    var passengerMatchLevel: Boolean? = null
    @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.lock-entity", RuleType.APPLY_SETTING)
    var lockEntity: Boolean? = null
    @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.spawner-particles-count", RuleType.APPLY_SETTING)
    var spawnerParticlesCount: Int? = null
    @RuleFieldInfo("display.rules.fields.max-random-variance", RuleType.STRATEGY)
    var maxRandomVariance: Int? = null
    @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.creeper-max-damage-radius", RuleType.APPLY_SETTING)
    var creeperMaxDamageRadius: Int? = null
    @field:RuleFieldInfo("display.rules.fields.minlevel", RuleType.CONDITION)
    var conditionsMinLevel: Int? = null
    @field:RuleFieldInfo("display.rules.fields.maxlevel", RuleType.CONDITION)
    var conditionsMaxLevel: Int? = null
    @field:RuleFieldInfo("display.rules.fields.minlevel", RuleType.APPLY_SETTING)
    var restrictionsMinLevel: Int? = null
    @field:RuleFieldInfo("display.rules.fields.maxlevel", RuleType.APPLY_SETTING)
    var restrictionsMaxLevel: Int? = null
    @field:RuleFieldInfo("display.rules.fields.apply-above-y", RuleType.CONDITION)
    var conditionsApplyAboveY: Int? = null
    @field:RuleFieldInfo("display.rules.fields.apply-below-y", RuleType.CONDITION)
    var conditionsApplyBelowY: Int? = null
    @field:RuleFieldInfo("display.rules.fields.min-distance-from-spawn", RuleType.CONDITION)
    var conditionsMinDistanceFromSpawn: Int? = null
    @field:RuleFieldInfo("display.rules.fields.max-distance-from-spawn", RuleType.CONDITION)
    var conditionsMaxDistanceFromSpawn: Int? = null
    @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.nametag-visible-time", RuleType.APPLY_SETTING)
    var nametagVisibleTime: Long? = null
    @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.max-death-in-chunk-threshold", RuleType.APPLY_SETTING)
    var maximumDeathInChunkThreshold: Int? = null
    @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.chunk-max-cooldown-time", RuleType.APPLY_SETTING)
    var chunkMaxCoolDownTime: Int? = null
    @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.max-adjacent-chunks", RuleType.APPLY_SETTING)
    var maxAdjacentChunks: Int? = null
    @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.cooldown-time", RuleType.APPLY_SETTING)
    var conditionsCooldownTime: Long? = null
    @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.times-to-cooldown-activation", RuleType.CONDITION)
    var conditionsTimesToCooldownActivation: Int? = null
    @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.rule-chance", RuleType.CONDITION)
    var conditionsChance: Float? = null
    @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.sunlight-burn-amount", RuleType.APPLY_SETTING)
    var sunlightBurnAmount: Double? = null
    @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.nametag", RuleType.APPLY_SETTING)
    var nametag: String? = null
    @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.creature-death-nametag", RuleType.APPLY_SETTING)
    var nametagCreatureDeath: String? = null
    @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.nametag-placeholder-levelled", RuleType.APPLY_SETTING)
    var nametagPlaceholderLevelled: String? = null
    @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.nametag-placeholder-unlevelled", RuleType.APPLY_SETTING)
    var nametagPlaceholderUnlevelled: String? = null
    @field:DoNotMerge @field:ExcludeFromHash @field:DoNotShow
    var presetName: String? = null
    @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.drop-table-ids", RuleType.APPLY_SETTING)
    val customDropDropTableIds = mutableListOf<String>()
    @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.health-indicator", RuleType.APPLY_SETTING)
    var healthIndicator: HealthIndicator? = null
    @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.invalid-placeholder-replacement", RuleType.APPLY_SETTING)
    var invalidPlaceholderReplacement: String? = null
    @field:RuleFieldInfo("display.rules.fields.mob-customname", RuleType.CONDITION)
    var conditionsMobCustomnameStatus = MobCustomNameStatus.NOT_SPECIFIED
    @field:RuleFieldInfo("display.rules.fields.mob-tamed-status", RuleType.CONDITION)
    var conditionsMobTamedStatus = MobTamedStatus.NOT_SPECIFIED
    @field:RuleFieldInfo("display.rules.fields.levelling-strategy", RuleType.STRATEGY)
    val levellingStrategy = mutableMapOf<StrategyType, LevellingStrategy>()
    @field:RuleFieldInfo("display.rules.fields.custom-strategy", RuleType.STRATEGY)
    val customStrategy: MutableMap<String, CustomStrategy> = TreeMap(String.CASE_INSENSITIVE_ORDER)
    @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.entity-name-overrides-with-level", RuleType.APPLY_SETTING)
    var entityNameOverridesLevel: MutableMap<String, MutableList<LevelTierMatching>>? = null
    @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.entity-name-overrides", RuleType.APPLY_SETTING)
    var entityNameOverrides: MutableMap<String, LevelTierMatching>? = null
    @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.custom-death-messages", RuleType.APPLY_SETTING)
    var deathMessages: DeathMessages? = null
    @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.nametag-visibility-options", RuleType.APPLY_SETTING)
    var nametagVisibilityEnum: MutableList<NametagVisibilityEnum>? = null
    @field:DoNotMerge @field:ExcludeFromHash @field:DoNotShow
    val ruleSourceNames = mutableMapOf<String, String>()
    @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.spawner-particle", RuleType.APPLY_SETTING)
    var spawnerParticle: Particle? = null
    @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.tiered-coloring-info", RuleType.APPLY_SETTING)
    var tieredColoringInfos: MutableList<TieredColoringInfo>? = null
    @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.enabled-compatibilities", RuleType.APPLY_SETTING)
    var enabledExtCompats: MutableMap<ExternalCompatibility, Boolean>? = null
    @field:RuleFieldInfo("display.rules.fields.mob-nbt-data", RuleType.APPLY_SETTING)
    var mobNBTData: MergeableStringList? = null
    @field:RuleFieldInfo("display.rules.fields.skylight-level", RuleType.CONDITION)
    var conditionsSkyLightLevel: MinAndMax? = null
    @field:RuleFieldInfo("display.rules.fields.worlds", RuleType.CONDITION)
    var conditionsWorlds: CachedModalList<String>? = null
    @field:RuleFieldInfo("display.rules.fields.entities", RuleType.CONDITION)
    var conditionsEntities: CachedModalList<String>? = null
    @field:RuleFieldInfo("display.rules.fields.biomes", RuleType.CONDITION)
    var conditionsBiomes: CachedModalList<Biome>? = null
    @field:RuleFieldInfo("display.rules.fields.external-plugins", RuleType.CONDITION)
    var conditionsExternalPlugins: CachedModalList<String>? = null
    @field:RuleFieldInfo("display.rules.fields.custom-names", RuleType.CONDITION)
    var conditionsCustomNames: CachedModalList<String>? = null
    @field:RuleFieldInfo("display.rules.fields.no-drop-entities", RuleType.CONDITION)
    var conditionsNoDropEntities: CachedModalList<String>? = null
    @field:RuleFieldInfo("display.rules.fields.worldguard-regions", RuleType.CONDITION)
    var conditionsWGregions: CachedModalList<String>? = null
    @field:RuleFieldInfo("display.rules.fields.worldguard-region-owners", RuleType.CONDITION)
    var conditionsWGregionOwners: CachedModalList<String>? = null
    @field:RuleFieldInfo("display.rules.fields.mythic-mobs-names", RuleType.CONDITION)
    var conditionsMMnames: CachedModalList<String>? = null
    @field:RuleFieldInfo("display.rules.fields.spawner-names", RuleType.CONDITION)
    var conditionsSpawnerNames: CachedModalList<String>? = null
    @field:RuleFieldInfo("display.rules.fields.spawner-egg-names", RuleType.CONDITION)
    var conditionsSpawnegEggNames: CachedModalList<String>? = null
    @field:RuleFieldInfo("display.rules.fields.scoreboard-tags", RuleType.CONDITION)
    var conditionsScoreboardTags: CachedModalList<String>? = null
    @field:RuleFieldInfo("display.rules.fields.world-tick-time", RuleType.CONDITION)
    var conditionsWorldTickTime: CachedModalList<MinAndMax>? = null
    @field:RuleFieldInfo("display.rules.fields.vanilla-bonuses", RuleType.APPLY_SETTING)
    var vanillaBonuses: CachedModalList<VanillaBonusEnum>? = null
    @field:RuleFieldInfo("display.rules.fields.spawn-reasons", RuleType.CONDITION)
    var conditionsSpawnReasons: CachedModalList<String>? = null
    @field:RuleFieldInfo("display.rules.fields.structures", RuleType.CONDITION)
    var conditionsStructure: CachedModalList<Structure>? = null
    @field:RuleFieldInfo("display.rules.fields.player-permissions", RuleType.CONDITION)
    var conditionsPermission: CachedModalList<String>? = null
    @field:RuleFieldInfo("display.rules.fields.player-names", RuleType.CONDITION)
    var conditionsPlayerNames: CachedModalList<String>? = null
    @field:RuleFieldInfo("display.rules.fields.gamemode", RuleType.CONDITION)
    var conditionsGamemode: CachedModalList<String>? = null
    @field:RuleFieldInfo("display.rules.fields.within-coordinates", RuleType.CONDITION)
    var conditionsWithinCoords: WithinCoordinates? = null
    @field:RuleFieldInfo("display.rules.fields.mob-multipliers", RuleType.APPLY_SETTING)
    var mobMultipliers: FineTuningAttributes? = null
    @field:ExcludeFromHash @field:RuleFieldInfo("display.rules.fields.chunk-kill-options", RuleType.APPLY_SETTING)
    var chunkKillOptions: ChunkKillOptions? = null

    companion object{
        fun formatRule(
            sb: StringBuilder,
            values: MutableMap<RuleSortingInfo, String>
        ){
            var hadConditions = false
            var hadApplySettings = false
            var hadStrategies = false
            var hadMisc = false

            for (item in values.asSequence()
                .filter { v -> v.key.ruleType == RuleType.CONDITION }
                .sortedBy { v -> v.key.fieldName }
                .iterator()
            ){
                if (!hadConditions){
                    hadConditions = true
                    sb.append("\n").append(
                        LocalizedMessages.text("display.rules.conditions-heading", colorize = false)
                    )
                }
                sb.append("\n   ").append(item.key.fieldName)
                    .append(": ").append(item.value)
            }

            for (item in values.asSequence()
                .filter { v -> v.key.ruleType == RuleType.APPLY_SETTING }
                .sortedBy { v -> v.key.fieldName }
                .iterator()
            ){
                if (!hadApplySettings){
                    hadApplySettings = true
                    sb.append("\n").append(
                        LocalizedMessages.text("display.rules.apply-settings-heading", colorize = false)
                    )
                }
                sb.append("\n   ").append(item.key.fieldName)
                    .append(": ").append(item.value)
            }

            for (item in values.asSequence()
                .filter { v -> v.key.ruleType == RuleType.STRATEGY }
                .sortedBy { v -> v.key.fieldName }
                .iterator()
            ){
                if (!hadStrategies){
                    hadStrategies = true
                    sb.append("\n").append(
                        LocalizedMessages.text("display.rules.strategies-heading", colorize = false)
                    )
                }
                sb.append("\n   ").append(item.key.fieldName)
                    .append(": ").append(item.value)
            }

            for (item in values.asSequence()
                .filter { v -> v.key.ruleType != RuleType.CONDITION &&
                        v.key.ruleType != RuleType.APPLY_SETTING &&
                        v.key.ruleType != RuleType.STRATEGY &&
                        v.key.ruleType != RuleType.NO_CATEGORY &&
                        v.key.fieldName != "Companion"}
                .iterator()
            ){
                if (!hadMisc){
                    hadMisc = true
                    sb.append("\n").append(
                        LocalizedMessages.text("display.rules.misc-heading", colorize = false)
                    )
                }
                sb.append("\n   ").append(item.key.fieldName)
                    .append(": ").append(item.value)
            }
        }
    }

    fun mergePresetRules(preset: RuleInfo?) {
        if (preset == null) return

        try {
            for (f in preset::javaClass.get().declaredFields) {
                if (f.name == "Companion") continue

                f.trySetAccessible()

                if (f.isAnnotationPresent(DoNotMerge::class.java)) continue
                if (f.get(preset) == null) continue

                var presetValue = f.get(preset)
                val ruleValue = this::javaClass.get().getDeclaredField(f.name).get(this)
                var skipSettingValue = false
                val mergableRule = presetValue as? MergableRule

                if (f.name == "entityNameOverrides" && this.entityNameOverrides != null && presetValue is MutableMap<*, *>) {
                    entityNameOverrides!!.putAll(presetValue as MutableMap<String, LevelTierMatching>)
                    skipSettingValue = true
                } else if (mergableRule != null) {
                    if (ruleValue != null && mergableRule.doMerge) {
                        (ruleValue as MergableRule).merge(mergableRule.cloneItem() as MergableRule)
                        skipSettingValue = true
                    }
                    else
                        presetValue = mergableRule.cloneItem()
                } else if (f.name == "entityNameOverrides_Level" && this.entityNameOverridesLevel != null) {
                    entityNameOverridesLevel!!.putAll(
                        presetValue as MutableMap<String, MutableList<LevelTierMatching>>
                    )
                    skipSettingValue = true
                }
                else if (f.name == "customDropDropTableIds") {
                    val mergingPreset = presetValue as MutableList<String>
                    customDropDropTableIds.addAll(mergingPreset)

                    skipSettingValue = true
                } else if (presetValue is MergeableStringList
                    && ruleValue != null
                ) {
                    if (presetValue.doMerge && presetValue.isNotEmpty) {
                        val current = ruleValue as MergeableStringList
                        current.items.addAll(presetValue.items)
                        skipSettingValue = true
                    }
                }

                if (presetValue is CachedModalList<*>) {
                    val thisCachedModalList = ruleValue as CachedModalList<*>?

                    if (thisCachedModalList != null && presetValue.doMerge)
                        thisCachedModalList.mergeCachedModal(presetValue)
                    else
                        this::javaClass.get().getDeclaredField(f.name).set(this, presetValue)

                    skipSettingValue = true
                }
                if (f.name == "levellingStrategy") {
                    val mergingStrategies = presetValue as MutableMap<StrategyType, LevellingStrategy>

                    for (strategy in mergingStrategies){
                        if (this.levellingStrategy.containsKey(strategy.key) && strategy.value.shouldMerge)
                            this.levellingStrategy[strategy.key]!!.mergeRule(strategy.value)
                        else
                            this.levellingStrategy[strategy.key] = strategy.value.cloneItem()
                    }

                    skipSettingValue = true
                }
                if (f.name == "customStrategy"){
                    val mergingStrategies = presetValue as MutableMap<String, CustomStrategy>

                    if (mergingStrategies.isNotEmpty()){
                        this.customStrategy.clear()
                        this.customStrategy.putAll(mergingStrategies)
                    }

                    skipSettingValue = true
                }

                if (presetValue is TieredColoringInfo)
                    presetValue = presetValue.cloneItem()!!

                if (presetValue == MobCustomNameStatus.NOT_SPECIFIED ||
                    presetValue == MobTamedStatus.NOT_SPECIFIED) {
                    continue
                }

                if (!skipSettingValue)
                    this::javaClass.get().getDeclaredField(f.name).set(this, presetValue)

                ruleSourceNames[f.name] = preset.ruleName
            }
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        }
    }

    data class RuleSortingInfo(
        val ruleType: RuleType,
        val fieldName: String
    ){
        override fun toString(): String {
            return "$ruleType: $fieldName"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RuleSortingInfo)
                return false

            return other.ruleType == this.ruleType &&
                other.fieldName == this.fieldName
        }

        override fun hashCode(): Int {
            return Objects.hash(ruleType, fieldName)
        }
    }

    fun formatRulesVisually(): String {
        return formatRulesVisually(false, null)
    }

    fun formatRulesVisually(
        isForHash: Boolean,
        excludedKeys: MutableList<String>?
    ): String {
        val values = mutableMapOf<RuleSortingInfo, String>()
        val sb = StringBuilder()

        sb.append("\n").append(
            LocalizedMessages.text("display.rules.id-heading", colorize = false)
        )
        if (this.presetName.isNullOrEmpty())
            sb.append(ruleName)
        else
            sb.append(this.presetName)
        if (!this.ruleIsEnabled)
            sb.append(LocalizedMessages.text("display.rules.disabled-suffix", colorize = false))
        else
            sb.append("&r")

        try {
            for (f in this::javaClass.get().declaredFields) {
                if (isForHash && f.isAnnotationPresent(ExcludeFromHash::class.java))
                    continue
                if (!isForHash && f.isAnnotationPresent(DoNotShow::class.java))
                    continue

                val value = f.get(this) ?: continue
                var ruleName = f.name
                var ruleInfoType = RuleType.MISC
                val ruleTypeInfo = f.getAnnotation(RuleFieldInfo::class.java)
                if (ruleTypeInfo != null) {
                    ruleInfoType = ruleTypeInfo.ruleType
                    ruleName = if (isForHash) f.name else LocalizedMessages.text(
                        ruleTypeInfo.value,
                        colorize = false
                    )
                }

                if (!isForHash && f.isAnnotationPresent(DoNotShowFalse::class.java) &&
                        value is Boolean && !value) continue
                if (value.toString().isEmpty()) continue
                if (excludedKeys != null && excludedKeys.contains(f.name)) continue
                if (value.toString() == "NOT_SPECIFIED") continue
                if (value.toString() == "{}") continue
                if (value.toString() == "[]") continue
                if (value.toString().equals("NONE", ignoreCase = true)) continue

                if (value is CachedModalList<*>) {
                    if (value.isEmpty() && !value.includeAll && !value.excludeAll)
                        continue
                }
                val ruleInfo = RuleSortingInfo(
                    ruleInfoType,
                    ruleName
                )
                values[ruleInfo] = "&b$value&r"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        formatRule(sb, values)

        return sb.toString()
    }

    override fun toString(): String {
        if (this.ruleName.isEmpty())
            return super.toString()

        return this.ruleName
    }
}
