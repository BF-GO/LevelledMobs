package io.github.arcaneplugins.levelledmobs.wrappers

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import io.github.arcaneplugins.levelledmobs.LevelledMobs
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.scheduler.BukkitTask

/**
 * Этот класс используется, когда код необходимо выполнить в контексте определенного потока.
 * Это позволяет серверам Folia выполнять код в правильном планировщике, пока
 * обеспечение совместимости с серверами Paper/Spigot без необходимости использования
 * разные методы для каждого типа сервера
 *
 * @author stumper66
 * @since 3.11.0
 */
class SchedulerWrapper {
    var runnable: Runnable? = null
    var entity: Entity? = null
    var bukkitTask: BukkitTask? = null
        private set

    constructor(runnable: Runnable){
        this.runnable = runnable
    }

    constructor(entity: Entity?){
        this.entity = entity
    }

    constructor(entity: Entity?, runnable: Runnable){
        this.entity = entity
        this.runnable = runnable
    }

    var locationForRegionScheduler: Location? = null
    var runDirectlyInFolia: Boolean = false
    var runDirectlyInBukkit: Boolean = false
    val main = LevelledMobs.instance

    fun runAsync() {
        run(true)
    }

    fun run(doRunAsync: Boolean = false) {
        if (main.ver.isRunningFolia) {
            if (runDirectlyInFolia) {
                runnable!!.run()
                return
            }

            val task = Consumer { _: ScheduledTask -> runnable!!.run() }

            if (entity != null)
                entity!!.scheduler.run(main, task, null)
            else {
                if (locationForRegionScheduler != null)
                    Bukkit.getRegionScheduler().run(main, locationForRegionScheduler!!, task)
                else
                    Bukkit.getAsyncScheduler().runNow(main, task)
            }
        } else {
            if (runDirectlyInBukkit) {
                runnable!!.run()
                return
            }

            // если вы предоставили объект в конструкторе, предполагается, что необходимо использовать основной поток
            // поскольку асинхронный доступ к объектам обычно приводит к ошибке
            bukkitTask = if (entity != null && !doRunAsync)
                Bukkit.getScheduler().runTask(main, runnable!!)
            else
                Bukkit.getScheduler().runTaskAsynchronously(main, runnable!!)
        }
    }

    fun runTaskTimerAsynchronously(
        initialDelayMS: Long,
        repeatPeriodMS: Long
    ): SchedulerResult {
        if (main.ver.isRunningFolia) {
            val task = Consumer { _: ScheduledTask -> runnable!!.run() }
            val scheduledTask = Bukkit.getAsyncScheduler().runAtFixedRate(
                main, task, initialDelayMS, repeatPeriodMS, TimeUnit.MILLISECONDS
            )

            return SchedulerResult(scheduledTask)
        } else {
            // конвертировать миллисекунды в приблизительные тики
            // 1 тик = ~ 50мс
            val convertedDelay = initialDelayMS / 50L
            val convertedPeriod = repeatPeriodMS / 50L
            val bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                main, runnable!!, convertedDelay, convertedPeriod
            )

            return SchedulerResult(bukkitTask)
        }
    }

    fun runDelayed(
        delayInTicks: Long
    ): SchedulerResult {
        if (main.ver.isRunningFolia) {
            val task = Consumer { _: ScheduledTask? -> runnable!!.run() }
            val scheduledTask: ScheduledTask?
            if (this.entity != null)
                scheduledTask = entity!!.scheduler.runDelayed(main, task, null, delayInTicks)
            else {
                val milliseconds = delayInTicks * 50L
                scheduledTask = Bukkit.getAsyncScheduler().runDelayed(
                    main, task, milliseconds, TimeUnit.MILLISECONDS
                )
            }

            return SchedulerResult(scheduledTask)
        } else {
            val bukkitTask = Bukkit.getScheduler().runTaskLater(
                main,
                runnable!!, delayInTicks
            )

            return SchedulerResult(bukkitTask)
        }
    }

    val willRunDirectly: Boolean
        get() {
            return if (main.ver.isRunningFolia)
                runDirectlyInFolia
            else
                runDirectlyInBukkit
        }
}