package io.github.arcaneplugins.levelledmobs.debug

/**
 * Содержит перечисления, используемые для отображения отладочных данных.
 *
 * @author lokka30, stumper66
 * @since 2.5.0
 */
enum class DebugType {
    /**
     * Записывается, когда LM проверяет, можно ли применить к мобу уровень и был ли он
     * успешный
     */
    APPLY_LEVEL_RESULT,

    /**
     * Записывается, когда LM анализирует моба, который появляется на сервере.
     */
    ENTITY_SPAWN,

    /**
     * Записывается, когда LM регулирует величину урона в дальнем бою от снарядов и стражей до
     * события (для них в Minecraft нет атрибутов)
     */
    RANGED_DAMAGE_MODIFICATION,

    CREEPER_BLAST_RADIUS,

    /**
     * Записывается, когда LM обрабатывает прирученный объект, для чего может потребоваться повторное назначение уровня.
     */
    ENTITY_TAME,

    /**
     * Записывается, когда LM регулирует количество дропа, создаваемых мобом.
     */
    SET_LEVELLED_ITEM_DROPS,

    SET_LEVELLED_XP_DROPS,

    /**
     * Различные события, связанные с сущностью
     */
    ENTITY_MISC,

    /**
     * Когда выполняются пользовательские команды
     */
    CUSTOM_COMMANDS,

    /**
     * При применении NBT к мобу
     */
    NBT_APPLICATION,

    /**
     * Записывается, когда LM обрабатывает моба из генератора существ.
     */
    LM_MOB_SPAWNER,

    CONDITION_ENTITIES_LIST,

    CONDITION_MINLEVEL,

    CONDITION_MAXLEVEL,

    CONDITION_WORLD_LIST,

    CONDITION_BIOME_LIST,

    CONDITION_PLUGIN_COMPAT,

    CONDITION_SPAWN_REASON,

    CONDITION_CUSTOM_NAME,

    CONDITION_CHANCE,

    CONDITION_WG_REGION,

    CONDITION_WG_REGION_OWNER,

    CONDITION_Y_LEVEL,

    CONDITION_MIN_SPAWN_DISTANCE,
    CONDITION_MAX_SPAWN_DISTANCE,

    SETTING_STOP_PROCESSING,

    PLAYER_LEVELLING,

    CONDITION_MYTHICMOBS_INTERNAL_NAME,

    CONDITION_SPAWNER_NAME,

    CONDITION_WORLD_TIME_TICK,

    CONDITION_PERMISSION,

    CONDITION_GAMEMODE,

    CONDITION_PLAYER_NAMES,

    CONDITION_STRUCTURES,

    APPLY_MULTIPLIERS,

    APPLY_BASE_MODIFIERS,

    CUSTOM_DROPS,

    CUSTOM_EQUIPS,

    MOB_GROUPS,

    GROUP_LIMITS,

    THREAD_LOCKS,

    SCOREBOARD_TAGS,

    SKYLIGHT_LEVEL,

    CHUNK_KILL_COUNT,

    SETTING_COOLDOWN,

    REMOVED_MULTIPLIERS,

    CONDITION_WITH_COORDINATES,

    MOB_HASH,

    DEVELOPER_LEW_CACHE,

    CUSTOM_STRATEGY,

    CONSTRUCT_LEVEL,

    STRATEGY_RESULT,

    RANDOM_NUMBER,

    LEVEL_RATIO,

    ENCHANTMENT_CHANCES,

    CUSTOM_DROPS_FORMULA,

    PLAYER_CONTEXT
}
