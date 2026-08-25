package io.github.arcaneplugins.levelledmobs.commands.subcommands

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.misc.FileLoader
import io.github.arcaneplugins.levelledmobs.util.LocalizedMessages
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player


/**
 * Перезагружает всю конфигурацию LevelledMobs с диска.
 *
 * @author lokka30
 * @since 2.0
 */
object ReloadSubcommand : CommandBase("levelledmobs.command.reload") {
    override val description = LocalizedMessages.text("command.descriptions.reload", colorize = false)

    fun buildCommand() : LiteralCommandNode<CommandSourceStack>{
        return createLiteralCommand("reload")
            .executes { ctx ->
                val main = LevelledMobs.instance
                val sender = ctx.source.sender
                main.reloadLM(sender)

                if (main.mainCompanion.hadRulesLoadError && sender is Player)
                    sender.sendMessage(FileLoader.getFileLoadErrorMessage())

                return@executes Command.SINGLE_SUCCESS
            }
            .build()
    }
}
