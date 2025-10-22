package com.visualizetransaction.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.visualizetransaction.settings.TransactionInspectorSettings

class ReadOnlyTransactionWriteCallInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(ReadOnlyTransactionWriteCallInspection::class.java)

        val settings = TransactionInspectorSettings.getInstance(project)
        settings.state.enableReadOnlyWriteCallDetection = true
    }

    fun testDetectWriteCallFromReadOnly() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.stereotype.Service;

            @Service
            class UserService {
                @Transactional(readOnly = true)
                public void viewUserData() {
                    updateStats();  // Problem!
                }

                @Transactional  // REQUIRED is default
                public void updateStats() {
                    // write operations
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any {
            it.description?.contains("write-capable") == true &&
                    it.description?.contains("readOnly") == true &&
                    it.description?.contains("REQUIRES_NEW") == true
        }) {
            "Expected warning for calling write method from readOnly. Found: ${highlights.map { it.description }}"
        }
    }

    fun testDetectWriteCallWithExplicitRequired() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.transaction.annotation.Propagation;

            class UserService {
                @Transactional(readOnly = true)
                public void viewData() {
                    saveData();  // Problem!
                }

                @Transactional(propagation = Propagation.REQUIRED)
                public void saveData() {
                    // write operations
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any {
            it.description?.contains("write-capable") == true
        }) {
            "Expected warning for REQUIRED propagation from readOnly"
        }
    }

    fun testNoWarningForRequiresNew() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.transaction.annotation.Propagation;

            class UserService {
                @Transactional(readOnly = true)
                public void viewData() {
                    saveData();  // OK - REQUIRES_NEW
                }

                @Transactional(propagation = Propagation.REQUIRES_NEW)
                public void saveData() {
                    // write operations in new transaction
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none {
            it.description?.contains("write-capable") == true
        }) {
            "Should not warn for REQUIRES_NEW propagation"
        }
    }

    fun testNoWarningForReadOnlyCallee() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;

            class UserService {
                @Transactional(readOnly = true)
                public void viewData() {
                    getStats();  // OK - also readOnly
                }

                @Transactional(readOnly = true)
                public void getStats() {
                    // read operations
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none {
            it.description?.contains("write-capable") == true
        }) {
            "Should not warn when callee is also readOnly"
        }
    }

    fun testNoWarningForNonTransactionalCaller() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;

            class UserService {
                public void viewData() {  // Not @Transactional
                    updateStats();  // OK
                }

                @Transactional
                public void updateStats() {
                    // write operations
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none {
            it.description?.contains("readOnly") == true
        }) {
            "Should not warn when caller is not transactional"
        }
    }

    fun testNoWarningForWriteCaller() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;

            class UserService {
                @Transactional  // Not readOnly
                public void processData() {
                    updateStats();  // OK - both write-capable
                }

                @Transactional
                public void updateStats() {
                    // write operations
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none {
            it.description?.contains("readOnly") == true
        }) {
            "Should not warn when caller is write-capable"
        }
    }

    fun testDetectSupportspropagation() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.transaction.annotation.Propagation;

            class UserService {
                @Transactional(readOnly = true)
                public void viewData() {
                    processData();  // Problem - SUPPORTS joins existing
                }

                @Transactional(propagation = Propagation.SUPPORTS)
                public void processData() {
                    // May join readOnly transaction
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any {
            it.description?.contains("write-capable") == true
        }) {
            "Expected warning for SUPPORTS propagation"
        }
    }

    fun testQuickFix_ChangePropagation() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;

            class UserService {
                @Transactional(readOnly = true)
                public void viewData() {
                    updateStats();
                }

                @Transactional
                public void updateStats() {
                    // write operations
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        myFixture.doHighlighting()

        val intentions = myFixture.availableIntentions
        val fix = intentions.find { it.familyName == "Change propagation to REQUIRES_NEW" }
        assertNotNull("Should have quick fix to change propagation", fix)

        fix?.invoke(project, myFixture.editor, myFixture.file)

        myFixture.checkResult("""
            import org.springframework.transaction.annotation.Transactional;

            class UserService {
                @Transactional(readOnly = true)
                public void viewData() {
                    updateStats();
                }

                @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
                public void updateStats() {
                    // write operations
                }
            }
        """.trimIndent())
    }

    fun testQuickFix_WithExistingAttributes() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;

            class UserService {
                @Transactional(readOnly = true)
                public void viewData() {
                    updateStats();
                }

                @Transactional(timeout = 30)
                public void updateStats() {
                    // write operations
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        myFixture.doHighlighting()

        val intentions = myFixture.availableIntentions
        val fix = intentions.find { it.familyName == "Change propagation to REQUIRES_NEW" }
        assertNotNull("Should have quick fix", fix)

        fix?.invoke(project, myFixture.editor, myFixture.file)

        myFixture.checkResult("""
            import org.springframework.transaction.annotation.Transactional;

            class UserService {
                @Transactional(readOnly = true)
                public void viewData() {
                    updateStats();
                }

                @Transactional(timeout = 30, propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
                public void updateStats() {
                    // write operations
                }
            }
        """.trimIndent())
    }
}
