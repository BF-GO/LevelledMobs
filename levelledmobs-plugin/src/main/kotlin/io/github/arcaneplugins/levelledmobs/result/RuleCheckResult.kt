package io.github.arcaneplugins.levelledmobs.result

/**
 * Сохраняет результаты правил, чтобы сохранить состояние случайного правила.
 *
 * @author stumper66
 * @since 3.1.2
 */
class RuleCheckResult {
    var useResult = false
        private set
    var ruleMadeChance: Boolean? = null
        private set

    constructor(useResult: Boolean){
        this.useResult = useResult
    }

    constructor(useResult: Boolean, ruleMadeChance: Boolean?){
        this.useResult = useResult
        this.ruleMadeChance = ruleMadeChance
    }
}