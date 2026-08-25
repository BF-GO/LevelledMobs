package io.github.arcaneplugins.levelledmobs.customdrops

import io.github.stumper66.lm_items.ExternalItemRequest
import io.github.stumper66.lm_items.LM_Items
import java.util.Hashtable
import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.MainCompanion
import io.github.arcaneplugins.levelledmobs.debug.DebugManager
import io.github.arcaneplugins.levelledmobs.managers.ExternalCompatibilityManager
import io.github.arcaneplugins.levelledmobs.debug.DebugType
import io.github.arcaneplugins.levelledmobs.managers.MobDataManager
import io.github.arcaneplugins.levelledmobs.managers.NotifyManager
import io.github.arcaneplugins.levelledmobs.util.Log
import io.github.arcaneplugins.levelledmobs.util.LocalizedMessages
import io.github.arcaneplugins.levelledmobs.wrappers.LivingEntityWrapper
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * Interfaces with the plugin LM_Items so can custom items from 3rd party plugins can be used
 * directly in custom drops
 *
 * @author stumper66
 * @since 3.5.0
 */
class LMItemsParser {
    private val pendingItems = mutableMapOf<CustomDropItem, String>()

    fun processPendingItems(){
        for (item in pendingItems){
            parseExternalItemAttributes(item.value, item.key)
        }
        pendingItems.clear()
    }

    fun parseExternalItemAttributes(
        materialName: String,
        item: CustomDropItem
    ): Boolean {
        if (!ExternalCompatibilityManager.instance.doesLMIMeetVersionRequirement()) {
            if (ExternalCompatibilityManager.hasLMItemsInstalled)
                Log.warKey("console.customdrops.lm-items-outdated-item", mapOf("item" to materialName))
            else
                Log.warKey("console.customdrops.lm-items-missing-item", mapOf("item" to materialName))

            return false
        }

        if (!MainCompanion.instance.hasFinishedLoading){
            pendingItems[item] = materialName

            // this is a placeholder item for now
            item.itemStack = ItemStack(Material.STICK)
            return true
        }

        val colon = materialName.indexOf(":")
        item.externalPluginName = materialName.take(colon)
        item.externalItemId = materialName.substring(colon + 1)
        val lmitems = LM_Items.plugin

        if (!lmitems.doesSupportPlugin(item.externalPluginName!!)) {
            Log.warKey("console.customdrops.unsupported-external-plugin", mapOf(
                "plugin" to item.externalPluginName!!
            ))
            return false
        }

        item.isExternalItem = true
        return getExternalItem(item, null)
    }

