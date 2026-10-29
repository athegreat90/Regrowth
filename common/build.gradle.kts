plugins { kotlin("jvm") }
kotlin { jvmToolchain(25) }
dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test { useJUnitPlatform() }
