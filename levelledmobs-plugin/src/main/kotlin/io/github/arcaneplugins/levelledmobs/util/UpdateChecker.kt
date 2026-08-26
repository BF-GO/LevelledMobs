package io.github.arcaneplugins.levelledmobs.util

import java.io.InputStream
import java.util.Scanner
import java.util.function.Consumer
import io.github.arcaneplugins.levelledmobs.wrappers.SchedulerWrapper
import java.io.FileNotFoundException
import java.net.URI
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

/**
 * Адаптированная версия средства проверки обновлений из Wiki SpigotMC.org.
 *
 * @author lokka30
 * @see UpdateChecker#getLatestVersion(Consumer)
 * @since 1.9
 */
class UpdateChecker(
    private var plugin: JavaPlugin,
    private var resourceName: String
) {

    /**
     * Благодарность редакторам [этой](вики-страницы https://www.spigotmc.org/wiki/creating-an-update-checker-that-checks-for-updates). (источник: 15 сентября 2020 г.)
     *
     * @param consumer что делать, если найден результат проверки обновлений
     * @since неизвестно
     */
    fun getLatestVersion(
        consumer: Consumer<String?>
    ) {
        val scheduler = SchedulerWrapper { checkVersion(consumer) }
        scheduler.run()
    }

    private fun checkVersion(
        consumer: Consumer<String?>
    ) {
        val inputStream: InputStream
        try {
            inputStream = URI ("https://hangar.papermc.io/api/v1/projects/$resourceName/latest?channel=Release")
                .toURL().openStream()
        }
        catch (e: FileNotFoundException) {
            Log.warKey("console.update.file-not-found", mapOf("error" to (e.message ?: "-")))
            return
        }
        catch (e: Exception) {
            Log.warKey("console.update.check-error", mapOf("error" to (e.message ?: "-")))
            return
        }

        val latestVersion = inputStream.use { stream ->
            Scanner(stream).use { scanner -> if (scanner.hasNext()) scanner.next() else null }
        }
        Bukkit.getGlobalRegionScheduler().execute(plugin) {
            consumer.accept(latestVersion)
        }
    }

    /**
     * @return строка версии из файла plugin.yml плагина, т. е. то, что пользователь запускает в данный момент.
     */
    @Suppress("DEPRECATION")
    val currentVersion: String
        get() = plugin.description.version
}
