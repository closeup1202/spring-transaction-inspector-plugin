package com.visualizetransaction.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Base test class that provides common Spring/JPA annotation stubs
 */
abstract class BaseInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        addSpringAnnotationStubs()
        addJpaAnnotationStubs()
    }

    private fun addSpringAnnotationStubs() {
        // Spring @Transactional
        myFixture.addFileToProject("org/springframework/transaction/annotation/Transactional.java", """
            package org.springframework.transaction.annotation;
            public @interface Transactional {
                Propagation propagation() default Propagation.REQUIRED;
                boolean readOnly() default false;
                Class<? extends Throwable>[] rollbackFor() default {};
                Class<? extends Throwable>[] noRollbackFor() default {};
            }
        """.trimIndent())

        myFixture.addFileToProject("org/springframework/transaction/annotation/Propagation.java", """
            package org.springframework.transaction.annotation;
            public enum Propagation {
                REQUIRED, REQUIRES_NEW, MANDATORY, NEVER, NOT_SUPPORTED, SUPPORTS, NESTED
            }
        """.trimIndent())

        // Spring @Async
        myFixture.addFileToProject("org/springframework/scheduling/annotation/Async.java", """
            package org.springframework.scheduling.annotation;
            public @interface Async {
                String value() default "";
            }
        """.trimIndent())

        // Spring @Service
        myFixture.addFileToProject("org/springframework/stereotype/Service.java", """
            package org.springframework.stereotype;
            public @interface Service {
                String value() default "";
            }
        """.trimIndent())

        // Spring @Repository
        myFixture.addFileToProject("org/springframework/stereotype/Repository.java", """
            package org.springframework.stereotype;
            public @interface Repository {
                String value() default "";
            }
        """.trimIndent())

        // Spring @Component
        myFixture.addFileToProject("org/springframework/stereotype/Component.java", """
            package org.springframework.stereotype;
            public @interface Component {
                String value() default "";
            }
        """.trimIndent())

        // Spring @Autowired
        myFixture.addFileToProject("org/springframework/beans/factory/annotation/Autowired.java", """
            package org.springframework.beans.factory.annotation;
            public @interface Autowired {
                boolean required() default true;
            }
        """.trimIndent())
    }

    private fun addJpaAnnotationStubs() {
        // JPA @Entity
        myFixture.addFileToProject("javax/persistence/Entity.java", """
            package javax.persistence;
            public @interface Entity {
                String name() default "";
            }
        """.trimIndent())

        // JPA @OneToMany
        myFixture.addFileToProject("javax/persistence/OneToMany.java", """
            package javax.persistence;
            public @interface OneToMany {
                FetchType fetch() default FetchType.LAZY;
                CascadeType[] cascade() default {};
                String mappedBy() default "";
            }
        """.trimIndent())

        // JPA @ManyToOne
        myFixture.addFileToProject("javax/persistence/ManyToOne.java", """
            package javax.persistence;
            public @interface ManyToOne {
                FetchType fetch() default FetchType.EAGER;
                CascadeType[] cascade() default {};
            }
        """.trimIndent())

        // JPA @OneToOne
        myFixture.addFileToProject("javax/persistence/OneToOne.java", """
            package javax.persistence;
            public @interface OneToOne {
                FetchType fetch() default FetchType.EAGER;
                CascadeType[] cascade() default {};
                String mappedBy() default "";
            }
        """.trimIndent())

        // JPA FetchType
        myFixture.addFileToProject("javax/persistence/FetchType.java", """
            package javax.persistence;
            public enum FetchType {
                LAZY, EAGER
            }
        """.trimIndent())

        // JPA CascadeType
        myFixture.addFileToProject("javax/persistence/CascadeType.java", """
            package javax.persistence;
            public enum CascadeType {
                ALL, PERSIST, MERGE, REMOVE, REFRESH, DETACH
            }
        """.trimIndent())

        // Jakarta @Transactional
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
            public enum TxType {
                REQUIRED, REQUIRES_NEW, MANDATORY, SUPPORTS, NOT_SUPPORTED, NEVER
            }
        """.trimIndent())

        // javax @Transactional
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
            public enum TxType {
                REQUIRED, REQUIRES_NEW, MANDATORY, SUPPORTS, NOT_SUPPORTED, NEVER
            }
        """.trimIndent())
    }

    override fun getTestDataPath(): String {
        return "src/test/resources"
    }
}
