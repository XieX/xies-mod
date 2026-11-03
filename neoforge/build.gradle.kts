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
        // And its resources: the content schema lives there, and XieParser copies
        // it out of the classpath into <gamedir>/xiecontent/ on every launch.
        resources.srcDir(layout.projectDirectory.dir("../common/src/main/resources"))
    }
}

neoForge {
    version = property("neo_version") as String

    runs {
        register("client") {
            client()
            // Nothing random-ticks and no components can be inspected without a
            // player, so verification drives a client into the dedicated server and
            // asserts over rcon. -Pxie.join=<host> is how that client gets there;
            // --args cannot supply it, because it replaces the launcher's own.
            (findProperty("xie.join") as String?)?.let {
                programArguments.addAll("--quickPlayMultiplayer", it)
            }
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

// The content in base-content/ is bundled into the jar, so one download
// is the whole mod and a fresh install has something to play. It goes under
// bundled/base-content/ with an index beside it, because a jar cannot be listed
// as a directory from the classpath; BundledContent reads the index and extracts
// the files into <gamedir>/xiecontent/base-content/ on the first launch that finds
// none there. Files and directories starting with "_" are the content's own
// work-in-progress convention and the parser would skip them anyway, so they are
// left out of the jar rather than shipped dead.
val contentRepo = layout.projectDirectory.dir("../base-content")
val bundledRoot = "bundled"
val bundledPack = "base-content"
// The content's files point "$schema" at the schema in common/, which is not beside
// them in the jar. Extracted, they sit under xiecontent/base-content/, and the schema the
// mod installs is at the top of xiecontent/, so each reference is rewritten to climb out
// to it.
val schemaRef = Regex("\"[^\"]*common/src/main/resources/xie-content\\.schema\\.json\"")

tasks.named<ProcessResources>("processResources") {
    val replacements = templateProperties
    inputs.properties(replacements)
    from("src/main/templates") {
        expand(replacements)
    }

    if (contentRepo.asFile.isDirectory) {
        from(contentRepo) {
            into("$bundledRoot/$bundledPack")
            exclude("**/_*", "**/_*/**", ".git/**", ".gitignore", "**/.DS_Store", "**/Thumbs.db")
        }
    } else {
        logger.warn("No content at ${contentRepo.asFile}; the jar will carry no bundled content")
    }

    val version = templateProperties["mod_version"].toString()
    val root = bundledRoot
    val pack = bundledPack
    val schema = schemaRef
    doLast {
        val packDir = destinationDir.resolve(root).resolve(pack)
        if (!packDir.isDirectory) {
            return@doLast
        }
        // Done here rather than with a copy filter, which would rewrite every line ending
        // to the build machine's.
        packDir.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .forEach { file ->
                val depth = file.relativeTo(packDir).invariantSeparatorsPath.count { it == '/' } + 1
                val target = "\"" + "../".repeat(depth) + "xie-content.schema.json\""
                val text = file.readText(Charsets.UTF_8)
                val rewritten = text.replace(schema) { target }
                if (rewritten != text) {
                    file.writeText(rewritten, Charsets.UTF_8)
                }
            }
        val index = packDir.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(packDir).invariantSeparatorsPath }
            .sorted()
            .joinToString("\n", postfix = "\n")
        destinationDir.resolve(root).resolve("$pack.index").writeText(index)
        destinationDir.resolve(root).resolve("$pack.version").writeText(version + "\n")
    }
}
