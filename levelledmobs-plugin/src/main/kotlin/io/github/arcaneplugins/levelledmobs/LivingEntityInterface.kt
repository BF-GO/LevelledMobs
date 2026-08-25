package io.github.arcaneplugins.levelledmobs

import io.github.arcaneplugins.levelledmobs.rules.RuleInfo
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.EntityType

/**
 * Интерфейс, используемый для оболочки LivingEntity для предоставления дополнительных общих команд и информации.
 *
 * @author stumper66
 * @since 3.0.0
 */
interface LivingEntityInterface {
    val entityType: EntityType?

    val location: Location?

    val world: World?

    val typeName: String

    fun getApplicableRules(): MutableList<RuleInfo>

    val distanceFromSpawn: Double

    var spawnedTimeOfDay: Int

    val summonedLevel: Int?

    val wasSummoned: Boolean

    fun clearEntityData()

    fun free()
}