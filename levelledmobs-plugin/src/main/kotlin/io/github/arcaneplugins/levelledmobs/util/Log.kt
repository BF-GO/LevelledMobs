package io.github.arcaneplugins.levelledmobs.util

import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.MainCompanion
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit

/**
 * Пишет сообщения в консоль
 *
 * @author lokka30, stumper66
 * @since 4.0
 */
object Log {
    private const val PREFIX = "&b[LevelledMobs]&7 "
    // используйте эту функцию для тестирования сообщений, чтобы не забыть удалить их позже
    @Deprecated("Удалить перед выпуском", ReplaceWith("inf(msg)", "io.github.arcaneplugins.levelledmobs.util.Log.inf"))
    fun infTemp(msg: String?) {
        inf(msg)
    }

    fun inf(msg: String?) {
        if (LevelledMobs.instance.ver.isRunningPaper)
            sendMessagePaper(msg)
        else
            Bukkit.getServer().consoleSender.sendMessage(MessageUtils.colorizeAll(PREFIX + msg))
    }

    fun infKey(path: String, replacements: Map<String, Any?> = emptyMap()) {
        inf(LocalizedMessages.text(path, replacements, colorize = false))
    }

    fun war(msg: String, recordError: Boolean = true) {
        if (LevelledMobs.instance.ver.isRunningPaper)
            sendMessagePaper(
                LocalizedMessages.text("console.log.warning-prefix", colorize = false) + msg
            )
        else
            Bukkit.getServer().consoleSender.sendMessage(MessageUtils.colorizeAll(PREFIX + msg))

        if (recordError && !MainCompanion.instance.errorMessages.contains(msg))
            MainCompanion.instance.errorMessages += msg
    }

    fun warKey(
        path: String,
        replacements: Map<String, Any?> = emptyMap(),
        recordError: Boolean = true
    ) {
        war(LocalizedMessages.text(path, replacements, colorize = false), recordError)
    }

    fun sev(msg: String) {
        if (LevelledMobs.instance.ver.isRunningPaper)
            sendMessagePaper(
                LocalizedMessages.text("console.log.severe-prefix", colorize = false) + msg
            )
        else
            Bukkit.getServer().consoleSender.sendMessage(MessageUtils.colorizeAll(PREFIX + msg))

        if (!MainCompanion.instance.errorMessages.contains(msg))
            MainCompanion.instance.errorMessages += msg
    }

    fun sevKey(path: String, replacements: Map<String, Any?> = emptyMap()) {
        sev(LocalizedMessages.text(path, replacements, colorize = false))
    }

    private fun sendMessagePaper(msg: String?){
        if (msg == null) return

        val serializer = LegacyComponentSerializer.legacyAmpersand()
        val msgComp = serializer.deserialize("$PREFIX$msg")
        Bukkit.getServer().consoleSender.sendMessage { msgComp }
    }
}
