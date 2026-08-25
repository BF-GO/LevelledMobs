@file:Suppress("DEPRECATION")

package io.github.arcaneplugins.levelledmobs.util

import java.util.regex.Pattern
import net.md_5.bungee.api.ChatColor
import org.bukkit.Bukkit

/**
 * Этот класс содержит набор методов, которые
 * упростить перевод цвета на основе '&'
 * коды в сообщениях. Вы можете раскрасить стандартные коды
 * (&a, &b, &1, &2 и т. д.) и даже шестнадцатеричные коды (&#abccdef),
 * и то и другое в одном методе :)
 *
 * @author lokka30, Sullivan_Bognar, imDaniX
 * @since 1.0.0
 */
@SuppressWarnings("deprecation")
object MessageUtils {
    /**
     * Раскрасьте сообщение, используя цветовые коды «&», например: '&a' для ChatColor.GREEN.
     * Если сервер версии 1.16 или новее, он также будет переводить шестнадцатеричные коды - например. '&#abcdef'.
     *
     * @param msg сообщение, из которого нужно перевести цветовые коды.
     * @return сообщение после преобразования цветовых кодов.
     * @author lokka30
     * @see MessageUtils#colorizeHexCodes(String)
     * @see MessageUtils#colorizeStandardCodes(String)
     * @since неизвестно
     */
    fun colorizeAll(
        msg: String?
    ): String {
        if (msg == null) return ""
        return colorizeStandardCodes(colorizeHexCodes(msg))
    }

    fun removeColorCodes(msg: String?): String {
        if (msg == null) return "" +
                ""
        val sb = StringBuilder()
        var foundCode = false
        for (char in msg.toCharArray()){
            if (foundCode){
                foundCode = false
                continue
            }
            if (char == '&' || char == '§'){
                foundCode = true
                continue
            }

            sb.append(char)
        }

        return sb.toString()
    }

    /**
     * По умолчанию для startTag установлено значение «&#», а для endTag — значение «» (ничего) — colorizeHexCodes.
     *
     * @param message сообщение для перевода
     * @return переведенная строка
     * @author lokka30
     * @see MessageUtils#colorizeHexCodes(String, String, String)
     * @since неизвестно
     */
    private fun colorizeHexCodes(
        message: String
    ): String {
        val startTag = "&#"
        val endTag = ""

        val hexPattern = Pattern.compile("$startTag([A-Fa-f0-9]{6})$endTag")
        val matcher = hexPattern.matcher(message)
        val buffer = StringBuilder(message.length + 4 * 8)
        val colorChar = ChatColor.COLOR_CHAR

        while (matcher.find()) {
            val group = matcher.group(1)
            matcher.appendReplacement(
                buffer, colorChar.toString() + "x"
                        + colorChar + group[0] + colorChar + group[1]
                        + colorChar + group[2] + colorChar + group[3]
                        + colorChar + group[4] + colorChar + group[5]
            )
        }
        return matcher.appendTail(buffer).toString()
    }

    /**
     * Преобразует только стандартные цветовые коды, не затрагивая шестнадцатеричные.
     * Стандартные коды имеют префикс «&», например `&a`.
     *
     * @author lokka30
     *
     * @param msg сообщение, из которого нужно перевести стандартные цветовые коды.
     * @return сообщение после преобразования цветовых кодов.
     *
     * @since неизвестно
     */
    fun colorizeStandardCodes(msg: String?): String {
        return if (Bukkit.getName()
                .equals("CraftBukkit", ignoreCase = true)
        ) org.bukkit.ChatColor.translateAlternateColorCodes(
            '&',
            msg!!
        )
        else ChatColor.translateAlternateColorCodes('&', msg)
    }
}
