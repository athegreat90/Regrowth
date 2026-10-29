plugins { kotlin("jvm"); id("net.neoforged.moddev") }
kotlin { jvmToolchain(25) }
base { archivesName.set("regrowth-kotlin-neoforge-26.1.2") }
neoForge {
    version = "26.1.2.125"
    runs {
        create("server") { server(); programArgument("--nogui") }
        create("client") { client() }
        create("gameTestServer") {
            type = "gameTestServer"
            gameDirectory = project.file("run")
            programArguments.addAll("--tests", "regrowth:*", "--report", "regrowth-gametest.xml")
            systemProperty("regrowth.gametest", "true")
            systemProperty("neoforge.enabledGameTestNamespaces", "regrowth")
        }
    }
    mods { create("regrowth") { sourceSet(sourceSets.main.get()); sourceSet(project(":common").sourceSets.main.get()) } }
    unitTest { enable(); testedMod = mods.getByName("regrowth") }
}
sourceSets.main {
    kotlin.srcDir(rootProject.file("src/main/kotlin"))
    resources.srcDir(rootProject.file("src/main/resources"))
}
dependencies {
    implementation(project(":common"))
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("dev.nyon:KotlinLangForge:2.16.0-k2.4.21-3.1+neoforge")
}
tasks.processResources {
    inputs.property("minecraft", "26.1.2")
    inputs.property("neo", "26.1.2.125")
    inputs.property("version", project.version)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand("minecraft" to "26.1.2", "neo" to "26.1.2.125", "version" to project.version)
    }
}
tasks.jar {
    from(project(":common").sourceSets.main.get().output)
    from(rootProject.file("LICENSE"))
    from(rootProject.file("THIRD_PARTY_NOTICES.md"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

sourceSets.test { kotlin.srcDir(rootProject.file("src/test/kotlin")) }
tasks.test { useJUnitPlatform() }
