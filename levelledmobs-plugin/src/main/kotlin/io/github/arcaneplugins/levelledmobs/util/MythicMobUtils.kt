package io.github.arcaneplugins.levelledmobs.util

import java.lang.reflect.InvocationTargetException
import java.util.Optional
import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.result.MythicMobsMobInfo
import io.github.arcaneplugins.levelledmobs.wrappers.LivingEntityWrapper
import org.bukkit.Bukkit

/**
 * Используется для обнаружения мобов с помощью мифических мобов.
 *
 * @author stumper66
 * @since 3.6.0
 */
object MythicMobUtils {
    fun getMythicMobInfo(lmEntity: LivingEntityWrapper): MythicMobsMobInfo? {
        // приведенный ниже код был написан для MythicMobs v5.0.4-f1007ca3

        /*
           1. В основном классе плагина MythicBukkit получить поле: private MobExecutor mobManager;
           2. У класса MobExecutor вызвать метод с UUID моба: public Optional<ActiveMob> getActiveMob(UUID uuid);
           3. У класса ActiveMob получить поле: private transient MythicMob type;
           4. MythicMob — интерфейс, его реализация — MobType.
           5. Класс MobType содержит необходимые свойства:
              private Boolean preventRandomEquipment = Boolean.valueOf(false);
              private Boolean preventOtherDrops = Boolean.valueOf(false);
              private String internalName;
        */

        // io.lumine.mythic.bukkit.MythicBukkit

        val mmMain = Bukkit.getPluginManager().getPlugin("MythicMobs")
        if (mmMain == null || !mmMain.isEnabled) return null

        val def = LevelledMobs.instance.definitions

        if (def.fieldMMmobManager == null) {
            Log.warKey("console.integration.mythicmobs-manager-null")
            return null
        }

        try {
            val mobExecutorObj = def.fieldMMmobManager!![mmMain]

            //     public Optional<ActiveMob> getActiveMob(UUID uuid) {
            //       return ((MobRegistry)this.mobRegistry.get()).getActiveMob(uuid); }

            // Дополнительно<io.lumine.mythic.core.mobs.ActiveMob>
            val activeMobObj = def.methodMMgetActiveMob!!.invoke(
                mobExecutorObj, lmEntity.livingEntity.uniqueId
            ) as Optional<*>

            if (activeMobObj.isEmpty) return null

            val mobTypeObj = def.fieldMMtype!![activeMobObj.get()]
            val result = MythicMobsMobInfo()
            result.preventOtherDrops = def.fieldMMpreventOtherDrops!![mobTypeObj] as Boolean
            result.preventRandomEquipment = def.fieldMMpreventRandomEquipment!![mobTypeObj] as Boolean
            result.internalName = def.fieldMMinternalName!![mobTypeObj] as String

            return result
        } catch (e: InvocationTargetException) {
            Log.warKey("console.integration.mythicmobs-info-error", mapOf("error" to (e.message ?: "-")))
        } catch (e: IllegalAccessException) {
            Log.warKey("console.integration.mythicmobs-info-error", mapOf("error" to (e.message ?: "-")))
        }
        return null
    }
}
