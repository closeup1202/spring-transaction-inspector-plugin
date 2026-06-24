package com.visualizetransaction.inspections

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

/**
 * Base test class providing:
 *   - A real JDK on the test project's classpath (so java.io.*, java.util.*, java.sql.* resolve).
 *   - Spring (annotation) and JPA (annotation + enum) stubs.
 */
abstract class BaseInspectionTest : LightJavaCodeInsightFixtureTestCase() {

    override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_21

    override fun setUp() {
        super.setUp()
        addJdkExtras()
        addSpringAnnotationStubs()
        addJpaAnnotationStubs()
        addSpringDataStubs()
    }

    /**
     * The light JDK descriptor only ships `java.base`. Provide stubs for the standard
     * library classes the test sources reference (`java.sql.SQLException`, etc.).
     */
    private fun addJdkExtras() {
        myFixture.addFileToProject("java/sql/SQLException.java", """
            package java.sql;
            public class SQLException extends Exception {
                public SQLException() {}
                public SQLException(String message) { super(message); }
            }
        """.trimIndent())
    }

    private fun addSpringAnnotationStubs() {
        myFixture.addFileToProject("org/springframework/transaction/annotation/Transactional.java", """
            package org.springframework.transaction.annotation;
            public @interface Transactional {
                Propagation propagation() default Propagation.REQUIRED;
                boolean readOnly() default false;
                int timeout() default -1;
                Class<? extends Throwable>[] rollbackFor() default {};
                String[] rollbackForClassName() default {};
                Class<? extends Throwable>[] noRollbackFor() default {};
            }
        """.trimIndent())

        myFixture.addFileToProject("org/springframework/transaction/annotation/Propagation.java", """
            package org.springframework.transaction.annotation;
            public enum Propagation {
                REQUIRED, REQUIRES_NEW, MANDATORY, NEVER, NOT_SUPPORTED, SUPPORTS, NESTED
            }
        """.trimIndent())

        myFixture.addFileToProject("org/springframework/scheduling/annotation/Async.java", """
            package org.springframework.scheduling.annotation;
            public @interface Async {
                String value() default "";
            }
        """.trimIndent())

        myFixture.addFileToProject("org/springframework/retry/annotation/Retryable.java", """
            package org.springframework.retry.annotation;
            public @interface Retryable {
                int maxAttempts() default 3;
            }
        """.trimIndent())

        myFixture.addFileToProject("org/springframework/stereotype/Service.java", """
            package org.springframework.stereotype;
            public @interface Service {
                String value() default "";
            }
        """.trimIndent())

        myFixture.addFileToProject("org/springframework/stereotype/Repository.java", """
            package org.springframework.stereotype;
            public @interface Repository {
                String value() default "";
            }
        """.trimIndent())

        myFixture.addFileToProject("org/springframework/stereotype/Component.java", """
            package org.springframework.stereotype;
            public @interface Component {
                String value() default "";
            }
        """.trimIndent())

        myFixture.addFileToProject("org/springframework/beans/factory/annotation/Autowired.java", """
            package org.springframework.beans.factory.annotation;
            public @interface Autowired {
                boolean required() default true;
            }
        """.trimIndent())
    }

    private fun addJpaAnnotationStubs() {
        myFixture.addFileToProject("javax/persistence/Entity.java", """
            package javax.persistence;
            public @interface Entity {
                String name() default "";
            }
        """.trimIndent())

        myFixture.addFileToProject("javax/persistence/OneToMany.java", """
            package javax.persistence;
            public @interface OneToMany {
                FetchType fetch() default FetchType.LAZY;
                CascadeType[] cascade() default {};
                String mappedBy() default "";
            }
        """.trimIndent())

        myFixture.addFileToProject("javax/persistence/ManyToOne.java", """
            package javax.persistence;
            public @interface ManyToOne {
                FetchType fetch() default FetchType.EAGER;
                CascadeType[] cascade() default {};
            }
        """.trimIndent())

        myFixture.addFileToProject("javax/persistence/OneToOne.java", """
            package javax.persistence;
            public @interface OneToOne {
                FetchType fetch() default FetchType.EAGER;
                CascadeType[] cascade() default {};
                String mappedBy() default "";
            }
        """.trimIndent())

        myFixture.addFileToProject("javax/persistence/FetchType.java", """
            package javax.persistence;
            public enum FetchType { LAZY, EAGER }
        """.trimIndent())

        myFixture.addFileToProject("javax/persistence/CascadeType.java", """
            package javax.persistence;
            public enum CascadeType { ALL, PERSIST, MERGE, REMOVE, REFRESH, DETACH }
        """.trimIndent())

        myFixture.addFileToProject("jakarta/transaction/Transactional.java", """
            package jakarta.transaction;
            public @interface Transactional {
                TxType value() default TxType.REQUIRED;
                Class<?>[] rollbackOn() default {};
                Class<?>[] dontRollbackOn() default {};
            }
        """.trimIndent())

        myFixture.addFileToProject("jakarta/transaction/TxType.java", """
            package jakarta.transaction;
            public enum TxType { REQUIRED, REQUIRES_NEW, MANDATORY, SUPPORTS, NOT_SUPPORTED, NEVER }
        """.trimIndent())

        myFixture.addFileToProject("javax/transaction/Transactional.java", """
            package javax.transaction;
            public @interface Transactional {
                TxType value() default TxType.REQUIRED;
                Class<?>[] rollbackOn() default {};
                Class<?>[] dontRollbackOn() default {};
            }
        """.trimIndent())

        myFixture.addFileToProject("javax/transaction/TxType.java", """
            package javax.transaction;
            public enum TxType { REQUIRED, REQUIRES_NEW, MANDATORY, SUPPORTS, NOT_SUPPORTED, NEVER }
        """.trimIndent())
    }

    private fun addSpringDataStubs() {
        myFixture.addFileToProject("org/springframework/data/repository/Repository.java", """
            package org.springframework.data.repository;
            public interface Repository<T, ID> {}
        """.trimIndent())

        myFixture.addFileToProject("org/springframework/data/repository/CrudRepository.java", """
            package org.springframework.data.repository;
            import java.util.Optional;
            public interface CrudRepository<T, ID> extends Repository<T, ID> {
                <S extends T> S save(S entity);
                <S extends T> Iterable<S> saveAll(Iterable<S> entities);
                Optional<T> findById(ID id);
                boolean existsById(ID id);
                Iterable<T> findAll();
                Iterable<T> findAllById(Iterable<ID> ids);
                long count();
                void deleteById(ID id);
                void delete(T entity);
                void deleteAll(Iterable<? extends T> entities);
                void deleteAll();
            }
        """.trimIndent())

        myFixture.addFileToProject("org/springframework/data/jpa/repository/JpaRepository.java", """
            package org.springframework.data.jpa.repository;
            import org.springframework.data.repository.CrudRepository;
            import java.util.List;
            public interface JpaRepository<T, ID> extends CrudRepository<T, ID> {
                @Override List<T> findAll();
                <S extends T> List<S> saveAll(Iterable<S> entities);
                <S extends T> S saveAndFlush(S entity);
                void flush();
                void deleteAllInBatch();
            }
        """.trimIndent())
    }
}
