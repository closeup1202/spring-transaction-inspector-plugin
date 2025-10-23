plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.7.1"
}

group = "com.visualizetransaction"
version = "1.0.2"

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
            <h3>Version 1.0.2 - Enhanced Detection</h3>
            <ul>
                <li><b>New Inspection:</b>
                    <ul>
                        <li>Transaction Propagation Conflict Detection (MANDATORY/NEVER/REQUIRES_NEW)</li>
                        <li>Detects runtime exceptions before they happen</li>
                        <li>Warns about data inconsistency risks with REQUIRES_NEW</li>
                    </ul>
                </li>
                <li><b>N+1 Query Detection Improvements:</b>
                    <ul>
                        <li>Now detects @ManyToOne(fetch = LAZY) relationships</li>
                        <li>Now detects @OneToOne(fetch = LAZY) relationships</li>
                        <li>More accurate detection based on fetch type defaults</li>
                    </ul>
                </li>
                <li><b>Total Inspections:</b> 8 comprehensive checks for Spring transaction anti-patterns</li>
            </ul>

            <h3>Version 1.0.1 - Major Improvements</h3>
            <ul>
                <li><b>Code Quality:</b> Fixed memory leak, refactored duplicate code</li>
                <li><b>Accuracy:</b> Type-based detection (95%+ accuracy, 60-80% fewer false positives)</li>
                <li><b>Features:</b> Settings toggles, repository caching, intention preview support</li>
            </ul>

            <h3>Version 1.0.0 - Initial Release</h3>
            <ul>
                <li>7 inspections for @Transactional anti-patterns</li>
                <li>Gutter icons, Quick Fixes, Customizable settings</li>
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
