package io.github.arcaneplugins.levelledmobs.rules

import java.util.concurrent.ThreadLocalRandom
import kotlin.math.max
import io.github.arcaneplugins.levelledmobs.util.LocalizedMessages

/**
 * Содержит настройки, относящиеся к пользовательскому
 * функция сообщений о смерти
 *
 * @author stumper66
 * @since 3.7.0
 */
class DeathMessages {
    private val messages = mutableListOf<String>()
    val isEnabled: Boolean = false

    fun addEntry(weight: Int, message: String) {
        val number = max(1.0, weight.toDouble()).toInt()
        repeat(
            number,
            action = { messages.add(message) }
        )
    }

    fun getDeathMessage(): String? {
        if (messages.isEmpty()) return null

        val useArray = ThreadLocalRandom.current().nextInt(messages.size)
        return messages[useArray]
    }

    val isEmpty: Boolean
        get() = messages.isEmpty()

    override fun toString(): String {
        if (!this.isEnabled) return LocalizedMessages.text(
            "display.rules.death-messages-disabled",
            colorize = false
        )
        if (this.isEmpty) return LocalizedMessages.text(
            "display.rules.death-messages-empty",
            colorize = false
        )

        return LocalizedMessages.text(
            "display.rules.death-messages-count",
            mapOf("count" to messages.size),
            false
        )
    }
}
