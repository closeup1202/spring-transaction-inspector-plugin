package com.visualizetransaction.utils

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

object PsiUtils {

    const val SPRING_TRANSACTIONAL = "org.springframework.transaction.annotation.Transactional"
    const val JAKARTA_TRANSACTIONAL = "jakarta.transaction.Transactional"
    const val JAVAX_TRANSACTIONAL = "javax.transaction.Transactional"

    private val ALL_TRANSACTIONAL_FQNS = setOf(
        SPRING_TRANSACTIONAL,
        JAKARTA_TRANSACTIONAL,
        JAVAX_TRANSACTIONAL
    )

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

    /**
     * Check if a method is a write operation using type-based verification.
     * This method combines name-based filtering with type checking to minimize false positives.
     */
    fun isWriteOperationMethod(methodName: String, method: PsiMethod? = null): Boolean {
        val writePatterns = listOf(
            "save", "saveAll", "saveAndFlush",
            "update", "updateAll",
            "delete", "deleteAll", "deleteById", "deleteInBatch",
            "remove", "removeAll",
            "persist", "merge", "flush"
        )

        if (!writePatterns.any { pattern -> methodName.lowercase().contains(pattern) }) {
            return false
        }

        if (method != null) {
            return isActualWriteOperation(method)
        }

        return true
    }

    private fun isActualWriteOperation(method: PsiMethod): Boolean {
        val containingClass = method.containingClass ?: return true

        if (isSpringDataRepository(containingClass)) return true
        if (isJpaEntityManager(containingClass)) return true
        if (hasRepositoryAnnotation(containingClass)) return true

        if (containingClass.name?.endsWith("Repository") == true ||
            containingClass.name?.endsWith("Dao") == true
        ) {
            return true
        }

        return false
    }

    /**
     * Spring Data Repository detection backed by [CachedValuesManager] so the result is
     * automatically invalidated when PSI changes. Replaces the previous unbounded
     * ConcurrentHashMap which leaked stale PsiClass references across edits.
     */
    private fun isSpringDataRepository(psiClass: PsiClass): Boolean {
        return CachedValuesManager.getCachedValue(psiClass) {
            CachedValueProvider.Result.create(
                checkSpringDataRepositoryInheritance(psiClass),
                PsiModificationTracker.MODIFICATION_COUNT
            )
        }
    }

