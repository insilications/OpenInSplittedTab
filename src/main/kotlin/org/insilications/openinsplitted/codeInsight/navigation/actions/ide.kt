// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("NOTHING_TO_INLINE")

package org.insilications.openinsplitted.codeInsight.navigation.actions

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.TargetElementUtil.targetElementFromLookupElement
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupEx
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.navigation.impl.NavigationRequestor
import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.util.EditSourceUtil
import com.intellij.lang.LanguageNamesValidation
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.impl.RawNavigationRequest
import com.intellij.platform.backend.navigation.impl.SourceNavigationRequest
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.insilications.openinsplitted.codeInsight.navigation.impl.fetchDataContext
import org.insilications.openinsplitted.codeInsight.navigation.impl.gtdTargetNavigatable
import org.insilications.openinsplitted.codeInsight.navigation.impl.navigationOptionsRequestFocus
import org.insilications.openinsplitted.codeInsight.navigation.impl.progressTitlePreparingNavigation
import org.insilications.openinsplitted.debug
import org.jetbrains.annotations.ApiStatus
import java.awt.AWTEvent
import java.awt.event.MouseEvent
import javax.swing.SwingConstants

val LOG: Logger = Logger.getInstance("org.insilications.openinsplitted")

/**
 * If `nextEditorWindow` is the same as `activeEditorWindow`, it means there are no splitted tabs.
 * In this case, we create a new vertical split relative to the current window, with
 * `focusNew` set to `true` to focus on the new tab.
 *
 * If `nextEditorWindow` is different from `activeEditorWindow`, then there is already a splitted tab.
 * Set that splitted tab as the current window using the `setAsCurrentWindow` method
 * with the `requestFocus` parameter set to `true`.
 *
 * @param project
 * @param file
 */
@RequiresEdt
fun receiveNextWindowPane(
    project: Project,
    file: VirtualFile?,
) {
    val fileEditorManager: FileEditorManagerEx = FileEditorManagerEx.getInstanceEx(project)
    val activeEditorWindow: EditorWindow = fileEditorManager.currentWindow ?: return
    val nextEditorWindow: EditorWindow? = fileEditorManager.getNextWindow(activeEditorWindow)

    if (nextEditorWindow == activeEditorWindow) {
        LOG.debug { "nextEditorWindow == activeEditorWindow" }
        // Create a new vertical split relative to the current window and focus on it.
        // The `file` parameter can be null, in which case the new split will have the same file as `activeEditorWindow`.
        activeEditorWindow.split(SwingConstants.VERTICAL, true, file, true)
    } else if (nextEditorWindow != null) {
        nextEditorWindow.setAsCurrentWindow(true)
        LOG.debug { "nextEditorWindow != activeEditorWindow - nextEditorWindow != null" }
    } else {
        LOG.debug { "nextEditorWindow != activeEditorWindow - nextEditorWindow == null" }
    }
}

@ApiStatus.Internal
@RequiresEdt
@RequiresBlockingContext
inline fun navigateToLookupItem(project: Project, editor: Editor): Boolean {
    val activeLookup: LookupEx = LookupManager.getInstance(project).activeLookup ?: return false
    val currentItem: LookupElement? = activeLookup.currentItem
    navigateToRequestor(project, {
        targetElementFromLookupElement(currentItem)
            ?.gtdTargetNavigatable()
            ?.navigationRequest()
    }, editor)
    return true
}

/**
 * This function retrieves a navigation request from the provided [requestor] and navigates to it.
 * We call `receiveNextWindowPane` to preemptively set the current window to the next splitted tab or a new splitted tab.
 * This forces the calls to the `navigate` method to reuse that tab. This workaround might be fragile, but it works perfectly.
 */
@ApiStatus.Internal
@RequiresEdt
@RequiresBlockingContext
inline fun navigateToRequestor(project: Project, requestor: NavigationRequestor, editor: Editor) {
    runWithModalProgressBlocking(project, progressTitlePreparingNavigation) {
        LOG.debug { "navigateToRequestor - requestor is ${requestor::class.simpleName}" }

        val request: NavigationRequest =
            ProgressManager.getInstance().computePrioritized(ThrowableComputable<NavigationRequest?, RuntimeException> {
                ApplicationManager.getApplication().runReadAction(Computable<NavigationRequest?> { requestor.navigationRequest() })
            }) ?: LOG.warn("navigateToRequestor - Failed to create navigation request").let { return@runWithModalProgressBlocking }

        val file: VirtualFile? = getVirtualFileFromNavigationRequest(request)

        val dataContext: DataContext
        // Switch to EDT for UI side-effects
        withContext(Dispatchers.EDT) {
            // Acquire DataContext on EDT
            dataContext = DataManager.getInstance().getDataContext(editor.component)
            // History update belongs on EDT
            IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation()
            // Maybe we don't have to extract the `file` everytime if we know a new split window is not going to be created?
            receiveNextWindowPane(project, file)
        }

        // Delegate to the platform's `IdeNavigationService.kt` to perform actual navigation
        project.serviceAsync<NavigationService>().navigate(request, navigationOptionsRequestFocus, dataContext)
    }
}

