package io.github.arcaneplugins.levelledmobs.commands

import io.github.arcaneplugins.levelledmobs.util.LocalizedMessages
import org.bukkit.command.CommandSender

object MessagesHelper {
    fun showMessage(
        sender: CommandSender,
        path: String
    ) {
        LocalizedMessages.send(sender, path)
    }

    fun showMessage(
        sender: CommandSender,
        path: String,
        replaceWhat: String,
        replaceWith: String
    ) {
        return showMessage(
            sender,
            path,
            mutableListOf(replaceWhat),
            mutableListOf(replaceWith)
        )
    }

    fun showMessage(
        sender: CommandSender,
        path: String,
        replaceWhat: MutableList<String>,
        replaceWith: MutableList<String>
    ) {
        LocalizedMessages.send(sender, path, replacements(replaceWhat, replaceWith))
    }

    fun getMessage(
        path: String
    ): String {
        return LocalizedMessages.text(path, mapOf("label" to ""))
    }

    fun getMessage(
        path: String,
        replaceWhat: String,
        replaceWith: String
    ): MutableList<String> {
        return getMessage(
            path,
            mutableListOf(replaceWhat),
            mutableListOf(replaceWith)
        )
    }

    fun getMessage(
        path: String,
        replaceWhat: MutableList<String>,
        replaceWith: MutableList<String>
    ): MutableList<String> {
        val values = replacements(replaceWhat, replaceWith).toMutableMap()
        values["label"] = "/lm"
        return LocalizedMessages.lines(path, values)
    }

    private fun replacements(
        replaceWhat: List<String>,
        replaceWith: List<String>
    ): Map<String, String> {
        require(replaceWhat.size == replaceWith.size) {
            "replaceWhat must be the same size as replaceWith"
        }
        return replaceWhat.indices.associate { replaceWhat[it] to replaceWith[it] }
    }
}
