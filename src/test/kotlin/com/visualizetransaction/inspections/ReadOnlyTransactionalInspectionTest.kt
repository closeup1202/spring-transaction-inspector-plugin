package com.visualizetransaction.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Test suite for ReadOnlyTransactionalInspection
 *
 * Tests detection of write operations in @Transactional(readOnly=true) methods
 */
class ReadOnlyTransactionalInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(ReadOnlyTransactionalInspection::class.java)
    }

    override fun getTestDataPath(): String {
        return "src/test/resources"
    }

    fun testDetectSaveInReadOnlyMethod() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.stereotype.Service;

            @Service
            public class UserService {
                private UserRepository userRepository;

                @Transactional(readOnly = true)
                public void updateUser(User user) {
                    userRepository.save(user);  // ❌ Write operation in readOnly method
                }
            }

            class User {}
            interface UserRepository extends JpaRepository<User, Long> {}
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any { it.description?.contains("readOnly=true") == true && it.description?.contains("write") == true }) {
            "Expected readOnly violation warning not found for save()"
        }
    }

    fun testDetectUpdateInReadOnlyMethod() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.stereotype.Service;

            @Service
            public class UserService {
                private UserRepository userRepository;

                @Transactional(readOnly = true)
                public void updateUser(User user) {
                    userRepository.update(user);  // ❌ Write operation in readOnly method
                }
            }

            class User {}
            interface UserRepository extends JpaRepository<User, Long> {
                void update(User user);
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any { it.description?.contains("readOnly=true") == true && it.description?.contains("write") == true }) {
            "Expected readOnly violation warning not found for update()"
        }
    }

    fun testDetectDeleteInReadOnlyMethod() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.stereotype.Service;

            @Service
            public class UserService {
                private UserRepository userRepository;

                @Transactional(readOnly = true)
                public void deleteUser(Long id) {
                    userRepository.deleteById(id);  // ❌ Write operation in readOnly method
                }
            }

            class User {}
            interface UserRepository extends JpaRepository<User, Long> {}
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any { it.description?.contains("readOnly=true") == true && it.description?.contains("write") == true }) {
            "Expected readOnly violation warning not found for deleteById()"
        }
    }

    fun testDetectCollectionModificationInReadOnlyMethod() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import javax.persistence.*;
            import java.util.List;

            @Entity
            public class User {
                @OneToMany(fetch = FetchType.LAZY)
                private List<Post> posts;

                public List<Post> getPosts() {
                    return posts;
                }
            }

            public class UserService {
                private User user;

                @Transactional(readOnly = true)
                public void addPostToUser(Post post) {
                    user.getPosts().add(post);  // ❌ Collection modification in readOnly method
                }
            }

            class Post {}
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any { it.description?.contains("readOnly=true") == true && it.description?.contains("modify") == true }) {
            "Expected readOnly violation warning not found for collection modification"
        }
    }

    fun testNoWarningForReadOnlyOperationInReadOnlyMethod() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.stereotype.Service;

            @Service
            public class UserService {
                private UserRepository userRepository;

                @Transactional(readOnly = true)
                public User getUser(Long id) {
                    return userRepository.findById(id).orElse(null);  // ✓ No warning - read operation
                }
            }

            class User {}
            interface UserRepository extends JpaRepository<User, Long> {}
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none { it.description?.contains("readOnly=true") == true && it.description?.contains("write") == true }) {
            "Unexpected readOnly violation warning for read operation"
        }
    }

    fun testNoWarningForWriteInNonReadOnlyMethod() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.stereotype.Service;

            @Service
            public class UserService {
                private UserRepository userRepository;

                @Transactional
                public void saveUser(User user) {
                    userRepository.save(user);  // ✓ No warning - normal @Transactional
                }
            }

            class User {}
            interface UserRepository extends JpaRepository<User, Long> {}
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none { it.description?.contains("readOnly=true") == true }) {
            "Unexpected readOnly violation warning for non-readOnly method"
        }
    }

    fun testDetectMultipleSaveCallsInReadOnlyMethod() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.stereotype.Service;

            @Service
            public class UserService {
                private UserRepository userRepository;

                @Transactional(readOnly = true)
                public void batchUpdate(List<User> users) {
                    for (User user : users) {
                        userRepository.save(user);  // ❌ Write operation in readOnly method
                    }
                }
            }

            class User {}
            interface UserRepository extends JpaRepository<User, Long> {}
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any { it.description?.contains("readOnly=true") == true && it.description?.contains("write") == true }) {
            "Expected readOnly violation warning not found in loop"
        }
    }
}