    fun getExternalItem(
        item: CustomDropItem,
        info: CustomDropProcessingInfo?
    ): Boolean {
        val itemsAPI = LM_Items.plugin.getItemAPIForPlugin(item.externalPluginName!!)

        if (itemsAPI == null) {
            Log.warKey("console.customdrops.items-api-unavailable", mapOf(
                "plugin" to item.externalPluginName!!
            ))
            return false
        }

        val main = LevelledMobs.instance
        val itemRequest = ExternalItemRequest(item.externalItemId!!)
        itemRequest.itemType = item.externalType
        itemRequest.amount = item.externalAmount
        if (info != null && info.formulaResult != null)
            itemRequest.amount = info.formulaResult

        if (main.mainCompanion.externalCompatibilityManager.doesLMIMeetVersionRequirement2()) {
            itemRequest.getMultipleItems = "-" == itemRequest.itemId
            itemRequest.minItems = item.minItems
            itemRequest.maxItems = item.maxItems
            itemRequest.allowedList = item.allowedList
            itemRequest.excludedList = item.excludedList
            itemRequest.isDebugEnabled = main.debugManager.isEnabled
        }

        if (item.externalExtras != null) {
            itemRequest.extras = Hashtable(item.externalExtras!!.size)

            for (key in item.externalExtras!!.keys) {
                var useKey = key
                var value = item.externalExtras!![useKey]

                if (useKey.endsWith("-formula", ignoreCase = true)) {
                    value = evaluateFormula(useKey, value, info?.lmEntity)
                    useKey = useKey.dropLast(8)
                }
                else if (value is String && value.contains("%")) {
                    if (info != null) {
                        value = main.levelManager.replaceStringPlaceholders(
                            value, info.lmEntity!!,true,info.mobKiller,false)
                    }
                    else if (ExternalCompatibilityManager.hasPapiInstalled)
                        value = ExternalCompatibilityManager.getPapiPlaceholder(null, value,null)
                }

                (itemRequest.extras as Hashtable<String, Any>)[useKey] = value
            }
        }

        val result = itemsAPI.getItem(itemRequest)

        if (!result.pluginIsInstalled) {
            Log.warKey("console.customdrops.external-plugin-missing", mapOf(
                "plugin" to item.externalPluginName!!
            ))
            return false
        }

        val itemStack = result.itemStack
        if (itemStack == null) {
            if (result.typeIsNotSupported) {
                if (item.externalType == null) {
                    Log.warKey("console.customdrops.external-type-unsupported", mapOf(
                        "item" to "${item.externalPluginName}:${item.externalItemId}",
                        "type" to "null"
                    ))
                } else {
                    Log.warKey("console.customdrops.external-type-unsupported", mapOf(
                        "item" to "${item.externalPluginName}:${item.externalItemId}",
                        "type" to item.externalType!!
                    ))
                }

                return false
            }

            val msg = LocalizedMessages.text(
                if (item.externalType == null && (result.itemStacks == null || result.itemStacks!!.isEmpty()))
                    "console.customdrops.external-null-item"
                else
                    "console.customdrops.external-null-item-with-type",
                mapOf(
                    "item" to "${item.externalPluginName}:${item.externalItemId}",
                    "type" to (item.externalType ?: "null")
                )
            )

            // on server startup show as warning message
            // after reload show as debug
            if (main.mainCompanion.hasFinishedLoading)
                DebugManager.log(DebugType.CUSTOM_DROPS) { msg }
            else
                Log.war(msg)

            main.customDropsHandler.customDropsParser.invalidExternalItems.add(msg)

            return false
        }

        if (main.mainCompanion.externalCompatibilityManager.doesLMIMeetVersionRequirement2()) {
            if (result.itemStacks != null && result.itemStacks!!.isNotEmpty())
                item.itemStacks = result.itemStacks as MutableList<ItemStack>
            else
                item.itemStack = result.itemStack
        }
        else
            item.itemStack = itemStack

        return true
    }

    private fun evaluateFormula(
        name: String,
        value: Any?,
        lmEntity: LivingEntityWrapper?
    ): Any?{
        if (value == null) return null
        if (lmEntity == null) return 1 // maybe an hack but do you have any better ideas?

        val formulaPre = value.toString()
        val formula = LevelledMobs.instance.levelManager.replaceStringPlaceholdersForFormulas(formulaPre, lmEntity)
        val evalResult = MobDataManager.evaluateExpression(formula)
        if (evalResult.hadError){
            NotifyManager.notifyOfErrorKey("console.formula.customdrop-extra-error", mapOf(
                "name" to name,
                "entity" to lmEntity.nameIfBaby,
                "error" to (evalResult.error ?: "-")
            ))
            DebugManager.log(DebugType.CUSTOM_DROPS_FORMULA, lmEntity){
                val msg = if (formula == formulaPre)
                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d024", mapOf("value-1" to (formula)), false)
                else
                    LocalizedMessages.text("command.levelledmobs.debug.runtime.d025", mapOf("value-1" to (formulaPre)), false) +
                            LocalizedMessages.text("command.levelledmobs.debug.runtime.d026", mapOf("value-1" to (formula)), false)

                LocalizedMessages.text("command.levelledmobs.debug.runtime.d027", mapOf("value-1" to (evalResult.error), "value-2" to (msg)), false) }
            return null
        }

        val result = evalResult.result

        DebugManager.log(DebugType.CUSTOM_DROPS_FORMULA, lmEntity){
            val msg = if (formula == formulaPre)
                LocalizedMessages.text("command.levelledmobs.debug.runtime.d028", mapOf("value-1" to (formula)), false)
            else
                LocalizedMessages.text("command.levelledmobs.debug.runtime.d029", mapOf("value-1" to (formulaPre)), false) +
                        LocalizedMessages.text("command.levelledmobs.debug.runtime.d030", mapOf("value-1" to (formula)), false)

            LocalizedMessages.text("command.levelledmobs.debug.runtime.d031", mapOf("value-1" to (result), "value-2" to (msg)), false)}

        return result
    }
}
