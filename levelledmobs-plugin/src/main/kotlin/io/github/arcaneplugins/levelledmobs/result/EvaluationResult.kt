package io.github.arcaneplugins.levelledmobs.result

/**
 * Содержит результаты оценки формулы.
 *
 * @author stumper66
 * @since 4.0
 */
data class EvaluationResult(
    val result: Double,
    val error: String?
){
    val hadError: Boolean
        get() = this.error != null
}