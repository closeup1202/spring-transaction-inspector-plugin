package com.visualizetransaction.inspections

import com.visualizetransaction.settings.TransactionInspectorSettings

class SwallowedExceptionInspectionTest : BaseInspectionTest() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(SwallowedExceptionInspection::class.java)
        TransactionInspectorSettings.getInstance(project).state.enableSwallowedExceptionDetection = true
    }

    fun testDetectSwallowedException() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;

            class OrderService {
                @Transactional
                public void process() {
                    try {
                        doWork();
                    } catch (Exception e) {
                        // swallowed -> transaction still commits
                        System.out.println("failed");
                    }
                }

                void doWork() throws Exception {}
            }
        """.trimIndent()

        myFixture.configureByText("OrderService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any { it.description?.contains("caught inside a @Transactional") == true }) {
            "Expected warning for swallowed exception. Found: ${highlights.map { it.description }}"
        }
    }

    fun testNoWarningForClassLevelTransactionalOnly() {
        // Class-level @Transactional is intentionally NOT flagged, to avoid noise on
        // unrelated methods of a transactional service. Only method-level @Transactional counts.
        val code = """
            import org.springframework.transaction.annotation.Transactional;

            @Transactional
            class OrderService {
                public void process() {
                    try {
                        doWork();
                    } catch (Exception e) {
                        log(e);
                    }
                }

                void doWork() throws Exception {}
                void log(Exception e) {}
            }
        """.trimIndent()

        myFixture.configureByText("OrderService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none { it.description?.contains("caught inside a @Transactional") == true }) {
            "Class-level @Transactional should not be flagged (method-level scoping). Found: ${highlights.map { it.description }}"
        }
    }

    fun testNoWarningWhenReThrown() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;

            class OrderService {
                @Transactional
                public void process() {
                    try {
                        doWork();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                void doWork() throws Exception {}
            }
        """.trimIndent()

        myFixture.configureByText("OrderService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none { it.description?.contains("caught inside a @Transactional") == true }) {
            "Should not warn when the exception is re-thrown"
        }
    }

    fun testNoWarningWhenSetRollbackOnly() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.transaction.interceptor.TransactionAspectSupport;

            class OrderService {
                @Transactional
                public void process() {
                    try {
                        doWork();
                    } catch (Exception e) {
                        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                    }
                }

                void doWork() throws Exception {}
            }
        """.trimIndent()

        myFixture.addFileToProject("org/springframework/transaction/interceptor/TransactionAspectSupport.java", """
            package org.springframework.transaction.interceptor;
            public abstract class TransactionAspectSupport {
                public static TransactionStatus currentTransactionStatus() { return null; }
                public interface TransactionStatus { void setRollbackOnly(); }
            }
        """.trimIndent())

        myFixture.configureByText("OrderService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none { it.description?.contains("caught inside a @Transactional") == true }) {
            "Should not warn when setRollbackOnly() is called"
        }
    }

    fun testNoWarningOutsideTransactional() {
        val code = """
            class OrderService {
                public void process() {
                    try {
                        doWork();
                    } catch (Exception e) {
                        // not transactional -> not our concern
                    }
                }

                void doWork() throws Exception {}
            }
        """.trimIndent()

        myFixture.configureByText("OrderService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none { it.description?.contains("caught inside a @Transactional") == true }) {
            "Should not warn for non-transactional methods"
        }
    }
}
