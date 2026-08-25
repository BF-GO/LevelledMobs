package io.github.arcaneplugins.levelledmobs.util

/**
 * Это небольшой класс, полезный для определения времени простых вещей, таких как время, необходимое для запуска плагина или запуска команды.
 * <p>
 * Отметьте начальную точку таймера с помощью `QuickTimer timer = new QuickTimer()`, затем получите время (в миллисекундах).
 * с тех пор, как он начал использовать `QuickTimer#getTimer()`.
 *
 * @author lokka30
 * @see System#currentTimeMillis()
 * @since неизвестно
 */
class QuickTimer {
    private var startTime = 0L

    init {
        start()
    }

    /**
     * Перезапустите/запустите таймер.
     */
    fun start() {
        startTime = System.currentTimeMillis()
    }

    /**
     * @return время (в миллисекундах) с момента начала
     */
    val timer: Long
        get() = System.currentTimeMillis() - startTime
}
