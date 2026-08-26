package io.github.arcaneplugins.levelledmobs.wrappers

import io.papermc.paper.threadedregions.scheduler.ScheduledTask

/** Result of a task scheduled through Paper's region scheduler API. */
class SchedulerResult {
    private var task: ScheduledTask? = null

    constructor(task: ScheduledTask?) {
        this.task = task
    }

    fun cancelTask() {
        task?.cancel()
    }

    fun isCancelled(): Boolean = task?.isCancelled ?: false
}
