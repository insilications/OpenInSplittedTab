package org.para.plugin

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.find.actions.ShowUsagesAction
import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.lang.LanguageNamesValidation
import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.util.PsiUtilCore
import com.intellij.ui.awt.RelativePoint
import java.text.MessageFormat
import javax.swing.SwingConstants
import javax.swing.Timer

/**
 * Created by hendrikvonprince on 09/11/16.
 * Converted to Kotlin by Copilot
 */
open class OpenInSplittedTabBaseAction(private val closePreviousTab: Boolean) : AnAction() {

    companion object {
        private val LOG = Logger.getInstance(OpenInSplittedTabBaseAction::class.java)

        private fun getUsagesPageSize(): Int {
            return maxOf(1, Registry.intValue("ide.usages.page.size", 100))
        }

        private fun startFindUsages(editor: Editor, project: Project, element: PsiElement?): Boolean {
            if (element == null) {
                return false
            }
            
            if (DumbService.getInstance(project).isDumb) {
                val action = ActionManager.getInstance().getAction(ShowUsagesAction.ID)
                val name = action.templatePresentation.text
                DumbService.getInstance(project).showDumbModeNotification(ActionUtil.getUnavailableMessage(name, false))
            } else {
                val popupPosition = JBPopupFactory.getInstance().guessBestPopupLocation(editor)
                ShowUsagesAction().startFindUsages(element, popupPosition, editor, getUsagesPageSize())
            }
            return true
        }

        private fun isKeywordUnderCaret(project: Project, file: PsiFile, offset: Int): Boolean {
            val elementAtCaret = file.findElementAt(offset) ?: return false
            val namesValidator = LanguageNamesValidation.INSTANCE.forLanguage(elementAtCaret.language)
            return namesValidator?.isKeyword(elementAtCaret.text, project) == true
        }

        private fun suggestCandidates(reference: PsiReference?): Collection<PsiElement> {
            if (reference == null) {
                return emptyList()
            }
            return TargetElementUtil.getInstance().getTargetCandidates(reference)
        }

        // returns true if processor is run or is going to be run after showing popup
        private fun chooseAmbiguousTarget(
            editor: Editor,
            offset: Int,
            processor: PsiElementProcessor<in PsiElement>,
            titlePattern: String,
            elements: Array<PsiElement>?
        ): Boolean {
            if (TargetElementUtil.inVirtualSpace(editor, offset)) {
                return false
            }

            val reference = TargetElementUtil.findReference(editor, offset)

            val targetElements = elements ?: if (reference == null) {
                emptyArray()
            } else {
                PsiUtilCore.toPsiElementArray(
                    underModalProgress(reference.element.project) {
                        suggestCandidates(reference)
                    }
                )
            }

            when (targetElements.size) {
                1 -> {
                    val element = targetElements[0]
                    LOG.assertTrue(element != null)
                    processor.execute(element)
                    return true
                }
                0 -> return false
                else -> {
                    val title = if (reference == null) {
                        titlePattern
                    } else {
                        val range = reference.rangeInElement
                        val elementText = reference.element.text
                        LOG.assertTrue(
                            range.startOffset >= 0 && range.endOffset <= elementText.length,
                            "${targetElements.contentToString()};$reference"
                        )
                        val refText = range.substring(elementText)
                        MessageFormat.format(titlePattern, refText)
                    }

                    NavigationUtil.getPsiElementPopup(
                        targetElements,
                        DefaultPsiElementCellRenderer(),
                        title,
                        processor
                    ).showInBestPositionFor(editor)
                    return true
                }
            }
        }

        private fun chooseAmbiguousTarget(
            editor: Editor,
            offset: Int,
            elements: Array<PsiElement>,
            nextWindowPane: EditorWindow
        ) {
            if (!editor.component.isShowing) return
            
            val navigateProcessor = PsiElementProcessor<PsiElement> { element ->
                scrollToTarget(element, nextWindowPane)
                true
            }
            
            val found = chooseAmbiguousTarget(
                editor,
                offset,
                navigateProcessor,
                CodeInsightBundle.message("declaration.navigation.title"),
                elements
            )
            
            if (!found) {
                HintManager.getInstance().showErrorHint(editor, "Cannot find declaration to go to")
            }
        }

        private fun scrollToTarget(target: PsiElement, nextWindowPane: EditorWindow) {
            // defer the scrolling of the new tab, otherwise the scrolling may not work properly
            val delayingScrollToCaret = Timer(10) { _ ->
                if (!nextWindowPane.isShowing) {
                    scrollToTarget(target, nextWindowPane)
                } else {
                    nextWindowPane.setAsCurrentWindow(true)
                    val selectedTextEditor = nextWindowPane.manager.selectedTextEditor
                    if (selectedTextEditor != null) {
                        // Wrap PSI and editor model access in read action to avoid threading issues
                        ReadAction.run {
                            selectedTextEditor.caretModel.moveToOffset(target.textOffset)
                            selectedTextEditor.scrollingModel.scrollToCaret(ScrollType.CENTER)
                        }
                    }
                }
            }
            delayingScrollToCaret.isRepeats = false
            delayingScrollToCaret.start()
        }

        private fun <T> underModalProgress(project: Project, computable: Computable<T>): T {
            return ProgressManager.getInstance().runProcessWithProgressSynchronously({
                DumbService.getInstance(project).setAlternativeResolveEnabled(true)
                try {
                    ApplicationManager.getApplication().runReadAction(computable)
                } finally {
                    DumbService.getInstance(project).setAlternativeResolveEnabled(false)
                }
            }, "Resolving Reference...", true, project)
        }
    }

