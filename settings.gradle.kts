/*
 * Этот файл создан задачей Gradle 'init'.
 *
 * Проект использует экспериментальные API с аннотацией @Incubating, которые могут измениться.
 */

rootProject.name = "LevelledMobs-Parent"

include(":levelledmobs-plugin")
include(":levelledmobs-api")

plugins{
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
