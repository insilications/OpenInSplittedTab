// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("NOTHING_TO_INLINE")

package org.insilications.openinsplitted.codeInsight.navigation.actions

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupEx
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.navigation.impl.NavigationRequestor
import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.util.EditSourceUtil
import com.intellij.idea.ActionsBundle
import com.intellij.lang.LanguageNamesValidation
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.Project
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
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.insilications.openinsplitted.codeInsight.navigation.impl.gtdTargetNavigatable
import org.insilications.openinsplitted.debug
import org.insilications.openinsplitted.navigationOptionsRequestFocus
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.AWTEvent
import java.awt.event.MouseEvent
import javax.swing.SwingConstants

private val LOG: Logger = Logger.getInstance("org.insilications.openinsplitted")

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

internal inline fun navigateToLookupItem(project: Project, editor: Editor): Boolean {
    val activeLookup: LookupEx = LookupManager.getInstance(project).activeLookup ?: return false
    val currentItem: LookupElement? = activeLookup.currentItem
    navigateRequestLazy(project, {
        TargetElementUtil.targetElementFromLookupElement(currentItem)
            ?.gtdTargetNavigatable()
            ?.navigationRequest()
    }, editor)
    return true
}

@Internal
@RequiresEdt
internal inline fun navigateRequestLazy(project: Project, requestor: NavigationRequestor, editor: Editor) {
//    EDT.assertIsEdt()
    @Suppress("DialogTitleCapitalization")
    val dataContext: DataContext = editor.component.let { DataManager.getInstance().getDataContext(it) }
    runWithModalProgressBlocking(project, ActionsBundle.actionText("GotoDeclarationOnly")) {
        val request: NavigationRequest? = readAction {
            requestor.navigationRequest()
        }

        if (request != null) {
            IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation()
            withContext(Dispatchers.EDT) {
                when (request) {
                    is SourceNavigationRequest -> {
                        LOG.debug { "navigateRequestLazy - SourceNavigationRequest" }
                        // We use this to preemptively set the current window to the next splitted tab or a new splitted tab.
                        // This forces the calls to the `navigate` method to reuse that tab.
                        // This workaround might be fragile, but it works perfectly.
                        // We already have the target file, so we pass it to open it in the case that a new split must be created.
                        receiveNextWindowPane(project, request.file)
                    }

                    // TODO: make RawNavigationRequest navigate to the next splitted tab or a new splitted tab too
                    // TODO: We need to get the target `VirtualFile` file from `RawNavigationRequest.navigatable` to pass to `receiveNextWindowPane`
                    is RawNavigationRequest -> {
                        val requestNavigatable: Navigatable = request.navigatable

                        LOG.debug { "navigateRequestLazy - RawNavigationRequest - request.navigatable is ${requestNavigatable::class.simpleName}" }
                        when (requestNavigatable) {
                            is OpenFileDescriptor -> {
                                LOG.debug { "navigateRequestLazy - RawNavigationRequest - request.navigatable is OpenFileDescriptor" }
                                receiveNextWindowPane(project, requestNavigatable.file)
                            }

                            is Navigatable -> {}
                            else -> {}
                        }
                        if (requestNavigatable is PsiElement) {
                            val kk: VirtualFile? = EditSourceUtil.getDescriptor(requestNavigatable)?.file
                        }
                        if (request.canNavigateToSource) {

                        }
                        val virtualFile = PsiUtilCore.getVirtualFile(request.navigatable)?.takeIf { it.isValid }
                    }

                    else -> {
                        LOG.error("navigateRequestLazy - unsupported request ${request.javaClass.name}")
                    }
                }

                project.serviceAsync<NavigationService>().navigate(request, navigationOptionsRequestFocus, dataContext)
            }
        }
    }
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