    private val gotoDeclarationAction = GotoDeclarationAction()

    override fun actionPerformed(e: AnActionEvent) {
        val file = e.getData(LangDataKeys.PSI_FILE)
        val editor = e.getData(PlatformDataKeys.EDITOR)

        if (file == null || editor == null) {
            e.presentation.isEnabled = false
            return
        }

        val offset = editor.caretModel.offset
        val elementAt = file.findElementAt(offset)

        if (elementAt == null) {
            e.presentation.isEnabled = false
            return
        }

        val project = elementAt.project
        val elements = getTargets(editor, project, offset)

        // if we got a valid symbol we will open it in a splitted tab, else we call the GotoDeclarationAction
        if (elements != null) {
            val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
            val nextWindowPane = receiveNextWindowPane(project, fileEditorManager, e.dataContext) ?: return

            when {
                elements.size != 1 -> {
                    if (elements.isEmpty() && suggestCandidates(TargetElementUtil.findReference(editor, offset)).isEmpty()) {
                        val element = GotoDeclarationAction.findElementToShowUsagesOf(editor, editor.caretModel.offset)
                        if (startFindUsages(editor, project, element)) {
                            return
                        }

                        //disable 'no declaration found' notification for keywords
                        if (isKeywordUnderCaret(project, file, offset)) return
                    }
                    chooseAmbiguousTarget(editor, offset, elements, nextWindowPane)
                }
                else -> {
                    val element = elements[0]
                    fileEditorManager.currentWindow = nextWindowPane
                    // We want to replace the current active tab inside the splitter instead of creating a new tab.
                    // So, we save which file is currently open, open the new file (in a new tab) and then close the
                    // previous tab. To do this, we save which file is currently open.
                    val fileToClose = fileEditorManager.currentFile
                    // use the openFileImpl2-method instead of the openFile-method, as the openFile-method would open a new
                    // window when the assigned shortcut for this action includes the shift-key
                    nextWindowPane.manager.openFileImpl2(nextWindowPane, element.containingFile.virtualFile, true)
                    // Of course, we don't want to close the tab if the new element is inside the same file as before.
                    if (closePreviousTab && fileToClose != null && fileToClose != element.containingFile.virtualFile) {
                        fileEditorManager.currentWindow.closeFile(fileToClose)
                    }

                    scrollToTarget(element, nextWindowPane)
                }
            }
        } else {
            gotoDeclarationAction.actionPerformed(e)
        }
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(LangDataKeys.PSI_FILE)
        val editor = e.getData(PlatformDataKeys.EDITOR)

        if (file == null || editor == null) {
            e.presentation.isEnabled = false
            return
        }

        val offset = editor.caretModel.offset
        val elementAt = file.findElementAt(offset)

        if (elementAt == null) {
            e.presentation.isEnabled = false
            return
        }

        val project = elementAt.project
        val target = getTargets(editor, project, offset)
        if (target != null) {
            e.presentation.isEnabled = true
        } else {
            // we found no target for our own action, but maybe we can execute the GotoDeclaration-action
            gotoDeclarationAction.update(e)
        }
    }

    /**
     * @param fileEditorManager
     * @param project
     * @param dataContext
     * @return If there already are splitted tabs, it will return the next one. If not, it creates a vertically splitted tab
     */
    private fun receiveNextWindowPane(
        project: Project,
        fileEditorManager: FileEditorManagerEx,
        dataContext: DataContext
    ): EditorWindow? {
        val activeWindowPane = EditorWindow.DATA_KEY.getData(dataContext)
            ?: return null // Action invoked when no files are open; do nothing

        var nextWindowPane = fileEditorManager.getNextWindow(activeWindowPane)

        if (nextWindowPane == activeWindowPane) {
            val fileManagerEx = FileEditorManagerEx.getInstance(project) as FileEditorManagerEx
            fileManagerEx.createSplitter(SwingConstants.VERTICAL, fileManagerEx.currentWindow)
            nextWindowPane = fileEditorManager.getNextWindow(activeWindowPane)
        }
        return nextWindowPane
    }

    /**
     * @return The first `PsiElement` that is found by the GotoDeclarationAction for the currently selected `PsiElement`
     */
    private fun getTargets(editor: Editor, project: Project, offset: Int): Array<PsiElement>? {
        return underModalProgress(project) {
            GotoDeclarationAction.findAllTargetElements(project, editor, offset)
        }
    }
}