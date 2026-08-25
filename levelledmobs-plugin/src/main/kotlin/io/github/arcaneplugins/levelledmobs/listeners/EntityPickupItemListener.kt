package io.github.arcaneplugins.levelledmobs.listeners

import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.misc.PickedUpEquipment
import io.github.arcaneplugins.levelledmobs.wrappers.LivingEntityWrapper
import io.github.arcaneplugins.levelledmobs.wrappers.SchedulerWrapper
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent

/**
 * Прослушивает, когда объект берет предметы, и отслеживает их, только если
 * сущность оборудовала предметы так, чтобы мы могли быть уверены, что позже не
 * уничтожить эти предметы
 *
 * @author stumper66
 * @since 3.14.0
 */
class EntityPickupItemListener : Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityPickupItemEvent(event: EntityPickupItemEvent) {
        // извините, ребята, это функция только Paper
        if (!LevelledMobs.instance.ver.isRunningPaper) return
        if (event.entity is Player) return

        val lmEntity = LivingEntityWrapper.getInstance(event.entity)

        if (!lmEntity.isLevelled || lmEntity.livingEntity.equipment == null) {
            lmEntity.free()
            return
        }

        // если вы не клонируете элемент, он изменится на воздух в следующей функции
        val itemStack = event.item.itemStack.clone()
        val pickedUpEquipment = PickedUpEquipment(lmEntity)
        val wrapper = SchedulerWrapper(lmEntity.livingEntity) {
            pickedUpEquipment.checkEquipment(itemStack)
            lmEntity.free()
        }

        lmEntity.inUseCount.getAndIncrement()
        wrapper.runDelayed(1L)
    }
}