plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.12.0"
}

group = "com.visualizetransaction"
version = "1.0.6"

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

    // Spring Framework annotations for tests
    testImplementation("org.springframework:spring-tx:6.1.3")
    testImplementation("org.springframework:spring-context:6.1.3")

    // JPA annotations for tests
    testImplementation("jakarta.persistence:jakarta.persistence-api:3.1.0")
    testImplementation("jakarta.transaction:jakarta.transaction-api:2.0.1")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
            untilBuild = "261.*"
        }

        changeNotes = """
            <h3>Version 1.0.6 - Build Toolchain Update</h3>
            <ul>
                <li><b>Build:</b> Upgraded to Gradle 9.4 and IntelliJ Platform Gradle Plugin 2.12.0 to fix plugin archive extraction issue on IntelliJ IDEA 2026.1 (build 261.*)</li>
            </ul>

            <h3>Version 1.0.5 - IDE Compatibility Update</h3>
            <ul>
                <li><b>IDE Compatibility:</b> Extended support to IntelliJ IDEA 2026.1 (build 261.*)</li>
            </ul>

            <h3>Version 1.0.4 - Improved Same-Class Call Detection</h3>
            <ul>
                <li><b>Context-Aware Warnings:</b> Same-class @Transactional method call inspection now differentiates between scenarios:
                    <ul>
                        <li><b>INFO level:</b> When caller has @Transactional and no special propagation - annotation is redundant but joins existing transaction (expected behavior)</li>
                        <li><b>WARNING level:</b> When called method has special propagation (REQUIRES_NEW, MANDATORY, etc.) - won't work as expected</li>
                        <li><b>WARNING level:</b> When caller has no @Transactional - annotation will be completely ignored</li>
                    </ul>
                </li>
                <li><b>Better Developer Experience:</b> Reduces false positives and helps developers understand when same-class calls are acceptable vs. problematic</li>
            </ul>

            <h3>Version 1.0.3 - Jakarta EE Support</h3>
            <ul>
                <li><b>Jakarta Transaction Support:</b> Now detects jakarta.transaction.Transactional and javax.transaction.Transactional</li>
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