    private fun checkSpringDataRepositoryInheritance(psiClass: PsiClass): Boolean {
        val repositoryInterfaces = listOf(
            "org.springframework.data.repository.Repository",
            "org.springframework.data.repository.CrudRepository",
            "org.springframework.data.repository.PagingAndSortingRepository",
            "org.springframework.data.repository.ListCrudRepository",
            "org.springframework.data.jpa.repository.JpaRepository",
            "org.springframework.data.repository.reactive.ReactiveCrudRepository",
            "org.springframework.data.repository.reactive.ReactiveSortingRepository",
            "org.springframework.data.mongodb.repository.MongoRepository",
            "org.springframework.data.mongodb.repository.ReactiveMongoRepository",
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

    private fun isJpaEntityManager(psiClass: PsiClass): Boolean {
        val qualifiedName = psiClass.qualifiedName ?: return false

        return qualifiedName in listOf(
            "javax.persistence.EntityManager",
            "jakarta.persistence.EntityManager",
            "org.hibernate.Session",
            "org.hibernate.StatelessSession"
        )
    }

    private fun hasRepositoryAnnotation(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any { annotation ->
            annotation.qualifiedName in listOf(
                "org.springframework.stereotype.Repository",
                "org.springframework.data.repository.Repository"
            )
        }
    }

    fun isCollectionModificationMethod(methodName: String): Boolean {
        return methodName in listOf(
            "add", "addAll", "remove", "removeAll", "clear",
            "addFirst", "addLast"
        )
    }

    fun hasLazyJpaRelationshipAnnotation(field: PsiField): Boolean {
        return field.annotations.any { annotation ->
            isLazyJpaRelationshipAnnotation(annotation)
        }
    }

    fun isLazyJpaRelationshipAnnotation(annotation: PsiAnnotation): Boolean {
        val qualifiedName = annotation.qualifiedName ?: return false

        return qualifiedName in listOf(
            "javax.persistence.OneToMany",
            "jakarta.persistence.OneToMany",
            "javax.persistence.ManyToMany",
            "jakarta.persistence.ManyToMany",
            "javax.persistence.ElementCollection",
            "jakarta.persistence.ElementCollection"
        )
    }

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
     * Check if a JPA relationship annotation resolves to LAZY semantics
     * (taking each annotation's default fetch type into account).
     */
    fun isEffectivelyLazy(annotation: PsiAnnotation): Boolean {
        val qualifiedName = annotation.qualifiedName ?: return false
        val fetchValue = annotation.findAttributeValue("fetch")?.text

        val lazyByDefault = qualifiedName in listOf(
            "javax.persistence.OneToMany",
            "jakarta.persistence.OneToMany",
            "javax.persistence.ManyToMany",
            "jakarta.persistence.ManyToMany",
            "javax.persistence.ElementCollection",
            "jakarta.persistence.ElementCollection"
        )

        val eagerByDefault = qualifiedName in listOf(
            "javax.persistence.ManyToOne",
            "jakarta.persistence.ManyToOne",
            "javax.persistence.OneToOne",
            "jakarta.persistence.OneToOne"
        )

        return when {
            lazyByDefault -> fetchValue?.contains("EAGER") != true
            eagerByDefault -> fetchValue?.contains("LAZY") == true
            else -> false
        }
    }

    fun isTransactionalAnnotation(annotation: PsiAnnotation): Boolean {
        return annotation.qualifiedName in ALL_TRANSACTIONAL_FQNS
    }

    fun hasTransactionalAnnotation(method: PsiMethod): Boolean {
        return method.annotations.any { isTransactionalAnnotation(it) } ||
                method.containingClass?.annotations?.any { isTransactionalAnnotation(it) } == true
    }

    /**
     * Find the first @Transactional annotation on a method, or fall back to a class-level one.
     */
    fun findTransactionalAnnotation(method: PsiMethod): PsiAnnotation? {
        return method.annotations.firstOrNull { isTransactionalAnnotation(it) }
            ?: method.containingClass?.annotations?.firstOrNull { isTransactionalAnnotation(it) }
    }

    /**
     * Resolve the propagation type of a @Transactional method, normalized to
     * the Spring enum names (REQUIRED, REQUIRES_NEW, MANDATORY, NEVER, NOT_SUPPORTED, SUPPORTS, NESTED).
     *
     * Works across Spring (`propagation` attribute), Jakarta and javax (`value` attribute holding TxType).
     * Returns null if there is no @Transactional at all.
     */
    fun getPropagation(method: PsiMethod): String {
        val annotation = findTransactionalAnnotation(method) ?: return "REQUIRED"
        val attribute = when (annotation.qualifiedName) {
            SPRING_TRANSACTIONAL -> annotation.findAttributeValue("propagation")
            JAKARTA_TRANSACTIONAL, JAVAX_TRANSACTIONAL -> annotation.findAttributeValue("value")
            else -> null
        } ?: return "REQUIRED"

        return attribute.text.substringAfterLast(".").trim()
    }

    /**
     * Evaluate a boolean attribute value (e.g. readOnly = true). Uses the constant evaluator so
     * `Boolean.TRUE`, parenthesized expressions or imported constants resolve correctly.
     */
    fun evaluateBoolean(value: PsiAnnotationMemberValue?): Boolean? {
        if (value == null) return null
        val project = value.project
        val helper = JavaPsiFacade.getInstance(project).constantEvaluationHelper
        val result = helper.computeConstantExpression(value)
        if (result is Boolean) return result
        return when (value.text.trim()) {
            "true", "Boolean.TRUE" -> true
            "false", "Boolean.FALSE" -> false
            else -> null
        }
    }

    fun isReadOnly(annotation: PsiAnnotation): Boolean {
        if (annotation.qualifiedName != SPRING_TRANSACTIONAL) return false
        return evaluateBoolean(annotation.findAttributeValue("readOnly")) == true
    }

    fun isReadOnlyTransactional(method: PsiMethod): Boolean {
        val annotation = method.annotations.firstOrNull {
            it.qualifiedName == SPRING_TRANSACTIONAL
        } ?: return false
        return isReadOnly(annotation)
    }

    /**
     * Check that two methods share an "AOP-equivalent" containing class.
     * Uses [PsiManager.areElementsEquivalent] so re-resolved PsiClass instances still compare equal.
     */
    fun isSameContainingClass(a: PsiMethod, b: PsiMethod): Boolean {
        val ca = a.containingClass ?: return false
        val cb = b.containingClass ?: return false
        return PsiManager.getInstance(ca.project).areElementsEquivalent(ca, cb)
    }
}
