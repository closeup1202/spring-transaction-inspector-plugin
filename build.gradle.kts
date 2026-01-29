plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.7.1"
}

group = "com.visualizetransaction"
version = "1.0.3"

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
            untilBuild = "253.*"
        }

        changeNotes = """
            <h3>Version 1.0.3 - Jakarta EE Support</h3>
            <ul>
                <li><b>Jakarta Transaction Support:</b> Now detects jakarta.transaction.Transactional and javax.transaction.Transactional
                    <ul>
                        <li>AOP Proxy Bypass Detection</li>
                        <li>Invalid Method Modifiers (private/final/static)</li>
                        <li>@Async + @Transactional Conflicts</li>
                        <li>Gutter Icons</li>
                        <li>Transaction Info Action</li>
                    </ul>
                </li>
                <li><b>IDE Compatibility:</b> Extended support to IntelliJ IDEA 2025.3 (build 253.*)</li>
            </ul>

            <h3>Version 1.0.2 - Enhanced Detection</h3>
            <ul>
                <li><b>New Inspection:</b> Transaction Propagation Conflict Detection (MANDATORY/NEVER/REQUIRES_NEW)</li>
                <li><b>N+1 Query Detection Improvements:</b> @ManyToOne/@OneToOne(fetch = LAZY) support</li>
            </ul>

            <h3>Version 1.0.1 - Major Improvements</h3>
            <ul>
                <li>Fixed memory leak, type-based detection, settings toggles</li>
            </ul>

            <h3>Version 1.0.0 - Initial Release</h3>
            <ul>
                <li>7 inspections for @Transactional anti-patterns</li>
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
