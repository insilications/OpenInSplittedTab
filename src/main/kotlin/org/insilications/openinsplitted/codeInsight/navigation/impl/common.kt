// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("NOTHING_TO_INLINE")

package org.insilications.openinsplitted.codeInsight.navigation.impl


import com.intellij.codeInsight.TargetElementUtil
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.EditSourceUtil
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
val navigationOptionsRequestFocus: NavigationOptions = NavigationOptions.requestFocus()
val progressTitlePreparingNavigation: String = IdeBundle.message("progress.title.preparing.navigation")

inline fun PsiElement.gtdTargetNavigatable(): Navigatable? {
    return TargetElementUtil.getInstance()
        .getGotoDeclarationTarget(this, navigationElement)
        ?.psiNavigatable()
}

inline fun PsiElement.psiNavigatable(): Navigatable? {
    return this as? Navigatable
        ?: EditSourceUtil.getDescriptor(this)
}

@RequiresEdt
inline fun fetchDataContext(project: Project): DataContext? {
    val component = IdeFocusManager.getInstance(project).focusOwner
    return component?.let { DataManager.getInstance().getDataContext(it) }
}

/**
 * Blocking (EDT-entered) modal progress replacement for obsolete
 * ProgressManager.runProcessWithProgressSynchronously. (Fixed version.)
 */
//@Throws(ProcessCanceledException::class)
//fun <T> underModalProgress(
//    project: Project,
//    progressTitle: @NlsContexts.ProgressTitle String,
//    computable: Computable<T>,
//): T {
//    val app = ApplicationManager.getApplication()
//    val dumbService = DumbService.getInstance(project)
//    val useAlternativeResolve = dumbService.isAlternativeResolveEnabled
//
//    // Layer D+E: read action around user computable
//    val readComputable = ThrowableComputable<T, RuntimeException> {
//        app.runReadAction(Computable { computable.compute() })
//    }
//
//    // Layer C: prioritized computation wrapping readComputable
//    val prioritizedComputable = ThrowableComputable<T, RuntimeException> {
//        ProgressManager.getInstance().computePrioritized(readComputable)
//    }
//
//    val finalComputable = ThrowableComputable<T, RuntimeException> {
//        if (useAlternativeResolve) {
//            dumbService.computeWithAlternativeResolveEnabled(prioritizedComputable)
//        } else {
//            prioritizedComputable.compute()
//        }
//    }
//
//    return try {
//        // runWithModalProgressBlocking must be invoked on EDT
//        runWithModalProgressBlocking(project, progressTitle) {
//            // We are now in a coroutine context (Dispatchers.Default)
//            // It is safe to just call finalComputable.compute()
//            finalComputable.compute()
//        }
//    } catch (ce: CancellationException) {
//        // Unify with legacy callers expecting ProcessCanceledException
//        throw ProcessCanceledException(ce)
//    }
//}
//
///**
// * Suspending, coroutine-based modal progress variant.
// *
// * Ensures execution on a background (Default) dispatcher, while showing a modal,
// * cancellable progress dialog. Preserves:
// *  - read action
// *  - prioritized scheduling
// *  - alternative resolve mode (if active)
// *  - conversion of CancellationException to ProcessCanceledException
// *
// * NOTE: The [computable] is a regular (non-suspending) function; it is executed
// * entirely under a read action (and possibly under alternative resolve). Do NOT
// * perform long blocking IO inside it without additional consideration.
// *
// * If you need a truly suspending body, see the commented template below (and the
// * warnings about suspending under read actions).
// */
//@Throws(ProcessCanceledException::class)
//suspend fun <T> underModalProgressSuspend(
//    project: Project,
//    progressTitle: @NlsContexts.ProgressTitle String,
////    computable: Computable<T>,
//    computable: () -> T,
////    eita: T,
//): T {
////    val kk = { println("Hello") }
////    val kk3: Supplier<T> = { eita }
////    val kk4 = Computable { eita }
////    val kk5 = kk4.compute()
////    val kk2: () -> T = { println("Hello") }
//    val dumbService = DumbService.getInstance(project)
//    val useAlternativeResolve = dumbService.isAlternativeResolveEnabled
//
//    val readComputable = ThrowableComputable<T, RuntimeException> { ApplicationManager.getApplication().runReadAction(Computable { computable() }) }
////    val readComputable = ThrowableComputable<T, RuntimeException> {
////        ApplicationManager.getApplication().runReadAction(Computable { computable() })
////        ApplicationManager.getApplication().runReadAction(Supplier<T> { computable() })
////    }
//    val readComputable2 = readAction { computable() }
//    val prioritizedComputable = ThrowableComputable<T, RuntimeException> {
//        ProgressManager.getInstance().computePrioritized(readComputable)
//    }
//    val finalComputable = ThrowableComputable<T, RuntimeException> {
//        if (useAlternativeResolve) {
//            dumbService.computeWithAlternativeResolveEnabled(prioritizedComputable)
//        } else {
//            prioritizedComputable.compute()
//        }
//    }
//
//    return try {
//        // Ensure we are not on EDT for the heavy portion
//        withContext(Dispatchers.Default) {
//            withModalProgress(project, progressTitle) {
//                // Body executes in Dispatchers.Default as inherited
//                finalComputable.compute()
//            }
//        }
//    } catch (ce: CancellationException) {
//        // Translate to ProcessCanceledException for legacy consistency
//        throw ProcessCanceledException(ce)
//    }
//}

/*
 * OPTIONAL (commented) template for a suspending variant that allows a suspend body.
 * WARNING: You SHOULD NOT hold a read lock across suspension points.
 * If you really need a suspend action:
 *
 * suspend fun <T> underModalProgressSuspend(
 *   project: Project,
 *   title: String,
 *   action: suspend () -> T
 * ): T {
 *   return withContext(Dispatchers.Default) {
 *     withModalProgress(project, title) {
 *       // If you need read access for a portion, wrap only that portion:
 *       readAction { actionBlockingPart() }
 *       // Avoid: runReadAction { action() } if action can suspend!
 *       action()
 *     }
 *   }
 * }
 */
