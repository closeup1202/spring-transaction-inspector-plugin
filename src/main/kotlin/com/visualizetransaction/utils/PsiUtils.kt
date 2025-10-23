package com.visualizetransaction.utils

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.JavaPsiFacade
import java.util.concurrent.ConcurrentHashMap

object PsiUtils {

    fun findFieldFromGetter(method: PsiMethod): PsiField? {
        val methodName = method.name

        val fieldName = when {
            methodName.startsWith("get") && methodName.length > 3 -> {
                methodName.substring(3).replaceFirstChar { it.lowercase() }
            }

            methodName.startsWith("is") && methodName.length > 2 -> {
                methodName.substring(2).replaceFirstChar { it.lowercase() }
            }

            else -> return null
        }

        return method.containingClass?.findFieldByName(fieldName, false)
    }

    // Cache for repository class checks (performance optimization)
    private val repositoryClassCache = ConcurrentHashMap<PsiClass, Boolean>()

    /**
     * Check if a method is a write operation using type-based verification.
     * This method combines name-based filtering with type checking to minimize false positives.
     *
     * @param methodName The name of the method to check
     * @param method Optional PsiMethod for type verification (if available)
     * @return true if this is a write operation, false otherwise
     */
    fun isWriteOperationMethod(methodName: String, method: PsiMethod? = null): Boolean {
        // Step 1: Name-based filtering (performance optimization)
        val writePatterns = listOf(
            "save", "saveAll", "saveAndFlush",
            "update", "updateAll",
            "delete", "deleteAll", "deleteById", "deleteInBatch",
            "remove", "removeAll",
            "persist", "merge", "flush"
        )

        if (!writePatterns.any { pattern -> methodName.lowercase().contains(pattern) }) {
            return false  // Fast path: name doesn't match any writing pattern
        }

        // Step 2: Type-based verification (if the method is available)
        if (method != null) {
            return isActualWriteOperation(method)
        }

        // Step 3: Fallback - conservative approach when type info unavailable
        // If name matches, but we can't verify the type, assume it's a write operation
        // User can suppress the warning if it's a false positive
        return true
    }

    /**
     * Verify if a method is actually a write operation based on its containing class type.
     */
    private fun isActualWriteOperation(method: PsiMethod): Boolean {
        val containingClass = method.containingClass ?: return true

        // 1. Spring Data Repository (the most common case)
        if (isSpringDataRepository(containingClass)) {
            return true
        }

        // 2. JPA EntityManager/Session
        if (isJpaEntityManager(containingClass)) {
            return true
        }

        // 3. @Repository annotation
        if (hasRepositoryAnnotation(containingClass)) {
            return true
        }

        // 4. Naming convention (fallback)
        if (containingClass.name?.endsWith("Repository") == true ||
            containingClass.name?.endsWith("Dao") == true) {
            return true
        }

        return false
    }

    /**
     * Check if a class is a Spring Data Repository by verifying interface inheritance.
     * Uses caching for performance.
     */
    private fun isSpringDataRepository(psiClass: PsiClass): Boolean {
        return repositoryClassCache.getOrPut(psiClass) {
            checkSpringDataRepositoryInheritance(psiClass)
        }
    }

    /**
     * Check if a class inherits from Spring Data Repository interfaces.
     */
    private fun checkSpringDataRepositoryInheritance(psiClass: PsiClass): Boolean {
        val repositoryInterfaces = listOf(
            // Spring Data Common
            "org.springframework.data.repository.Repository",
            "org.springframework.data.repository.CrudRepository",
            "org.springframework.data.repository.PagingAndSortingRepository",
            "org.springframework.data.repository.ListCrudRepository",
            // Spring Data JPA
            "org.springframework.data.jpa.repository.JpaRepository",
            // Spring Data Reactive
            "org.springframework.data.repository.reactive.ReactiveCrudRepository",
            "org.springframework.data.repository.reactive.ReactiveSortingRepository",
            // Spring Data MongoDB
            "org.springframework.data.mongodb.repository.MongoRepository",
            "org.springframework.data.mongodb.repository.ReactiveMongoRepository",
            // Spring Data R2DBC
            "org.springframework.data.r2dbc.repository.R2dbcRepository"
        )

        val project = psiClass.project
        val scope = psiClass.resolveScope
        val facade = JavaPsiFacade.getInstance(project)

        for (repoInterface in repositoryInterfaces) {
            val baseClass = facade.findClass(repoInterface, scope) ?: continue
            if (psiClass.isInheritor(baseClass, true)) {
                return true
            }
        }

        return false
    }

