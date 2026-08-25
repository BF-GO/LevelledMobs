package io.github.arcaneplugins.levelledmobs.misc

import java.util.Stack
import io.github.arcaneplugins.levelledmobs.LivingEntityInterface
import io.github.arcaneplugins.levelledmobs.rules.RuleInfo
import io.github.arcaneplugins.levelledmobs.wrappers.LivingEntityWrapperBase
import io.github.arcaneplugins.levelledmobs.util.LocalizedMessages
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.EntityType

/**
 * Обертка для класса LivingEntity, предоставляющая различные общие функции и настройки, используемые для
 * правила обработки. Используется только с командой вызова.
 *
 * @author stumper66
 * @since 3.0.0
 */
class LivingEntityPlaceholder : LivingEntityWrapperBase(), LivingEntityInterface {
    companion object{
        private val cache = Stack<LivingEntityPlaceholder>()
        private val cachedplaceholdersLock = Any()
        private var _spawnedTimeOfDay: Int? = null

        fun getInstance(
            entityType: EntityType,
            location: Location
        ): LivingEntityPlaceholder {
            val leph: LivingEntityPlaceholder

            if (location.world == null)
                throw NullPointerException(
                    LocalizedMessages.text("console.internal.world-required", colorize = false)
                )

            synchronized(cachedplaceholdersLock) {
                leph = if (cache.empty())
                    LivingEntityPlaceholder()
                else
                    cache.pop()
            }

            leph.populateEntityData(entityType, location, location.world)
            leph.inUseCount.set(1)
            return leph
        }
    }

    private fun populateEntityData(
        entityType: EntityType,
        location: Location,
        world: World
    ) {
        this.entityType = entityType
        super.populateData(world, location)
    }

    override fun free() {
        if (inUseCount.decrementAndGet() > 0) return
        if (!isPopulated) return

        clearEntityData()
        synchronized(cachedplaceholdersLock) {
            cache.push(this)
        }
    }

    override fun clearEntityData() {
        this.entityType = null
        super.clearEntityData()
    }

    override var entityType: EntityType? = null
        get() = field!!

    override fun getApplicableRules(): MutableList<RuleInfo> {
        return main.rulesManager.getApplicableRules(this).allApplicableRules
    }

    override val typeName: String
        get() = this.entityType!!.name

    override var spawnedTimeOfDay: Int
        set(value) { _spawnedTimeOfDay = value }
        get() {
            if (_spawnedTimeOfDay == null)
                _spawnedTimeOfDay = world.time.toInt()

            return _spawnedTimeOfDay!!
        }

    override val wasSummoned: Boolean
        get() = summonedLevel != null
}
