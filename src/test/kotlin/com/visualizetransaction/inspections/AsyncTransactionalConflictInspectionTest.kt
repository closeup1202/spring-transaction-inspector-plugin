package com.visualizetransaction.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.visualizetransaction.settings.TransactionInspectorSettings

class AsyncTransactionalConflictInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(AsyncTransactionalConflictInspection::class.java)
        val settings = TransactionInspectorSettings.getInstance(project)
        settings.state.enableAsyncTransactionalDetection = true
    }

    fun testDetectAsyncWithTransactional() {
        val code = """
            import org.springframework.scheduling.annotation.Async;
            import org.springframework.transaction.annotation.Transactional;

            class UserService {
                @Async
                @Transactional
                public void processAsync() {
                    // Transaction won't propagate to async thread!
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any {
            it.description?.contains("@Async") == true &&
                    it.description?.contains("@Transactional") == true &&
                    it.description?.contains("NOT propagate") == true
        }) {
            "Expected warning for @Async + @Transactional conflict. Found: ${highlights.map { it.description }}"
        }
    }

    fun testDetectLazyLoadingInAsync() {
        val code = """
            import org.springframework.scheduling.annotation.Async;
            import javax.persistence.Entity;
            import javax.persistence.OneToMany;
            import javax.persistence.FetchType;
            import java.util.List;

            @Entity
            class User {
                @OneToMany(fetch = FetchType.LAZY)
                private List<Post> posts;

                public List<Post> getPosts() {
                    return posts;
                }
            }

            class Post {}

            class UserService {
                @Async
                public void processUserPosts(User user) {
                    int count = user.getPosts().size();  // LazyInitializationException!
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any {
            it.description?.contains("LazyInitializationException") == true &&
                    it.description?.contains("@Async") == true
        }) {
            "Expected warning for lazy loading in @Async method. Found: ${highlights.map { it.description }}"
        }
    }

    fun testDetectSameClassAsyncCall() {
        val code = """
            import org.springframework.scheduling.annotation.Async;
            import org.springframework.stereotype.Service;

            @Service
            class UserService {
                public void createUser() {
                    processAsync();  // AOP proxy bypass!
                }

                @Async
                public void processAsync() {
                    // Won't run async!
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any {
            it.description?.contains("same class") == true &&
                    it.description?.contains("@Async") == true &&
                    it.description?.contains("bypassed") == true
        }) {
            "Expected warning for same-class @Async call. Found: ${highlights.map { it.description }}"
        }
    }

    fun testNoWarningForCorrectAsyncUsage() {
        val code = """
            import org.springframework.scheduling.annotation.Async;

            class UserService {
                @Async
                public void processAsync(String data) {
                    // Correct usage - no transaction, no lazy loading
                    System.out.println(data);
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none {
            it.description?.contains("@Async") == true
        }) {
            "Should not have warning for correct @Async usage"
        }
    }

    fun testNoWarningForEagerLoading() {
        val code = """
            import org.springframework.scheduling.annotation.Async;
            import javax.persistence.Entity;
            import javax.persistence.OneToMany;
            import javax.persistence.FetchType;
            import java.util.List;

            @Entity
            class User {
                @OneToMany(fetch = FetchType.EAGER)
                private List<Post> posts;

                public List<Post> getPosts() {
                    return posts;
                }
            }

            class Post {}

            class UserService {
                @Async
                public void processUserPosts(User user) {
                    int count = user.getPosts().size();  // OK - EAGER fetch
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none {
            it.description?.contains("LazyInitializationException") == true
        }) {
            "Should not have warning for EAGER fetch type"
        }
    }

    fun testNoWarningForDifferentClassAsyncCall() {
        val code = """
            import org.springframework.scheduling.annotation.Async;
            import org.springframework.stereotype.Service;

            @Service
            class UserService {
                private final AsyncService asyncService;

                public UserService(AsyncService asyncService) {
                    this.asyncService = asyncService;
                }

                public void createUser() {
                    asyncService.processAsync();  // OK - different class
                }
            }

            @Service
            class AsyncService {
                @Async
                public void processAsync() {
                    // Will run async correctly
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none {
            it.description?.contains("same class") == true
        }) {
            "Should not have warning for different-class @Async call"
        }
    }

    fun testQuickFix_RemoveAsync() {
        val code = """
            import org.springframework.scheduling.annotation.Async;
            import org.springframework.transaction.annotation.Transactional;

            class UserService {
                @Async
                @Transactional
                public void processAsync() {
                    // code
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        myFixture.doHighlighting()

        val intentions = myFixture.availableIntentions
        val removeFix = intentions.find { it.familyName == "Remove @Async annotation" }
        assertNotNull("Should have quick fix to remove @Async", removeFix)

        removeFix?.invoke(project, myFixture.editor, myFixture.file)

        myFixture.checkResult("""
            import org.springframework.scheduling.annotation.Async;
            import org.springframework.transaction.annotation.Transactional;

            class UserService {
                @Transactional
                public void processAsync() {
                    // code
                }
            }
        """.trimIndent())
    }

    fun testQuickFix_RemoveTransactional() {
        val code = """
            import org.springframework.scheduling.annotation.Async;
            import org.springframework.transaction.annotation.Transactional;

            class UserService {
                @Async
                @Transactional
                public void processAsync() {
                    // code
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        myFixture.doHighlighting()

        val intentions = myFixture.availableIntentions
        val removeFix = intentions.find { it.familyName == "Remove @Transactional annotation" }
        assertNotNull("Should have quick fix to remove @Transactional", removeFix)

        removeFix?.invoke(project, myFixture.editor, myFixture.file)

        myFixture.checkResult("""
            import org.springframework.scheduling.annotation.Async;
            import org.springframework.transaction.annotation.Transactional;

            class UserService {
                @Async
                public void processAsync() {
                    // code
                }
            }
        """.trimIndent())
    }
}
