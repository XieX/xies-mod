// Deliberately near-empty. Gradle 9 pushes towards project isolation, so each
// subproject configures itself rather than being configured from here via
// `subprojects {}`.

tasks.register("printVersions") {
    group = "help"
    description = "Prints the platform versions this build targets."
    val mc = providers.gradleProperty("minecraft_version")
    val neo = providers.gradleProperty("neo_version")
    doLast {
        println("Minecraft: ${mc.get()}")
        println("NeoForge:  ${neo.get()}")
    }
}
