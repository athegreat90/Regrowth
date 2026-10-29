plugins {
    kotlin("jvm") version "2.4.21" apply false
    id("net.neoforged.moddev") version "2.0.148" apply false
}
allprojects {
    group = "com.mactso.regrowth"
    version = "0.1.0-dev"
    repositories { mavenCentral(); maven("https://repo.nyon.dev/releases") }
}
