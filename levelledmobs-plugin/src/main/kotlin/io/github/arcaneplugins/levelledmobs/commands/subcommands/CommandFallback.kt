package io.github.arcaneplugins.levelledmobs.commands.subcommands

import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.github.arcaneplugins.levelledmobs.util.Log
import io.github.arcaneplugins.levelledmobs.util.LocalizedMessages
import org.bukkit.command.Command
import org.bukkit.command.CommandSender

class CommandFallback(
    commandName: String
) : Command(commandName) {
    // они будут использоваться только в том случае, если CommandAPI не загружается, что обычно происходит только
    // если это неподдерживаемая версия Minecraft

    override fun execute(
        sender: CommandSender,
        label: String,
        args: Array<String>
    ): Boolean {
        val main = LevelledMobs.instance

        if (!sender.hasPermission("levelledmobs.command")) {
            main.configUtils.sendNoPermissionMsg(sender)
            return true
        }

        if (args.isEmpty()){
            LocalizedMessages.send(sender, "command.levelledmobs.fallback-options")
            return true
        }

        if ("reload".equals(args[0], ignoreCase = true)){
            main.reloadLM(sender)
        }
        else if ("show-errors".equals(args[0], ignoreCase = true))
            showErrors(sender, args)
        else if ("info".equals(args[0], ignoreCase = true))
            InfoSubcommand.showInfo(sender)

        return true
    }

    private fun showErrors(
        sender: CommandSender,
        args: Array<String>
    ){
        val doClear = args.size >= 2 && "clear".equals(args[1], ignoreCase = true)

        DebugSubcommand.showErrors(sender, doClear)
    }

    override fun tabComplete(
        sender: CommandSender,
        label: String,
        args: Array<String>
    ): MutableList<String> {
        if (!sender.hasPermission("levelledmobs.command")) {
            LevelledMobs.instance.configUtils.sendNoPermissionMsg(sender)
            return mutableListOf()
        }

        return when (args.size) {
            1 -> mutableListOf("reload", "info", "show-errors")
            2 if "show-errors".equals(args[0], ignoreCase = true) -> mutableListOf("clear")
            else -> mutableListOf()
        }
    }
}
