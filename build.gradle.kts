plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.19.0"
    id("org.jetbrains.dokka") version "2.2.0"
}

group = "diche"
version = "1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        clion("2025.2")
    }
}

kotlin {
    jvmToolchain(21)
}

// Генерация документации: ./gradlew dokkaGeneratePublicationHtml
// Результат: build/dokka/html/index.html
dokka {
    dokkaPublications.html {
        moduleName.set("Session Timer")
        includes.from("docs/module.md")
    }
}

intellijPlatform {
    instrumentCode = false
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "252"
        }
    }
}
