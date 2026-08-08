pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases") { name = "NeoForged" }
    }
}

// ModDevGradle runs some of its tooling (downloadAssets) on a Java 21 toolchain
// regardless of what the mod itself targets, so the build needs both 21 and 25
// available. This lets Gradle fetch whichever JDK a task asks for instead of
// requiring every contributor to install them by hand.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "xies-mod"

// :common holds the loader-agnostic content model and parser. It has no Minecraft
// dependency at all, so it compiles and tests in about a second without a game
// instance. :neoforge compiles the same sources into the mod jar (see its build file).
include("common")
include("neoforge")
