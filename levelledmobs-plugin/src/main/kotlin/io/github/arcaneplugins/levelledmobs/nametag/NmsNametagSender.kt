package io.github.arcaneplugins.levelledmobs.nametag

import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.nametag.ComponentUtils.appendComponents
import io.github.arcaneplugins.levelledmobs.nametag.ComponentUtils.getTextComponent
import io.github.arcaneplugins.levelledmobs.nametag.ComponentUtils.getTranslatableComponent
import io.github.arcaneplugins.levelledmobs.nametag.KyoriNametags.generateComponent
import io.github.arcaneplugins.levelledmobs.result.NametagResult
import io.github.arcaneplugins.levelledmobs.util.MessageUtils.colorizeAll
import java.lang.reflect.InvocationTargetException
import java.util.LinkedList
import java.util.Optional
import java.util.function.Consumer
import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player

/**
 * Отправляет игрокам пакеты именных тегов версии NMS.
 *
 * @author PenalBuffalo (aka stumper66)
 * @since 3.6.0
 */
@Suppress("UNCHECKED_CAST")
class NmsNametagSender : NametagSender {
    @Volatile
    private var def = LevelledMobs.instance.definitions
    override fun sendNametag(
        livingEntity: LivingEntity,
        nametag: NametagResult,
        player: Player,
        alwaysVisible: Boolean
    ) {
        val prepareAndSend = Runnable {
            val packet = prepareNametagPacket(livingEntity, nametag, alwaysVisible) ?: return@Runnable
            player.scheduler.run(
                LevelledMobs.instance,
                Consumer {
                    if (player.isOnline && player.isValid)
                        sendPreparedPacket(player, packet)
                },
                null
            )
        }

        if (Bukkit.isOwnedByCurrentRegion(livingEntity))
            prepareAndSend.run()
        else
            livingEntity.scheduler.run(
                LevelledMobs.instance,
                Consumer { prepareAndSend.run() },
                null
            )
    }

    fun refresh() {
        this.def = LevelledMobs.instance.definitions
    }

    private fun prepareNametagPacket(
        livingEntity: LivingEntity,
        nametag: NametagResult,
        doAlwaysVisible: Boolean
    ): Any? {
        try {
            // livingEntity.getHandle()
            val internalLivingEntity = def.methodGetHandle!!.invoke(livingEntity)
            // internalLivingEntity.getEntityData()
            val entityDataPreClone = def.methodGetEntityData!!.invoke(internalLivingEntity)
            val entityData: Any = cloneEntityData(entityDataPreClone, internalLivingEntity) ?: return null

            //final Object entityData = entityDataPreClone;
            val optionalComponent =
                def.fieldOPTIONALCOMPONENT!![def.clazzDataWatcherRegistry]
            // https://wiki.vg/Entity_metadata#Entity_Metadata_Format

            // окончательный EntityDataAccessor<Optional<Component>> customNameAccessor =
            //     //new EntityDataAccessor<>(2, EntityDataSerializers.OPTIONAL_COMPONENT);
            val customNameAccessor =
                def.ctorEntityDataAccessor!!.newInstance(2, optionalComponent)
            val customName: Optional<Any> = buildNametagComponent(livingEntity, nametag)

            //final Optional<Object> customName = entityData.set(customNameAccessor, customName);
            def.methodSet!!.invoke(entityData, customNameAccessor, customName)

            val objBoolean = def.fieldBOOLEAN!![def.clazzDataWatcherRegistry]
            val customNameVisibleAccessor =
                def.ctorEntityDataAccessor!!.newInstance(3, objBoolean)

            // entityData.set(customNameVisibleAccessor, !nametag.isNullOrEmpty() && doAlwaysVisible);
            def.methodSet!!.invoke(entityData, customNameVisibleAccessor, doAlwaysVisible)

            val livingEntityId = def.methodGetId!!.invoke(internalLivingEntity) as Int

            // Список<DataWatcher.b<?>>
            // java.util.List getAllNonDefaultValues() -> c
            val getAllNonDefaultValues: List<*> = getNametagFields(entityData)
            return def.ctorPacket!!.newInstance(livingEntityId, getAllNonDefaultValues)
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
        } catch (e: InstantiationException) {
            e.printStackTrace()
        }
        return null
    }

    private fun sendPreparedPacket(player: Player, packet: Any) {
        try {
            val serverPlayer = def.methodPlayergetHandle!!.invoke(player)
            val connection = def.fieldConnection!![serverPlayer]
            def.methodSend!!.invoke(connection, packet)
            LevelledMobs.instance.nametagQueueManager.recordPacketSent()
        } catch (e: ReflectiveOperationException) {
            e.printStackTrace()
        }
    }