/**
 * This function retrieves a navigation request from the provided [navigatable] and navigates to it.
 * We call `receiveNextWindowPane` to preemptively set the current window to the next splitted tab or a new splitted tab.
 * This forces the calls to the `navigate` method to reuse that tab. This workaround might be fragile, but it works perfectly.
 */
@ApiStatus.Internal
@RequiresEdt
inline fun navigateToNavigatable(project: Project, navigatable: Navigatable, dataContext: DataContext?) {
    runWithModalProgressBlocking(project, progressTitlePreparingNavigation) {
        LOG.debug { "navigateToNavigatable - navigatable is: ${navigatable::class.simpleName}" }
        val file: VirtualFile? = getVirtualFileFromNavigatable(navigatable)

        val dataContextCheck: DataContext?
        // Switch to EDT for UI side-effects
        withContext(Dispatchers.EDT) {
            // Acquire DataContext on EDT
            dataContextCheck = dataContext ?: fetchDataContext(project)
            // History update belongs on EDT
            IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation()
            // Maybe we don't have to extract the `file` everytime if we know a new split window is not going to be created?
            receiveNextWindowPane(project, file)
        }

        // Delegate to the platform's `IdeNavigationService.kt` to perform actual navigation
        project.serviceAsync<NavigationService>().navigate(navigatable, navigationOptionsRequestFocus, dataContextCheck)
    }
}

@ApiStatus.Experimental
suspend fun getVirtualFileFromNavigationRequest(request: NavigationRequest): VirtualFile? {
    return when (request) {
        // `SharedSourceNavigationRequest` is a subclass of `SourceNavigationRequest`.
        is SourceNavigationRequest -> {
            LOG.debug { "getVirtualFileFromNavigationRequest - request is SourceNavigationRequest" }
            request.file
        }

        is RawNavigationRequest -> {
            LOG.debug { "getVirtualFileFromNavigationRequest - request is RawNavigationRequest" }
            getVirtualFileFromNavigatable(request.navigatable)
        }
        // `DirectoryNavigationRequest` is non-source, so we don't need to handle it
        // It can be handled perfectly by `NavigationService.navigate`
        else -> {
            LOG.warn("getVirtualFileFromNavigationRequest - Non-source request: ${request::class.simpleName}")
            null
        }
    }
}

suspend fun getVirtualFileFromNavigatable(navigatable: Navigatable): VirtualFile? {
    // 1. OpenFileDescriptor
    // This only accesses the getter `nav.file` (a VirtualFile). This is VFS-level and does not require a read action
    if (navigatable is OpenFileDescriptor) {
        LOG.debug { "0 extractFileFromNavigatable - navigatable is OpenFileDescriptor" }
        return navigatable.file
    }

    // 2. PSI-based
    if (navigatable is PsiElement) {
        // if (!nav.isValid) return null

        // Try to get a descriptor derived from PSI with the `EditSourceUtil.getDescriptor` method
        // The method makes a best-effort attempt to extract descriptor-like objects of type `Navigatable`
        // It often yields an OpenFileDescriptor
        val descriptor: Navigatable? = readAction { EditSourceUtil.getDescriptor(navigatable) }
        when (descriptor) {
            // This only accesses the getter `nav.file` (a VirtualFile). This is VFS-level and does not require a read action
            is OpenFileDescriptor -> {
                LOG.debug { "1 extractFileFromNavigatable - descriptor is OpenFileDescriptor" }
                return descriptor.file
            }

            is PsiElement -> {
                LOG.debug { "2 extractFileFromNavigatable - descriptor is PsiElement" }
                return readAction { PsiUtilCore.getVirtualFile(descriptor) } ?: LOG.warn("3 extractFileFromNavigatable - returned null").let { return null }
            }
            // 3. Non-PSI, non-descriptor-like object of type `Navigatable` → No recoverable file
            // else -> null
        }
    }
    LOG.warn("4 extractFileFromNavigatable - returned null")
    return null
}

inline fun notifyNowhereToGo(project: Project, editor: Editor, file: PsiFile, offset: Int) {
    // Disable the 'no declaration found' notification for keywords
    if (Registry.`is`("ide.gtd.show.error") && !isUnderDoubleClick() && !isKeywordUnderCaret(project, file, offset)) {
        HintManager.getInstance().showInformationHint(editor, CodeInsightBundle.message("declaration.navigation.nowhere.to.go"))
    }
}

inline fun isUnderDoubleClick(): Boolean {
    val event: AWTEvent = IdeEventQueue.getInstance().trueCurrentEvent
    return event is MouseEvent && event.clickCount == 2
}

inline fun isKeywordUnderCaret(project: Project, file: PsiFile, offset: Int): Boolean {
    val elementAtCaret: PsiElement = file.findElementAt(offset) ?: return false
    val namesValidator = LanguageNamesValidation.INSTANCE.forLanguage(elementAtCaret.language)
    return namesValidator.isKeyword(elementAtCaret.text, project)
}
