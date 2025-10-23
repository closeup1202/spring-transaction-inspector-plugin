plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.7.1"
}

group = "com.visualizetransaction"
version = "1.0.1"

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

    // JUnit 4 for BasePlatformTestCase (which extends junit.framework.TestCase)
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
            untilBuild = "252.*"
        }

        changeNotes = """
            <h3>Version 1.0.1 - Major Improvements</h3>
            <ul>
                <li><b>Code Quality Improvements:</b>
                    <ul>
                        <li>Fixed memory leak in AddRollbackForFix (FixRegistry cleanup)</li>
                        <li>Refactored duplicate code - extracted common utilities to PsiUtils</li>
                    </ul>
                </li>
                <li><b>Accuracy Enhancements:</b>
                    <ul>
                        <li>Type-based write operation detection (95%+ accuracy, reduced false positives by 60-80%)</li>
                        <li>Smart detection of Spring Data Repository, JPA EntityManager, and @Repository classes</li>
                        <li>Support for MongoDB, R2DBC, and Reactive repositories</li>
                    </ul>
                </li>
                <li><b>New Features:</b>
                    <ul>
                        <li>Added settings toggle for ReadOnly transactional inspection</li>
                        <li>Performance optimization with repository class caching</li>
                        <li>Intention preview support with @SafeFieldForPreview</li>
                    </ul>
                </li>
                <li><b>Consistency:</b> All inspections now have enable/disable settings for better control</li>
            </ul>

            <h3>Version 1.0.0 - Initial Release</h3>
            <ul>
                <li>7 comprehensive inspections for Spring @Transactional anti-patterns</li>
                <li>Gutter icons with different icons for read-only transactions</li>
                <li>Smart Quick Fixes for common issues</li>
                <li>Customizable settings UI</li>
            </ul>
        """.trimIndent()
    }

    buildSearchableOptions = false
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

    publishPlugin {
        token.set(System.getenv("JETBRAINS_TOKEN"))
    }

    buildPlugin {
        archiveBaseName.set("spring-transaction-inspector")
        archiveVersion.set(project.version.toString())
    }

    test {
        systemProperty("java.awt.headless", "true")
        systemProperty("file.encoding", "UTF-8")
        testLogging {
            events("passed", "failed", "skipped")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}
