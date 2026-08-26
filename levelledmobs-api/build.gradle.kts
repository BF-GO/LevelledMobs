group = "io.github.arcaneplugins"
version = "1.0"
description = "LevelledMobs API"

plugins {
    id("java")
}

dependencies {
    compileOnly(project(":levelledmobs-plugin"))
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}
