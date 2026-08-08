plugins {
    `java-library`
    id("net.neoforged.moddev") version "2.0.143"
}

repositories {
    mavenCentral()
}

val modId = property("mod_id") as String

version = property("mod_version") as String
group = property("mod_group_id") as String

// Otherwise the jar is named after the Gradle module ("neoforge-0.4.0.jar").
base.archivesName = modId

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

// Compile :common's sources straight into the mod jar rather than depending on
// :common as a project. ModDevGradle does not bundle project dependencies into
// the jar, so this avoids having to shade or jarJar anything — the classes just
// end up in the mod. :common stays a real module so it keeps its own fast tests.
sourceSets {
    named("main") {
        java.srcDir(layout.projectDirectory.dir("../common/src/main/java"))
    }
}

neoForge {
    version = property("neo_version") as String

    runs {
        register("client") {
            client()
        }
        register("server") {
            server()
            programArgument("--nogui")
        }
        register("data") {
            clientData()
            programArguments.addAll(
                "--mod", modId,
                "--all",
                "--output", file("src/generated/resources/").absolutePath,
                "--existing", file("src/main/resources/").absolutePath
            )
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets["main"])
        }
    }
}

dependencies {
    // Minecraft already ships Gson, so we compile against it but do not ship it.
    compileOnly("com.google.code.gson:gson:${property("gson_version")}")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

// Mod metadata lives in src/main/templates and has its ${...} placeholders filled
// in here. Kept out of src/main/resources so that expansion cannot corrupt an
// asset that legitimately contains a dollar sign.
// Resolved here rather than inside the task block: inside it, `property(...)`
// resolves against the task rather than the project.
val templateProperties = mapOf(
    "minecraft_version" to property("minecraft_version"),
    "minecraft_version_range" to property("minecraft_version_range"),
    "neo_version" to property("neo_version"),
    "mod_id" to property("mod_id"),
    "mod_name" to property("mod_name"),
    "mod_license" to property("mod_license"),
    "mod_version" to property("mod_version"),
    "mod_authors" to property("mod_authors"),
    "mod_description" to property("mod_description")
)

tasks.named<ProcessResources>("processResources") {
    val replacements = templateProperties
    inputs.properties(replacements)
    from("src/main/templates") {
        expand(replacements)
    }
}
