plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.7.1"
}

group = "com.visualizetransaction"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Configure IntelliJ Platform Gradle Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        create("IU", "2025.2.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add necessary plugin dependencies for compilation here, example:
        bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
            untilBuild = "252.*"
        }

        changeNotes = """
            <h3>Version 1.0.0</h3>
            <ul>
                <li>Initial release</li>
                <li>Gutter icons for @Transactional methods with different icons for read-only transactions</li>
                <li>Detection of same-class method calls (AOP proxy bypass)</li>
                <li>Detection of invalid method modifiers (private/final/static)</li>
                <li>N+1 query detection in loops and streams</li>
                <li>Quick fixes for common issues</li>
                <li>Customizable settings UI</li>
                <li>Full support for both Java and Kotlin</li>
                <li>Compatible with IntelliJ IDEA 2024.2+</li>
            </ul>
        """.trimIndent()
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
