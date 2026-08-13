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
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:23.0.0")
    implementation("io.github.revxrsal:lamp.common:4.0.0-rc.17")
    implementation("io.github.revxrsal:lamp.bukkit:4.0.0-rc.17")
    implementation("io.github.revxrsal:lamp.brigadier:4.0.0-rc.17")
    implementation("com.github.lidanthedev:triumph-gui:3.1.14") {
        exclude(group = "com.google.code.gson", module = "gson")
    }
    implementation("com.github.cryptomorin:XSeries:13.7.0")
    compileOnly("com.github.lidanthedev:CaveCrawlers:v2.0.0")
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

tasks.shadowJar {
    archiveClassifier.set("")
    val packageName = project.name.decapitalize()
    relocate("dev.triumphteam.gui", "me.lidan.${packageName}.gui")
    relocate("com.cryptomorin.xseries", "me.lidan.${packageName}.xseries")
}

tasks.jar {
    enabled = false
}

tasks.build {
    dependsOn(tasks.named("shadowJar"))
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
