package io.github.arcaneplugins.levelledmobs.customdrops

import io.github.arcaneplugins.levelledmobs.misc.NamespacedKeys
import io.github.arcaneplugins.levelledmobs.wrappers.LivingEntityWrapper
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * Содержит информацию о том, какие специальные предметы экипированы на мобе.
 *
 * @author stumper66
 * @since 3.3.3
 */
class EquippedItemsInfo {
    var helmet: ItemStack? = null
    var chestplate: ItemStack? = null
    var leggings: ItemStack? = null
    var boots: ItemStack? = null
    var mainHand: ItemStack? = null
    var offhand: ItemStack? = null

    companion object{
        fun getEntityEquippedItems(
            lmEntity: LivingEntityWrapper
        ): EquippedItemsInfo? {
            return loadFromPDC(lmEntity)
        }

        private fun loadFromPDC(
            lmEntity: LivingEntityWrapper
        ): EquippedItemsInfo{
            val equipment = EquippedItemsInfo()

            equipment.mainHand = getItemFromPDC(NamespacedKeys.equipment0, lmEntity)
            equipment.offhand = getItemFromPDC(NamespacedKeys.equipment1, lmEntity)
            equipment.helmet = getItemFromPDC(NamespacedKeys.equipment2, lmEntity)
            equipment.chestplate = getItemFromPDC(NamespacedKeys.equipment3, lmEntity)
            equipment.leggings = getItemFromPDC(NamespacedKeys.equipment4, lmEntity)
            equipment.boots = getItemFromPDC(NamespacedKeys.equipment5, lmEntity)

            return equipment
        }

        private fun getItemFromPDC(
            key: NamespacedKey,
            lmEntity: LivingEntityWrapper
        ): ItemStack? {
            val temp = lmEntity.pdc.get(key, PersistentDataType.BYTE_ARRAY)
            return if (temp != null)
                ItemStack.deserializeBytes(temp)
            else
                null
        }
    }

    fun saveEquipment(
        lmEntity: LivingEntityWrapper
    ){
        saveEquipmentToPDC(lmEntity)
    }

    private fun saveEquipmentToPDC(
        lmEntity: LivingEntityWrapper
    ){
        saveItemToPDC(mainHand, NamespacedKeys.equipment0, lmEntity)
        saveItemToPDC(offhand, NamespacedKeys.equipment1, lmEntity)
        saveItemToPDC(helmet, NamespacedKeys.equipment2, lmEntity)
        saveItemToPDC(chestplate, NamespacedKeys.equipment3, lmEntity)
        saveItemToPDC(leggings, NamespacedKeys.equipment4, lmEntity)
        saveItemToPDC(boots, NamespacedKeys.equipment5, lmEntity)
    }

    private fun saveItemToPDC(
        item: ItemStack?,
        key: NamespacedKey,
        lmEntity: LivingEntityWrapper
    ) {
        if (isItemAllowedForSerialization(item)){
            val serialized = item!!.serializeAsBytes()
            lmEntity.pdc.set(key, PersistentDataType.BYTE_ARRAY, serialized)
        }
    }

    private fun isItemAllowedForSerialization(itemStack: ItemStack?): Boolean{
        if (itemStack == null) return false
        // возможно, позже появятся еще типы
        if (itemStack.type == Material.AIR) return false

        return true
    }
}
