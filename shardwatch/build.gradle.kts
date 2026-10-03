import java.security.MessageDigest

plugins {
    java
}

group = "dev.shardwatch"
version = "1.0.0"
description = "Crystal staff suite: Flares, Verdicts, Rewind, Glint, Facets and Shardscope."

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.xerial:sqlite-jdbc:3.46.1.3")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

val packDir = layout.projectDirectory.dir("resourcepack")
val packOut = layout.buildDirectory.dir("pack")

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(21)
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name()
        val props = mapOf("version" to project.version, "description" to project.description)
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    test {
        useJUnitPlatform()
    }

    jar {
        archiveFileName.set("Shardwatch-${project.version}.jar")
    }

    // Deterministic zip of the resource pack, so its SHA-1 only changes when the content does.
    val packZip by registering(Zip::class) {
        group = "build"
        description = "Zips resourcepack/ and writes its SHA-1 next to it."
        from(packDir)
        archiveFileName.set("Shardwatch-pack.zip")
        destinationDirectory.set(packOut)
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        doLast {
            val zip = archiveFile.get().asFile
            val sha1 = MessageDigest.getInstance("SHA-1").digest(zip.readBytes())
                .joinToString("") { "%02x".format(it) }
            File(zip.parentFile, "Shardwatch-pack.zip.sha1").writeText(sha1 + "\n")
            println("Shardwatch-pack.zip SHA-1: $sha1")
        }
    }

    build {
        dependsOn(packZip)
    }
}
