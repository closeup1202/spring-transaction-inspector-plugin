package com.visualizetransaction.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Test suite for InvalidTransactionalMethodInspection
 *
 * Tests detection of @Transactional on methods with invalid modifiers
 */
class InvalidTransactionalMethodInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(InvalidTransactionalMethodInspection::class.java)
    }

    override fun getTestDataPath(): String {
        return "src/test/resources"
    }

    fun testDetectPrivateTransactionalMethod() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.stereotype.Service;

            @Service
            public class UserService {
                @Transactional
                private void updateUser(User user) {  // ❌ private @Transactional - AOP cannot intercept
                    // implementation
                }
            }

            class User {}
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any { it.description?.contains("private") == true && it.description?.contains("@Transactional") == true }) {
            "Expected warning for private @Transactional method not found"
        }
    }

    fun testDetectFinalTransactionalMethod() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.stereotype.Service;

            @Service
            public class UserService {
                @Transactional
                public final void updateUser(User user) {  // ❌ final @Transactional - AOP cannot intercept
                    // implementation
                }
            }

            class User {}
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any { it.description?.contains("final") == true || it.description?.contains("@Transactional") == true }) {
            "Expected warning for final @Transactional method not found"
        }
    }

    fun testDetectStaticTransactionalMethod() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;

            public class UserService {
                @Transactional
                public static void updateUser(User user) {  // ❌ static @Transactional - AOP cannot intercept
                    // implementation
                }
            }

            class User {}
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any { it.description?.contains("static") == true && it.description?.contains("@Transactional") == true }) {
            "Expected warning for static @Transactional method not found"
        }
    }

    fun testNoWarningForPublicTransactionalMethod() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.stereotype.Service;

            @Service
            public class UserService {
                @Transactional
                public void updateUser(User user) {  // ✓ No warning - public, non-final, non-static
                    // implementation
                }
            }

            class User {}
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none {
            it.description?.contains("private") == true || it.description?.contains("final") == true || it.description?.contains(
                "static"
            ) == true
        }) {
            "Unexpected warning for valid public @Transactional method"
        }
    }

    fun testNoWarningForNonTransactionalPrivateMethod() {
        val code = """
            import org.springframework.stereotype.Service;

            @Service
            public class UserService {
                private void helperMethod() {  // ✓ No warning - private but not @Transactional
                    // implementation
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none { it.description?.contains("@Transactional") == true }) {
            "Unexpected warning for non-transactional method"
        }
    }
}
