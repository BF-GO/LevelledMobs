package io.github.arcaneplugins.levelledmobs.wrappers

import io.github.arcaneplugins.levelledmobs.LevelledMobs
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity

/**
 * Routes work through the schedulers shared by modern Paper and Folia.
 *
 * Entity and location work always runs on its owning region. Work without an
 * owner is treated as CPU/I/O work and uses the async scheduler. Callers may
 * opt into direct execution only when they are already inside the correct
 * event/scheduler context.
 */
class SchedulerWrapper {
    var runnable: Runnable? = null
    var entity: Entity? = null

    constructor(runnable: Runnable) {
        this.runnable = runnable
    }

    constructor(entity: Entity?) {
        this.entity = entity
    }

    constructor(entity: Entity?, runnable: Runnable) {
        this.entity = entity
        this.runnable = runnable
    }

    var locationForRegionScheduler: Location? = null
    var runDirectlyInFolia: Boolean = false
    var runDirectlyInBukkit: Boolean = false
    private val main = LevelledMobs.instance

    fun runAsync() {
        if (entity != null || locationForRegionScheduler != null)
            run()
        else
            Bukkit.getAsyncScheduler().runNow(main) { runnable!!.run() }
    }

    fun run(@Suppress("UNUSED_PARAMETER") doRunAsync: Boolean = false) {
        if (willRunDirectly) {
            runnable!!.run()
            return
        }

        val ownedEntity = entity
        if (ownedEntity != null) {
            ownedEntity.scheduler.run(main, Consumer { runnable!!.run() }, null)
            return
        }

        val location = locationForRegionScheduler
        if (location != null) {
            Bukkit.getRegionScheduler().run(main, location) { runnable!!.run() }
            return
        }

        Bukkit.getAsyncScheduler().runNow(main) { runnable!!.run() }
    }

    fun runGlobal(): SchedulerResult {
        val task = Bukkit.getGlobalRegionScheduler().run(main) { runnable!!.run() }
        return SchedulerResult(task)
    }

    fun runGlobalDelayed(delayInTicks: Long): SchedulerResult {
        val task = Bukkit.getGlobalRegionScheduler().runDelayed(
            main,
            Consumer { runnable!!.run() },
            delayInTicks.coerceAtLeast(1L)
        )
        return SchedulerResult(task)
    }

    fun runTaskTimerGlobal(initialDelayTicks: Long, repeatPeriodTicks: Long): SchedulerResult {
        val task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
            main,
            Consumer { runnable!!.run() },
            initialDelayTicks.coerceAtLeast(1L),
            repeatPeriodTicks.coerceAtLeast(1L)
        )
        return SchedulerResult(task)
    }

    fun runTaskTimerAsynchronously(
        initialDelayMS: Long,
        repeatPeriodMS: Long
    ): SchedulerResult {
        val task = Bukkit.getAsyncScheduler().runAtFixedRate(
            main,
            Consumer { _: ScheduledTask -> runnable!!.run() },
            initialDelayMS,
            repeatPeriodMS,
            TimeUnit.MILLISECONDS
        )
        return SchedulerResult(task)
    }

    fun runDelayed(delayInTicks: Long): SchedulerResult {
        val ownedEntity = entity
        if (ownedEntity != null) {
            val task = ownedEntity.scheduler.runDelayed(
                main,
                Consumer { runnable!!.run() },
                null,
                delayInTicks.coerceAtLeast(1L)
            )
            return SchedulerResult(task)
        }

        val location = locationForRegionScheduler
        if (location != null) {
            val task = Bukkit.getRegionScheduler().runDelayed(
                main,
                location,
                Consumer { runnable!!.run() },
                delayInTicks.coerceAtLeast(1L)
            )
            return SchedulerResult(task)
        }

        val task = Bukkit.getAsyncScheduler().runDelayed(
            main,
            Consumer { runnable!!.run() },
            delayInTicks.coerceAtLeast(1L) * 50L,
            TimeUnit.MILLISECONDS
        )
        return SchedulerResult(task)
    }

    val willRunDirectly: Boolean
        get() = runDirectlyInFolia || runDirectlyInBukkit
}
