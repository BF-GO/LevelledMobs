package io.github.arcaneplugins.levelledmobs.util

import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin

/**
 * Разрешает весь настраиваемый текст LevelledMobs из messages.yml.
 *
 * Связанный файл загружается во время onLoad, поэтому загрузочная диагностика может быть показана до
 * доступна записываемая конфигурация. После загрузки messages.yml он становится активным.
 * source и прилагаемый каталог остаются запасным вариантом на случай отсутствия ключей.
 */
object LocalizedMessages {
    private const val BUNDLED_RESOURCE = "messages.yml"
    private const val EMERGENCY_PREFIX = "&b[LevelledMobs]&7 "

    private var bundled = YamlConfiguration()
    @Volatile
    private var active: YamlConfiguration? = null

    fun initialize(plugin: JavaPlugin) {
        val resource = plugin.getResource(BUNDLED_RESOURCE) ?: return
        resource.use { stream ->
            InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                bundled = YamlConfiguration.loadConfiguration(reader)
            }
        }
    }

    fun activate(configuration: YamlConfiguration) {
        active = configuration
    }

    fun text(
        path: String,
        replacements: Map<String, Any?> = emptyMap(),
        colorize: Boolean = true
    ): String = lines(path, replacements, colorize).joinToString("\n")

    fun lines(
        path: String,
        replacements: Map<String, Any?> = emptyMap(),
        colorize: Boolean = true
    ): MutableList<String> {
        val resolved = readLines(active, path)
            .ifEmpty { readLines(bundled, path) }
            .ifEmpty { mutableListOf("&c[LevelledMobs] Отсутствует сообщение: &f$path") }

        val allReplacements = LinkedHashMap<String, Any?>(replacements.size + 1)
        allReplacements["prefix"] = prefix(colorize = false)
        allReplacements.putAll(replacements)

        return resolved.mapTo(mutableListOf()) { original ->
            var result = original
            for ((name, value) in allReplacements) {
                val token = if (name.startsWith('%') && name.endsWith('%')) name else "%$name%"
                result = result.replace(token, value?.toString().orEmpty())
            }
            if (colorize) MessageUtils.colorizeAll(result) else result
        }
    }

    fun send(
        sender: CommandSender,
        path: String,
        replacements: Map<String, Any?> = emptyMap()
    ) {
        val label = if (sender is ConsoleCommandSender) "lm" else "/lm"
        val values = LinkedHashMap<String, Any?>(replacements.size + 1)
        values["label"] = label
        values.putAll(replacements)
        sender.sendMessage(text(path, values))
    }

    fun prefix(colorize: Boolean = true): String {
        val configured = readLines(active, "common.prefix")
            .ifEmpty { readLines(bundled, "common.prefix") }
            .firstOrNull()
            ?: EMERGENCY_PREFIX
        return if (colorize) MessageUtils.colorizeAll(configured) else configured
    }

    private fun readLines(configuration: YamlConfiguration?, path: String): MutableList<String> {
        if (configuration == null || !configuration.contains(path)) return mutableListOf()
        if (configuration.isList(path)) return configuration.getStringList(path)
        return configuration.getString(path)?.let { mutableListOf(it) } ?: mutableListOf()
    }
}
