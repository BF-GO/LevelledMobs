package io.github.arcaneplugins.levelledmobs.commands

import io.github.arcaneplugins.levelledmobs.util.LocalizedMessages
import org.bukkit.command.CommandSender

/**
 * Предоставляет общие функции для показа пользователю сообщений, хранящихся в messages.yml.
 *
 * @author stumper66
 * @since 3.3.0
 */
open class MessagesBase{
    protected var messageLabel = "lm"
    protected var commandSender: CommandSender? = null

    protected fun showMessage(path: String) {
        if (commandSender == null) {
            throw NullPointerException(
                LocalizedMessages.text("console.internal.command-sender-not-set", colorize = false)
            )
        }

        showMessage(path, commandSender!!, messageLabel)
    }

    companion object{
        @JvmStatic
        protected fun showMessage(
            path: String,
            sender: CommandSender,
            messageLabel: String
        ) {
            LocalizedMessages.send(sender, path, mapOf("label" to messageLabel))
        }

        @JvmStatic
        protected fun getMessage(
            path: String,
            replaceWhat: MutableList<String>,
            replaceWith: MutableList<String>
        ): MutableList<String> {
            require(replaceWhat.size == replaceWith.size) {
                LocalizedMessages.text(
                    "console.internal.replacement-list-size-mismatch",
                    colorize = false
                )
            }
            val replacements = replaceWhat.indices.associate { replaceWhat[it] to replaceWith[it] }
                .toMutableMap()
            replacements["label"] = ""
            return LocalizedMessages.lines(path, replacements)
        }
    }

    protected fun showMessage(
        path: String,
        replaceWhat: String,
        replaceWith: String
    ) {
        showMessage(
            path,
            mutableListOf(replaceWhat),
            mutableListOf(replaceWith)
        )
    }

    protected fun showMessage(
        path: String,
        replaceWhat: MutableList<String>,
        replaceWith: MutableList<String>
    ) {
        if (commandSender == null)
            throw NullPointerException(
                LocalizedMessages.text("console.internal.command-sender-not-set", colorize = false)
            )

        val messages = getMessage(path, replaceWhat, replaceWith)
        commandSender!!.sendMessage(messages.joinToString("\n"))
    }

    protected fun showMessage(
        path: String,
        replaceWhat: MutableList<String>,
        replaceWith: MutableList<String>,
        sender: CommandSender
    ) {
        val messages = getMessage(path, replaceWhat, replaceWith)
        sender.sendMessage(messages.joinToString("\n"))
    }
}
