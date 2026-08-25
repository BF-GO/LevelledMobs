package io.github.arcaneplugins.levelledmobs.result

/**
 * Содержит значения, используемые, когда требуется минимальное и максимальное значение.
 *
 * @author stumper66
 * @since 3.12.2
 */
class MinAndMaxHolder{
    constructor(
        min: Int,
        max: Int
    ){
        this.min = min.toFloat()
        this.max = max.toFloat()
    }

    constructor(
        min: Float,
        max: Float
    ){
        this.min = min
        this.max = max
        this.isUsingFloat = true
    }

    var min = 0f
    var max = 0f

    var minAsInt: Int
        get() = min.toInt()
        set(value) { this.min = value.toFloat() }

    var maxAsInt: Int
        get() = max.toInt()
        set(value) { this.max = value.toFloat() }

    var isUsingFloat: Boolean = false
        private set
    var useMin: Boolean = true

    fun ensureMinAndMax(float: Float, max: Float) {
        this.min.coerceAtMost(min)
        this.max.coerceAtMost(max)
    }

    fun ensureMinAndMax(min: Int, max: Int) {
        this.min.toInt().coerceAtMost(min)
        this.max.toInt().coerceAtMost(max)
    }

    override fun toString(): String {
        return "$min, $max"
    }
}