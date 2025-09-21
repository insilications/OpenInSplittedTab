// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.insilications.openinsplitted.codeInsight.navigation.actions

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.multiverse.isSharedSourceSupportEnabled
import com.intellij.codeInsight.navigation.impl.NavigationRequestor
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ui.UISettings
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.idea.ActionsBundle
import com.intellij.lang.LanguageNamesValidation
import com.intellij.openapi.actionSystem.ex.ActionUtil.underModalProgress
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileNavigator
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.impl.DirectoryNavigationRequest
import com.intellij.platform.backend.navigation.impl.RawNavigationRequest
import com.intellij.platform.backend.navigation.impl.SharedSourceNavigationRequest
import com.intellij.platform.backend.navigation.impl.SourceNavigationRequest
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EDT
import org.insilications.openinsplitted.codeInsight.navigation.impl.gtdTargetNavigatable
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.event.MouseEvent
import javax.swing.SwingConstants

fun receiveNextWindowPane(
    fileEditorManager: FileEditorManagerEx,
    file: VirtualFile,
) {
    val LOG: Logger = Logger.getInstance(GotoDeclarationOrUsageHandler2Splitted::class.java)
    val activeEditorWindow: EditorWindow = fileEditorManager.currentWindow ?: return
    val nextEditorWindow: EditorWindow? = fileEditorManager.getNextWindow(activeEditorWindow)

    if (nextEditorWindow == activeEditorWindow) {
        LOG.info("nextEditorWindow == activeEditorWindow")
        // Create a new vertical split relative to the current window.
        activeEditorWindow.split(SwingConstants.VERTICAL, true, file, true)
    } else if (nextEditorWindow != null) {
        nextEditorWindow.setAsCurrentWindow(true)
//            fileEditorManager.currentWindow = nextEditorWindow
//            nextEditorWindow.requestFocus(true)
        LOG.info("nextEditorWindow != activeEditorWindow - nextEditorWindow != null")
    } else {
        LOG.info("nextEditorWindow != activeEditorWindow - nextEditorWindow == null")
    }
}

internal fun navigateToLookupItem(project: Project): Boolean {
    val activeLookup: Lookup? = LookupManager.getInstance(project).activeLookup
    if (activeLookup == null) {
        return false
    }
    val currentItem = activeLookup.currentItem
    navigateRequestLazy(project) {
        TargetElementUtil.targetElementFromLookupElement(currentItem)
            ?.gtdTargetNavigatable()
            ?.navigationRequest()
    }
    return true
}

/**
 * Obtains a [NavigationRequest] instance from [requestor] on a background thread, and calls [navigateRequest].
 */
internal fun navigateRequestLazy(project: Project, requestor: NavigationRequestor) {
    EDT.assertIsEdt()
    @Suppress("DialogTitleCapitalization")
    val request = underModalProgress(project, ActionsBundle.actionText("GotoDeclarationOnly")) {
        requestor.navigationRequest()
    }
    if (request != null) {
        navigateRequest(project, request)
    }
}

@Internal
@RequiresEdt
fun navigateRequest(project: Project, request: NavigationRequest) {
    EDT.assertIsEdt()
    val LOG: Logger = Logger.getInstance(GotoDeclarationOrUsageHandler2Splitted::class.java)
    IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation()
    when (request) {
        is SourceNavigationRequest -> {
            // TODO support pure source request without OpenFileDescriptor
            val offset = request.offsetMarker?.takeIf { it.isValid }?.startOffset ?: -1
            val openFileDescriptor = if (request is SharedSourceNavigationRequest && isSharedSourceSupportEnabled(project)) {
                OpenFileDescriptor(project, request.file, request.context, offset)
            } else {
                OpenFileDescriptor(project, request.file, offset)
            }
            if (UISettings.getInstance().openInPreviewTabIfPossible && Registry.`is`("editor.preview.tab.navigation")) {
                openFileDescriptor.isUsePreviewTab = true
            }
            val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
            val file: VirtualFile = openFileDescriptor.file
            if (!fileEditorManager.canOpenFile(file)) {
                LOG.info("!fileEditorManager.canOpenFile(file) - $file")
            }
            receiveNextWindowPane(fileEditorManager, file)
//            val editorWindow = fileEditorManager.splitters.openInRightSplit(file, true)
//            openFileDescriptor.setUseCurrentWindow(true)
//            fileEditorManager.currentWindow = editorWindow
//            val opened = ofd.navigateInEditor(FileEditorManager.getInstance(project), options.requestFocus)
            FileNavigator.getInstance().navigate(openFileDescriptor, true)
        }

        is DirectoryNavigationRequest -> {
            PsiNavigationSupport.getInstance().navigateToDirectory(request.directory, true)
        }

        is RawNavigationRequest -> {
            LOG.info("RawNavigationRequest - navigatable is ${request.navigatable::class.simpleName}")
            request.navigatable.navigate(true)
        }

        else -> {
            error("unsupported request ${request.javaClass.name}")
        }
    }
}

fun notifyNowhereToGo(project: Project, editor: Editor, file: PsiFile, offset: Int) {
    // Disable the 'no declaration found' notification for keywords
    if (Registry.`is`("ide.gtd.show.error") && !isUnderDoubleClick() && !isKeywordUnderCaret(project, file, offset)) {
        HintManager.getInstance().showInformationHint(editor, CodeInsightBundle.message("declaration.navigation.nowhere.to.go"))
    }
}

private fun isUnderDoubleClick(): Boolean {
    val event = IdeEventQueue.getInstance().trueCurrentEvent
    return event is MouseEvent && event.clickCount == 2
}

private fun isKeywordUnderCaret(project: Project, file: PsiFile, offset: Int): Boolean {
    val elementAtCaret = file.findElementAt(offset) ?: return false
    val namesValidator = LanguageNamesValidation.INSTANCE.forLanguage(elementAtCaret.language)
    return namesValidator.isKeyword(elementAtCaret.text, project)
}
