pluginManagement {
    repositories { gradlePluginPortal(); maven("https://maven.neoforged.net/releases") }
}
rootProject.name = "regrowth-kotlin-neoforge"
include("common", "mc2612", "mc262", "mc263")