    // возвращает SynchedEntityData (DataWatcher)
    // аргументы: SynchedEntityData, LivingEntity (нмс)
    @Throws(InvocationTargetException::class, InstantiationException::class, IllegalAccessException::class)
    private fun cloneEntityData(
        entityDataPreClone: Any,
        internalLivingEntity: Any
    ): Any? {
        // конструктор:
        // public a(SyncedDataHolder synceddataholder)
        // SynchedEntityData.Builder builder = new SynchedEntityData.Builder(internalLivingEntity);
        val entityDataBuilder: Any = def.ctorSynchedEntityDataBuilder!!.newInstance(internalLivingEntity)

        try {
            // SynchedEntityData.DataItem<?>[]
            val itemsById = def.fieldInt2ObjectMap!!.get(entityDataPreClone) as Array<Any>
            if (itemsById.isEmpty()) return null

            for (objDataItem in itemsById) {
                // .getAccessor()
                val accessor: Any = def.methodGetAccessor!!.invoke(objDataItem)
                // .getValue()
                val value: Any = def.methodGetValue!!.invoke(objDataItem)

                // builder.define(dataItem.getAccessor(), dataItem.getValue());
                def.methodDataWatcherBuilderDefine!!.invoke(entityDataBuilder, accessor, value)
            }

            // builder.build();
            return def.methodDataWatcherBuilderBuild!!.invoke(entityDataBuilder)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }

        return def.methodDataWatcherBuilderBuild!!.invoke(entityDataBuilder)
    }

    private fun getNametagFields(
        entityData: Any
    ): List<Any> {
        // Список<SynchedEntityData.DataValue<?>>
        val results: MutableList<Any> = LinkedList()

        try {
            // SynchedEntityData.DataItem<?>[]
            val itemsById =
                def.fieldInt2ObjectMap!!.get(entityData) as Array<Any>

            if (itemsById.isEmpty()) return results

            for (objItem in itemsById) {
                // objItem.value()
                val objData: Any = def.methodDataWatcherItemValue!!.invoke(objItem)

                // .id()
                val objDataId = def.methodDataWatcherGetId!!.invoke(objData) as Int
                if (objDataId !in 2..3) continue

                results.add(objData)
            }
        } catch (ex: java.lang.Exception) {
            ex.printStackTrace()
        }

        return results
    }

    private fun getNametagFieldsLegacy(
        entityData: Any
    ): List<Any> {
        val results: MutableList<Any> = LinkedList()

        try {
            val itemsById =
                def.fieldInt2ObjectMap!![entityData] as Map<Int, Any>

            if (itemsById.isEmpty()) return results

            for (objDataId in itemsById.keys) {
                if (objDataId !in 2..3) continue

                val objDataItem = itemsById[objDataId]
                val accessor = def.methodGetAccessor!!.invoke(objDataItem)

                // DataWatcher.Item
                val dataWatcherItem = def.methodDataWatcherGetItem!!
                    .invoke(entityData, accessor)

                results.add(def.methodDataWatcherItemValue!!.invoke(dataWatcherItem))
                //results.add(objDataItem);
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

        return results
    }

    private fun buildNametagComponent(
        livingEntity: LivingEntity,
        nametag: NametagResult
    ): Optional<Any> {
        if (nametag.isNullOrEmpty) {
            return Optional.empty()
        }

        if (def.hasKiori) {
            // бумажные серверы идут сюда:
            return Optional.of(generateComponent(livingEntity, nametag))
        }

        // остальная часть этого метода будет использоваться только на серверах-спиготах.
        val mobName = nametag.nametagNonNull
        val displayName = "{DisplayName}"
        val displayNameIndex = mobName.indexOf(displayName)

        if (displayNameIndex < 0) {
            val comp = getTextComponent(colorizeAll(nametag.nametagNonNull))
            return if (comp == null) Optional.empty() else Optional.of(comp)
        }

        val leftText = if (displayNameIndex > 0) resolveText(mobName.take(displayNameIndex)) else null

        val rightText =
            if (mobName.length > displayNameIndex + displayName.length) resolveText(mobName.substring(displayNameIndex + displayName.length)) else null
        val mobNameComponent = if (nametag.overriddenName == null) {
            if (def.useTranslationComponents) getTranslatableComponent(def.getTranslationKey(livingEntity)) else getTextComponent(
                livingEntity.name
            )
        }
        else
            getTextComponent(resolveText(nametag.overriddenName))

        // по какой-либо причине, если вы используете пустой компонент,
        // именной тег будет дублироваться при каждом вызове этой функции
        val comp = getTextComponent("")!!

        if (leftText != null) {
            // comp.append(Component);
            appendComponents(comp, getTextComponent(leftText))
        }

        appendComponents(comp, mobNameComponent)

        if (rightText != null) {
            // comp.append(Component);
            appendComponents(comp, getTextComponent(rightText))
        }

        return Optional.of(comp)
    }

    private fun resolveText(text: String?): String? {
        if (text.isNullOrEmpty()) return null

        return colorizeAll(text)
    }

    override fun toString(): String {
        return "Nametags_NMS"
    }
}
