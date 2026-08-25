package io.github.arcaneplugins.levelledmobs.rules.strategies

/**
 * Тип стратегии назначения уровня, которая будет использоваться
 *
 * @author stumper66
 * @since 4.0.0
 */
enum class StrategyType {
    RANDOM,
    WEIGHTED_RANDOM,
    RANDOM_VARIANCE,
    Y_COORDINATE,
    CUSTOM,
    SPAWN_DISTANCE,
    PLAYER_VARIABLE
}