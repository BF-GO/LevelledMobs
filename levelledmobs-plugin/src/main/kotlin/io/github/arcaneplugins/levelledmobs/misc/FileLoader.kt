package io.github.arcaneplugins.levelledmobs.misc

import io.github.arcaneplugins.levelledmobs.commands.subcommands.RulesSubcommand
import io.github.arcaneplugins.levelledmobs.util.Log
import io.github.arcaneplugins.levelledmobs.util.LocalizedMessages
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import org.bukkit.util.FileUtil
import org.yaml.snakeyaml.Yaml

/**
 * Used to load various configuration files and migrate if necessary
 *
 * @author lokka30, stumper66
 * @since 2.4.0
 */
object FileLoader {
    const val SETTINGS_FILE_VERSION = 40 // Last changed: v4.5.3 b151
    const val MESSAGES_FILE_VERSION = 10 // Russian catalog and configurable runtime messages
    const val CUSTOMDROPS_FILE_VERSION = 12 // Last changed: v4.1.0 b44
    const val RULES_FILE_VERSION = 5 // Last changed: v4.0.0 b1
    const val EXTERNALPLUGINS_FILE_VERSION = 1 // Last changed: v4.0.0

    fun loadFile(
        plugin: Plugin,
        cfgName: String,
        compatibleVersion: Int
    ): YamlConfiguration? {
        var useCfgName = cfgName
        useCfgName += ".yml"

        Log.infKey("console.file-loader.loading-file", mapOf("file" to useCfgName))

        val file = File(plugin.dataFolder, useCfgName)
        val isMessages = useCfgName == "messages.yml"

        saveResourceIfNotExists(plugin, file)
        try {
            FileInputStream(file).use { fs ->
                Yaml().load<Any>(fs)
            }
        } catch (e: Exception) {
            if (isMessages) {
                val invalidBackup = File(plugin.dataFolder, "messages.yml.invalid.old")
                Files.copy(
                    file.toPath(), invalidBackup.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
                plugin.saveResource(file.name, true)
                val restored = YamlConfiguration.loadConfiguration(file)
                Log.sevKey(
                    "console.file-loader.invalid-messages-reset",
                    mapOf("backup" to invalidBackup.name, "error" to e.toString())
                )
                return restored
            }
            Log.sevKey(
                "console.file-loader.yaml-error",
                mapOf("file" to useCfgName, "error" to e.toString())
            )
            return null
        }

        var cfg = YamlConfiguration.loadConfiguration(file)
        cfg.options().copyDefaults(true)
        val ymlHelper = YmlParsingHelper(cfg)
        val fileVersion = ymlHelper.getInt( "file-version")
        val isCustomDrops = useCfgName == "customdrops.yml"
        val isRules = useCfgName == "rules.yml"

        if (fileVersion < compatibleVersion) {
            val backedupFile = File(
                plugin.dataFolder,
                "$useCfgName.v$fileVersion.old"
            )

            // copy to old file
            FileUtil.copy(file, backedupFile)
            Log.infKey(
                "console.file-loader.backup-created",
                mapOf("file" to useCfgName, "backup" to backedupFile.name)
            )

            if (isMessages) {
                val preservedFlags = listOf(
                    "other.compatibility-notice.enabled",
                    "other.update-notice.send-in-console",
                    "other.update-notice.send-on-join"
                ).associateWith { path -> cfg.getBoolean(path) }
                plugin.saveResource(file.name, true)
                cfg = YamlConfiguration.loadConfiguration(file)
                for ((path, value) in preservedFlags) cfg.set(path, value)
                cfg.save(file)
                Log.infKey(
                    "console.file-loader.messages-replaced",
                    mapOf("old-version" to fileVersion, "new-version" to compatibleVersion)
                )
                return cfg
            }

            // overwrite the file from new version
            if (!isRules) {
                plugin.saveResource(file.name, true)
            }

            // copy supported values from old file to new
            if (!isRules){
                Log.infKey(
                    "console.file-loader.migrating-file",
                    mapOf("file" to useCfgName)
                )
            }

            if (isCustomDrops)
                FileMigrator.copyCustomDrops(backedupFile, file, fileVersion)
            else if (!isRules)
                FileMigrator.copyYmlValues(backedupFile, file, fileVersion)
             else {
                Log.warKey("console.file-loader.rules-reset")
                RulesSubcommand.resetRules(null, RulesSubcommand.ResetDifficulty.SILVER)
            }

            // reload cfg from the updated values
            cfg = YamlConfiguration.loadConfiguration(file)
        }
        else if (!isRules)
            checkFileVersion(file, compatibleVersion, ymlHelper.getInt( "file-version"))

        return cfg
    }

    fun getFileLoadErrorMessage(): String {
        return LocalizedMessages.text("other.rules-file-load-error")
    }

    private fun saveResourceIfNotExists(
        instance: Plugin,
        file: File
    ) {
        if (!file.exists()) {
            Log.infKey(
                "console.file-loader.creating-file",
                mapOf("file" to file.name)
            )
            instance.saveResource(file.name, false)
        }
    }

    private fun checkFileVersion(
        file: File,
        compatibleVersion: Int,
        installedVersion: Int
    ) {
        if (compatibleVersion == installedVersion) return

        val statePath = if (installedVersion < compatibleVersion)
            "console.file-loader.version-outdated" else "console.file-loader.version-ahead"
        Log.sevKey(
            statePath,
            mapOf("file" to file.name)
        )
        Log.warKey(
            "console.file-loader.version-details",
            mapOf("installed" to installedVersion, "compatible" to compatibleVersion)
        )
    }
}
