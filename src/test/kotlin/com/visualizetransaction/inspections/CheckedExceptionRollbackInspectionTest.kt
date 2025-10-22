package com.visualizetransaction.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.visualizetransaction.settings.TransactionInspectorSettings

class CheckedExceptionRollbackInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CheckedExceptionRollbackInspection::class.java)

        // Enable the setting for this inspection
        val settings = TransactionInspectorSettings.getInstance(project)
        settings.state.enableCheckedExceptionRollbackDetection = true
    }

    fun testMethodThrowsIOExceptionWithoutRollbackFor() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import java.io.IOException;

            class TestService {
                @Transactional
                public void processFile() throws IOException {
                    // some code
                }
            }
        """.trimIndent()

        myFixture.configureByText("TestService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any {
            it.description?.contains("IOException") == true &&
                    it.description?.contains("rollbackFor") == true &&
                    it.description?.contains("@Transactional") == true
        }) {
            "Expected warning for @Transactional method throwing IOException without rollbackFor. Found: ${highlights.map { it.description }}"
        }
    }

    fun testMethodThrowsExceptionWithoutRollbackFor() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;

            class TestService {
                @Transactional
                public void process() throws Exception {
                    // some code
                }
            }
        """.trimIndent()

        myFixture.configureByText("TestService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any {
            it.description?.contains("Exception") == true &&
                    it.description?.contains("rollbackFor") == true
        }) {
            "Expected warning for @Transactional method throwing Exception without rollbackFor"
        }
    }

    fun testMethodThrowsRuntimeException_NoWarning() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;

            class TestService {
                @Transactional
                public void process() throws IllegalArgumentException {
                    // RuntimeException - automatically rolls back, no warning needed
                }
            }
        """.trimIndent()

        myFixture.configureByText("TestService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none {
            it.description?.contains("rollbackFor") == true
        }) {
            "Should not have warning for RuntimeException"
        }
    }

    fun testMethodWithRollbackFor_NoWarning() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import java.io.IOException;

            class TestService {
                @Transactional(rollbackFor = Exception.class)
                public void processFile() throws IOException {
                    // rollbackFor specified, no warning needed
                }
            }
        """.trimIndent()

        myFixture.configureByText("TestService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none {
            it.description?.contains("doesn't specify 'rollbackFor'") == true
        }) {
            "Should not have warning when rollbackFor is already specified"
        }
    }

    fun testNonTransactionalMethod_NoWarning() {
        val code = """
            import java.io.IOException;

            class TestService {
                public void processFile() throws IOException {
                    // Not transactional, no warning
                }
            }
        """.trimIndent()

        myFixture.configureByText("TestService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none {
            it.description?.contains("@Transactional") == true
        }) {
            "Should not have warning for non-transactional method"
        }
    }

    fun testMethodWithNoExceptions_NoWarning() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;

            class TestService {
                @Transactional
                public void process() {
                    // No exceptions declared, no warning
                }
            }
        """.trimIndent()

        myFixture.configureByText("TestService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none {
            it.description?.contains("throws checked exception") == true
        }) {
            "Should not have warning when no exceptions are thrown"
        }
    }

    fun testQuickFix_AddSpecificException() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import java.io.IOException;

            class TestService {
                @Transactional
                public void processFile() throws IOException {
                    // some code
                }
            }
        """.trimIndent()

        myFixture.configureByText("TestService.java", code)
        myFixture.doHighlighting()

        val intentions = myFixture.availableIntentions
        val specificFix = intentions.find { it.familyName == "Add rollbackFor = IOException.class" }
        assertNotNull("Should have quick fix for specific exception. Available: ${intentions.map { it.familyName }}", specificFix)

        specificFix?.invoke(project, myFixture.editor, myFixture.file)

        myFixture.checkResult(
            """
            import org.springframework.transaction.annotation.Transactional;
            import java.io.IOException;

            class TestService {
                @Transactional(rollbackFor = IOException.class)
                public void processFile() throws IOException {
                    // some code
                }
            }
            """.trimIndent()
        )
    }

    fun testQuickFix_AddExceptionClass() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import java.io.IOException;

            class TestService {
                @Transactional
                public void processFile() throws IOException {
                    // some code
                }
            }
        """.trimIndent()

        myFixture.configureByText("TestService.java", code)
        myFixture.doHighlighting()

        val intentions = myFixture.availableIntentions
        val genericFix = intentions.find { it.familyName == "Add rollbackFor = Exception.class" }
        assertNotNull("Should have quick fix for Exception.class. Available: ${intentions.map { it.familyName }}", genericFix)

        genericFix?.invoke(project, myFixture.editor, myFixture.file)

        myFixture.checkResult(
            """
            import org.springframework.transaction.annotation.Transactional;
            import java.io.IOException;

            class TestService {
                @Transactional(rollbackFor = Exception.class)
                public void processFile() throws IOException {
                    // some code
                }
            }
            """.trimIndent()
        )
    }

    fun testQuickFix_MultipleExceptions() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import java.io.IOException;
            import java.sql.SQLException;

            class TestService {
                @Transactional
                public void processFile() throws IOException, SQLException {
                    // some code
                }
            }
        """.trimIndent()

        myFixture.configureByText("TestService.java", code)
        myFixture.doHighlighting()

        val intentions = myFixture.availableIntentions
        val specificFix = intentions.find {
            it.familyName.contains("IOException.class") && it.familyName.contains("SQLException.class")
        }
        assertNotNull("Should have quick fix for multiple specific exceptions. Available: ${intentions.map { it.familyName }}", specificFix)

        specificFix?.invoke(project, myFixture.editor, myFixture.file)

        myFixture.checkResult(
            """
            import org.springframework.transaction.annotation.Transactional;
            import java.io.IOException;
            import java.sql.SQLException;

            class TestService {
                @Transactional(rollbackFor = {IOException.class, SQLException.class})
                public void processFile() throws IOException, SQLException {
                    // some code
                }
            }
            """.trimIndent()
        )
    }

    fun testQuickFix_WithExistingAttributes() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import java.io.IOException;

            class TestService {
                @Transactional(readOnly = false)
                public void processFile() throws IOException {
                    // some code
                }
            }
        """.trimIndent()

        myFixture.configureByText("TestService.java", code)
        myFixture.doHighlighting()

        val intentions = myFixture.availableIntentions
        val specificFix = intentions.find { it.familyName == "Add rollbackFor = IOException.class" }
        assertNotNull("Should have quick fix for specific exception. Available: ${intentions.map { it.familyName }}", specificFix)

        specificFix?.invoke(project, myFixture.editor, myFixture.file)

        myFixture.checkResult(
            """
            import org.springframework.transaction.annotation.Transactional;
            import java.io.IOException;

            class TestService {
                @Transactional(readOnly = false, rollbackFor = IOException.class)
                public void processFile() throws IOException {
                    // some code
                }
            }
            """.trimIndent()
        )
    }
}
