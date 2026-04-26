# Changelog

All notable changes to **Spring Transaction Inspector** are documented here. The most
recent entry is also embedded into `plugin.xml` `<change-notes>` and used by the GitHub
release workflow.

## [1.1.0] - Quality & Reliability Release

### Internal
- Replaced unbounded `ConcurrentHashMap` repository cache with `CachedValuesManager` keyed on `PsiModificationTracker.MODIFICATION_COUNT`, eliminating stale `PsiClass` retention across edits.
- Centralized propagation/readOnly/transactional-annotation parsing in `PsiUtils`; all inspections now share the same logic and behave consistently across Spring, Jakarta and javax `@Transactional`.
- Booleans on `@Transactional` (e.g. `readOnly`) are now resolved via `JavaPsiFacade.constantEvaluationHelper`, so `Boolean.TRUE`, parenthesized expressions and constant references are recognized.
- Same-class detection now uses `PsiManager.areElementsEquivalent`, fixing false negatives on re-resolved `PsiClass` instances.
- Inspections short-circuit at the visitor entry when their toggle is disabled, eliminating per-call work in disabled-by-user setups.
- Quick fixes (`AddRollbackForFix`, `ChangePropagationToRequiresNewFix`) use direct PSI attribute manipulation instead of textual annotation rebuild, preserving formatting and avoiding edge-case attribute loss.
- Modifier quick fixes (`ChangeMethodVisibility`, `RemoveFinal`, `RemoveStatic`, `SuppressWarning`) now traverse via `PsiTreeUtil.getParentOfType`, robust to unexpected parent shapes.

### Added
- **Jakarta/javax propagation conflict detection.** `TransactionalPropagationConflictInspection` and `TransactionalMethodCallInspection` now honor `jakarta.transaction.Transactional`/`javax.transaction.Transactional` `value = TxType.MANDATORY|NEVER|REQUIRES_NEW`.
- **Jakarta/javax `rollbackOn` awareness.** `CheckedExceptionRollbackInspection` no longer warns when Jakarta/javax `@Transactional` already declares `rollbackOn`.
- **N+1 detection outside `@Transactional`.** New opt-in setting *"Also detect outside @Transactional (OSIV)"* — useful for projects relying on Open-EntityManager-In-View where lazy access happens past the service layer.
- **Deeper async lazy-access detection.** Lazy-relationship access in `@Async` methods is now detected anywhere in the call chain (e.g. `user.getOrders().get(0).getItems()`), not only the first-level getter.

### CI
- Tests now run on every PR via a new `test.yml` workflow.
- Release workflow stops hard-coding the changelog body and uses GitHub's auto-generated release notes plus the marketplace link, so future tags don't ship stale notes.

## [1.0.6] - Build Toolchain Update
- Upgraded to Gradle 9.4 and IntelliJ Platform Gradle Plugin 2.12.0 to fix a plugin archive extraction issue on IntelliJ IDEA 2026.1 (build 261.*).

## [1.0.5] - IDE Compatibility Update
- Extended support to IntelliJ IDEA 2026.1 (build 261.*).

## [1.0.4] - Improved Same-Class Call Detection
- Same-class `@Transactional` method call now differentiates:
  - INFO: caller has `@Transactional` and no special propagation (annotation is redundant but joins existing transaction).
  - WARNING: called method has special propagation (`REQUIRES_NEW`, `MANDATORY`, etc.) — won't work as expected.
  - WARNING: caller has no `@Transactional` — annotation is completely ignored.

## [1.0.3] - Jakarta EE Support
- Detects `jakarta.transaction.Transactional` and `javax.transaction.Transactional` for AOP-bypass, invalid modifiers, async/transactional conflicts, gutter icons and the Transaction Info action.
- Extended IntelliJ IDEA support to build 253.*.

## [1.0.2] - Enhanced Detection
- New inspection: Transaction Propagation Conflict Detection (MANDATORY/NEVER/REQUIRES_NEW).
- N+1 detection now recognizes `@ManyToOne(fetch = LAZY)` and `@OneToOne(fetch = LAZY)`.

## [1.0.1] - Major Improvements
- Fixed memory leak in `AddRollbackForFix`.
- Type-based write-operation detection (95%+ accuracy).
- Repository class caching for performance.
- Settings toggles for every inspection.

## [1.0.0] - Initial Release
- 7 inspections for `@Transactional` anti-patterns.
- Quick Fixes, gutter icons, customizable settings.
