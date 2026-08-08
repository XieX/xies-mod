plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

dependencies {
    // Gson only. Nothing in this module may import net.minecraft.* — that is the
    // whole point of it, and it is what keeps the parser testable in isolation.
    api("com.google.code.gson:gson:${property("gson_version")}")

    testImplementation(platform("org.junit:junit-bom:${property("junit_version")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    // Lets RealContentTest find the sibling content repo. Override with
    //   ./gradlew :common:test -Dxie.content.dir=/some/other/pack
    systemProperty(
        "xie.content.dir",
        providers.systemProperty("xie.content.dir")
            .orElse(layout.projectDirectory.dir("../../mod-content").asFile.absolutePath)
            .get()
    )
}
