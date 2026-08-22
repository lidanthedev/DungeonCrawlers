plugins {
    java
    `maven-publish`
    id("com.gradleup.shadow") version "8.3.6"
    id("io.freefair.lombok") version "8.11"
}

group = "me.lidan"
version = "1.0"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
    maven("https://mvn.lumine.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.essentialsx.net/releases/")
    maven("https://repo.alessiodp.com/releases/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly(libs.paper.api)
    compileOnly(libs.cave.crawlers)
    compileOnly(libs.worldedit.bukkit)
    compileOnly(libs.fawe.core)
    compileOnly(libs.mythic.mobs)
    compileOnly(libs.vault.api)
    compileOnly(libs.essentials)
    compileOnly(libs.parties.api)
    compileOnly(libs.gson)

    compileOnly(libs.lamp.common)
    compileOnly(libs.lamp.bukkit)
    compileOnly(libs.lamp.brigadier)
    implementation(libs.triumph.gui) {
        exclude(group = "com.google.code.gson", module = "gson")
    }
    implementation(libs.xseries)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockbukkit) {
        // MockBukkit publishes its JUnit extension API as a runtime dependency;
        // keep the project's JUnit BOM authoritative for the test engine.
        exclude(group = "org.junit.jupiter", module = "junit-jupiter-api")
    }
    testImplementation(libs.lamp.common)
    testImplementation(libs.paper.api)
    testImplementation(libs.cave.crawlers)
    testImplementation(libs.parties.api)
    testImplementation(libs.worldedit.bukkit)
    testImplementation(libs.gson)
}

val targetJavaVersion = 21

java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
}

tasks.compileJava {
    // Preserve parameter names in the bytecode.
    options.compilerArgs.add("-parameters")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"

    if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
        options.release.set(targetJavaVersion)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveClassifier.set("")
    val packageName = project.name.replaceFirstChar { it.lowercase() }
    relocate("dev.triumphteam.gui", "me.lidan.${packageName}.gui")
    relocate("com.cryptomorin.xseries", "me.lidan.${packageName}.xseries")
}

tasks.jar {
    enabled = false
}

tasks.build {
    dependsOn(tasks.named("shadowJar"))
    dependsOn(tasks.named("verifyNoExternalPluginShading"))
}

val verifyNoExternalPluginShading by tasks.registering {
    dependsOn(tasks.shadowJar)
    doLast {
        val forbiddenPrefixes = listOf(
            "me/lidan/cavecrawlers/",
            "io/lumine/mythic/",
            "com/sk89q/worldedit/",
            "net/milkbowl/vault/",
            "net/ess3/",
            "net/essentialsx/",
            "com/alessiodp/parties/",
            "com/google/gson/"
        )
        zipTree(tasks.shadowJar.get().archiveFile).matching {
            forbiddenPrefixes.forEach { include("$it**") }
        }.files.takeIf { it.isNotEmpty() }?.let { shaded ->
            throw GradleException("External plugin API classes were shaded: ${shaded.take(10)}")
        }
    }
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