    /**
     * Check if a class is a JPA EntityManager or Hibernate Session.
     */
    private fun isJpaEntityManager(psiClass: PsiClass): Boolean {
        val qualifiedName = psiClass.qualifiedName ?: return false

        return qualifiedName in listOf(
            // JPA 2.x
            "javax.persistence.EntityManager",
            // JPA 3.0+ (Jakarta)
            "jakarta.persistence.EntityManager",
            // Hibernate
            "org.hibernate.Session",
            "org.hibernate.StatelessSession"
        )
    }

    /**
     * Check if a class has @Repository annotation.
     */
    private fun hasRepositoryAnnotation(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any { annotation ->
            annotation.qualifiedName in listOf(
                "org.springframework.stereotype.Repository",
                "org.springframework.data.repository.Repository"
            )
        }
    }

    /**
     * Check if a method name is a collection modification operation.
     */
    fun isCollectionModificationMethod(methodName: String): Boolean {
        return methodName in listOf(
            "add", "addAll", "remove", "removeAll", "clear",
            "addFirst", "addLast"
        )
    }

    /**
     * Check if a field has lazy-loaded JPA relationship annotations.
     * Checks both javax.persistence (JPA 2.x) and jakarta.persistence (JPA 3.0+).
     */
    fun hasLazyJpaRelationshipAnnotation(field: PsiField): Boolean {
        return field.annotations.any { annotation ->
            isLazyJpaRelationshipAnnotation(annotation)
        }
    }

    /**
     * Check if an annotation is a lazy-loaded JPA relationship annotation.
     */
    fun isLazyJpaRelationshipAnnotation(annotation: PsiAnnotation): Boolean {
        val qualifiedName = annotation.qualifiedName ?: return false

        return qualifiedName in listOf(
            // OneToMany is LAZY by default
            "javax.persistence.OneToMany",
            "jakarta.persistence.OneToMany",
            // ManyToMany is LAZY by default
            "javax.persistence.ManyToMany",
            "jakarta.persistence.ManyToMany",
            // ElementCollection is LAZY by default
            "javax.persistence.ElementCollection",
            "jakarta.persistence.ElementCollection"
        )
    }

    /**
     * Check if an annotation is a JPA relationship annotation (any type).
     */
    fun isJpaRelationshipAnnotation(annotation: PsiAnnotation): Boolean {
        val qualifiedName = annotation.qualifiedName ?: return false

        return qualifiedName in listOf(
            "javax.persistence.OneToOne",
            "jakarta.persistence.OneToOne",
            "javax.persistence.OneToMany",
            "jakarta.persistence.OneToMany",
            "javax.persistence.ManyToOne",
            "jakarta.persistence.ManyToOne",
            "javax.persistence.ManyToMany",
            "jakarta.persistence.ManyToMany",
            "javax.persistence.ElementCollection",
            "jakarta.persistence.ElementCollection"
        )
    }

    /**
     * Check if an annotation is a Spring @Transactional annotation.
     */
    fun isTransactionalAnnotation(annotation: PsiAnnotation): Boolean {
        return annotation.qualifiedName == "org.springframework.transaction.annotation.Transactional"
    }

    /**
     * Check if a method or class has @Transactional annotation.
     */
    fun hasTransactionalAnnotation(method: PsiMethod): Boolean {
        return method.annotations.any { isTransactionalAnnotation(it) } ||
                method.containingClass?.annotations?.any { isTransactionalAnnotation(it) } == true
    }
}
