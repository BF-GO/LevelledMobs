package io.github.arcaneplugins.levelledmobs.customdrops

enum class GroupSelectionMode(val configValue: String) {
    SEQUENTIAL("sequential"),
    RANDOM_SINGLE("random-single");

    companion object {
        fun fromConfig(value: String?): GroupSelectionMode? {
            if (value.isNullOrBlank()) return SEQUENTIAL

            val normalized = value.trim().lowercase().replace('_', '-')
            return entries.firstOrNull { it.configValue == normalized }
        }
    }
}
