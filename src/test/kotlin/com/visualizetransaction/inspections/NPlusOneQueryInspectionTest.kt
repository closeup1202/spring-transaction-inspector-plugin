package com.visualizetransaction.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Test suite for NPlusOneQueryInspection
 *
 * Tests various N+1 query patterns to ensure detection accuracy
 */
class NPlusOneQueryInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(NPlusOneQueryInspection::class.java)
    }

    override fun getTestDataPath(): String {
        return "src/test/resources"
    }

    fun testDetectLazyLoadingInForEachLoop() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import javax.persistence.Entity;
            import javax.persistence.OneToMany;
            import javax.persistence.FetchType;
            import java.util.List;

            public class UserService {
                private UserRepository userRepository;

                @Transactional
                public void processUsers() {
                    List<User> users = userRepository.findAll();

                    for (User user : users) {
                        int count = user.getPosts().size();  // ❌ N+1 detected
                    }
                }
            }

            @Entity
            class User {
                @OneToMany(fetch = FetchType.LAZY)
                private List<Post> posts;

                public List<Post> getPosts() {
                    return posts;
                }
            }

            interface UserRepository extends JpaRepository<User, Long> {
                List<User> findAll();
            }

            class Post {}
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any { it.description?.contains("N+1 query") == true }) {
            "Expected N+1 query warning not found"
        }
    }

    fun testDetectLazyLoadingInStreamMap() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import javax.persistence.*;
            import java.util.List;
            import java.util.stream.Collectors;

            public class UserService {
                private UserRepository userRepository;

                @Transactional
                public List<String> getUserPostCounts() {
                    return userRepository.findAll()
                        .stream()
                        .map(user -> String.valueOf(user.getPosts().size()))  // ❌ N+1 detected
                        .collect(Collectors.toList());
                }
            }

            @Entity
            class User {
                @OneToMany(fetch = FetchType.LAZY)
                private List<Post> posts;

                public List<Post> getPosts() {
                    return posts;
                }
            }

            interface UserRepository extends JpaRepository<User, Long> {}
            class Post {}
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any { it.description?.contains("N+1 query") == true }) {
            "Expected N+1 query warning in stream.map() not found"
        }
    }

    fun testDetectLazyLoadingInStreamFlatMap() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import javax.persistence.*;
            import java.util.List;
            import java.util.stream.Collectors;

            public class UserService {
                private UserRepository userRepository;

                @Transactional
                public List<Post> getAllUserPosts() {
                    return userRepository.findAll()
                        .stream()
                        .flatMap(user -> user.getPosts().stream())  // ❌ N+1 detected
                        .collect(Collectors.toList());
                }
            }

            @Entity
            class User {
                @OneToMany(fetch = FetchType.LAZY)
                private List<Post> posts;

                public List<Post> getPosts() {
                    return posts;
                }
            }

            interface UserRepository extends JpaRepository<User, Long> {}
            class Post {}
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any { it.description?.contains("N+1 query") == true && it.description?.contains("flatMap") == true }) {
            "Expected N+1 query warning in stream.flatMap() not found"
        }
    }

    fun testDetectLazyLoadingInStreamForEach() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import javax.persistence.*;
            import java.util.List;

            public class UserService {
                private UserRepository userRepository;

                @Transactional
                public void printUserPosts() {
                    userRepository.findAll()
                        .stream()
                        .forEach(user -> System.out.println(user.getPosts().size()));  // ❌ N+1 detected
                }
            }

            @Entity
            class User {
                @OneToMany(fetch = FetchType.LAZY)
                private List<Post> posts;

                public List<Post> getPosts() {
                    return posts;
                }
            }

            interface UserRepository extends JpaRepository<User, Long> {}
            class Post {}
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any { it.description?.contains("N+1 query") == true && it.description?.contains("forEach") == true }) {
            "Expected N+1 query warning in stream.forEach() not found"
        }
    }

    fun testNoWarningForEagerLoadingInStream() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import javax.persistence.*;
            import java.util.List;
            import java.util.stream.Collectors;

            public class UserService {
                private UserRepository userRepository;

                @Transactional
                public List<String> getUserNames() {
                    return userRepository.findAll()
                        .stream()
                        .map(User::getName)  // ✓ No warning - eager field
                        .collect(Collectors.toList());
                }
            }

            @Entity
            class User {
                private String name;

                public String getName() {
                    return name;
                }
            }

            interface UserRepository extends JpaRepository<User, Long> {}
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none { it.description?.contains("N+1 query") == true }) {
            "Unexpected N+1 query warning for eager field access"
        }
    }
}
