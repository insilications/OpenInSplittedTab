package org.para.plugin

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.codeInsight.navigation.getPsiElementPopup
import com.intellij.find.actions.ShowUsagesAction
import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.lang.LanguageNamesValidation
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.util.PsiUtilCore
import com.intellij.ui.awt.RelativePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.MessageFormat
import javax.swing.SwingConstants
import javax.swing.Timer

/**
 * Base action to open the declaration of a symbol in a splitted tab.
 *
 * @param closePreviousTab If true, closes the previous tab in the splitted window when navigating to a new symbol.
 * If false, keeps all opened tabs in the splitted window.
 */
open class OpenInSplittedTabBaseAction(private val closePreviousTab: Boolean) : DumbAwareAction() {
    companion object {
        private val LOG = Logger.getInstance(OpenInSplittedTabBaseAction::class.java)

        private fun startFindUsages(editor: Editor, project: Project, element: PsiElement): Boolean {
            val dumbServiceInstance: DumbService = DumbService.getInstance(project)
            if (dumbServiceInstance.isDumb) {
                LOG.info("startFindUsages - dumbServiceInstance.isDumb == true - element: ${element.text}")
                val action: AnAction = ActionManager.getInstance().getAction(ShowUsagesAction.ID)
                val name: String = action.templatePresentation.text

                @Suppress("DEPRECATION")
                dumbServiceInstance.showDumbModeNotification(ActionUtil.getUnavailableMessage(name, false))
            } else {
                LOG.info("startFindUsages - dumbServiceInstance.isDumb == false - element: ${element.text}")
                val popupPosition: RelativePoint = JBPopupFactory.getInstance().guessBestPopupLocation(editor)
                ShowUsagesAction.startFindUsages(element, popupPosition, editor)
            }
            return true
        }

        private fun isKeywordUnderCaret(project: Project, file: PsiFile, offset: Int): Boolean {
            val elementAtCaret = file.findElementAt(offset) ?: return false
            val namesValidator = LanguageNamesValidation.INSTANCE.forLanguage(elementAtCaret.language)
            return namesValidator.isKeyword(elementAtCaret.text, project)
        }

        private suspend fun getSuggestCandidates(project: Project, reference: PsiReference?): Array<PsiElement> {
            if (reference == null) {
                return PsiElement.EMPTY_ARRAY
            }

//            val app = ApplicationManager.getApplication()
//
//            // If already in a read action (e.g., during update), compute directly to avoid `invokeAndWait` deadlock.
//            if (app.isReadAccessAllowed) {
//                return PsiUtilCore.toPsiElementArray(TargetElementUtil.getInstance().getTargetCandidates(reference))
//            }

            return withBackgroundProgress(project, "Resolving References...") {
                readAction {
                    PsiUtilCore.toPsiElementArray(TargetElementUtil.getInstance().getTargetCandidates(reference))
                }
            }
        }

        // Returns true if processor is run or is going to be run after showing popup
        private suspend fun chooseAmbiguousTarget2(
            project: Project,
            editor: Editor,
            offset: Int,
            processor: PsiElementProcessor<in PsiElement>,
            titlePattern: String,
            elements: Array<PsiElement>
        ): Boolean {
            if (TargetElementUtil.inVirtualSpace(editor, offset)) {
                return false
            }

            val reference: PsiReference? = TargetElementUtil.findReference(editor, offset)


//            if (elements.isEmpty()) {
//                elements = if (reference == null)
//                    PsiElement.EMPTY_ARRAY
//                else
//                    PsiUtilCore.toPsiElementArray(
//                        underModalProgress(
//                            reference.getElement().getProject(),
//                            { suggestCandidates(reference) })
//                    )
//            }
//            if (elements == null || elements.length == 0) {
//                elements = reference == null ? PsiElement.EMPTY_ARRAY
//                : PsiUtilCore.toPsiElementArray(
//                    underModalProgress(reference.getElement().getProject(),
//                        () -> suggestCandidates(reference)));
//            }

            val targetElements: Array<PsiElement> = (elements ?: if (reference == null) {
                PsiElement.EMPTY_ARRAY
            } else {
                getSuggestCandidates(project, reference)
            })

            when (targetElements.size) {
                1 -> {
                    val element: PsiElement = targetElements[0]
                    LOG.info("chooseAmbiguousTarget2 - targetElements.size == 1 - element: ${element.text}")
                    return processor.execute(element)
                }

                0 -> return false
                else -> {
                    val title = if (reference == null) {
                        titlePattern
                    } else {
                        val range = reference.rangeInElement
                        val elementText = reference.element.text

                        LOG.info("chooseAmbiguousTarget2 - reference.element.text: $elementText")
                        LOG.info(
                            "chooseAmbiguousTarget2 - reference.rangeInElement.startOffset: " +
                                    "${range.startOffset}. Is it >= 0? ${range.startOffset}"
                        )
                        LOG.info(
                            "chooseAmbiguousTarget2 - reference.rangeInElement.endOffset: ${range.endOffset}. " +
                                    "Is it <= ${elementText.length} (reference.element.text.length)?"
                        )
                        LOG.info("chooseAmbiguousTarget2 - targetElements: ${targetElements.contentToString()} - reference: $reference")

                        val refText = range.substring(elementText)
                        MessageFormat.format(titlePattern, refText)
                    }

                    getPsiElementPopup(
                        targetElements,
                        DefaultPsiElementCellRenderer(),
                        title,
                        processor
                    ).showInBestPositionFor(editor)
                    return true
                }
            }
        }

        private suspend fun chooseAmbiguousTarget1(
            project: Project,
            editor: Editor,
            offset: Int,
            elements: Array<PsiElement>,
            nextWindowPane: EditorWindow
        ) {
            if (!editor.component.isShowing) return

            val navigateProcessor = PsiElementProcessor<PsiElement> { element ->
                scrollToTarget(element, nextWindowPane)
                return@PsiElementProcessor true
            }

            val found: Boolean = chooseAmbiguousTarget2(
                project,
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
            // Defer the scrolling of the new tab, otherwise the scrolling may not work properly
            val delayingScrollToCaret = Timer(10) { _ ->
                if (!nextWindowPane.isShowing) {
                    scrollToTarget(target, nextWindowPane)
                } else {
                    nextWindowPane.setAsCurrentWindow(true)

                    @Suppress("UnstableApiUsage")
                    val selectedTextEditor: Editor? = nextWindowPane.manager.selectedTextEditor
                    if (selectedTextEditor != null) {
                        // Wrap PSI and `selectedTextEditor` editor model access in a read action to avoid threading issues
                        // `ReadAction.run()` and `ReadAction.compute()` require a blocking context and are always
                        // executed on the calling thread. Use them if a read action is required in a blocking context.
                        ReadAction.run<RuntimeException> {
                            selectedTextEditor.caretModel.moveToOffset(target.textOffset)
                            selectedTextEditor.scrollingModel.scrollToCaret(ScrollType.CENTER)
                        }
                    }
                }
            }
            delayingScrollToCaret.isRepeats = false
            delayingScrollToCaret.start()
        }
    }

    private val gotoDeclarationAction = GotoDeclarationAction()

    // Tell the platform we are on a background thread
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val dataContext: DataContext = e.dataContext
        val editor: Editor? = CommonDataKeys.EDITOR.getData(dataContext)
        val file: PsiFile? = CommonDataKeys.PSI_FILE.getData(dataContext)

        if (file == null || editor == null) {
            e.presentation.isEnabled = false
            return
        }

        val offset: Int = editor.caretModel.offset
        val elementAt: PsiElement? = file.findElementAt(offset)

        if (elementAt == null) {
            e.presentation.isEnabled = false
            return
        }

        val project: Project = elementAt.project

        @Suppress("UnstableApiUsage")
        currentThreadCoroutineScope().launch {
            val elements: Array<PsiElement> = getTargetsAtOffset(editor, project, offset)

            withContext(Dispatchers.EDT) {
                val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
                val nextWindowPane = receiveNextWindowPane(project, fileEditorManager, e.dataContext) ?: return@withContext

                when {
                    // If we get exactly one `PsiElement` at `offset`, we will open it in a splitted tab
                    elements.size == 1 -> {
                        val element: PsiElement = elements[0]
                        fileEditorManager.currentWindow = nextWindowPane

                        LOG.info("actionPerformed - elements.size == 1 - element: ${element.text}")

                        // We want to replace the current active tab inside the splitter instead of creating a new tab.
                        // So, we save which file is currently open, open the new file (in a new tab) and then close the
                        // previous tab. To do this, we save which file is currently open.
                        val fileToClose = fileEditorManager.currentFile

                        // Use the `openFileImpl2` internal method instead of the `openFile` public API method because
                        // the `openFile` opens a new window when the assigned shortcut for this action includes the shift-key
                        @Suppress("DEPRECATION_ERROR")
                        nextWindowPane.manager.openFileImpl2(nextWindowPane, element.containingFile.virtualFile, true)
                        // We don't want to close the tab if the new element is inside the same file as before
                        if (closePreviousTab && fileToClose != null && fileToClose != element.containingFile.virtualFile) {
                            fileEditorManager.currentWindow?.closeFile(fileToClose)
                        }

                        scrollToTarget(element, nextWindowPane)
                    }
                    // If we get zero or more than one `PsiElement` at `offset`, that is ambiguous
                    // We will try to decide which one to choose
                    else -> {
                        // This call to `suggestCandidates` maybe doesn't need to have an internal call to `withBackgroundProgress`
                        // This check can also be moved to the background, but for simplicity and because
                        // it might be fast, we can leave it here. If it proves slow, refactor.

                        LOG.info("actionPerformed - elements.size != 1 - elements: ${elements.contentToString()}")

                        if (elements.isEmpty() && getSuggestCandidates(
                                project,
                                TargetElementUtil.findReference(editor, offset)
                            ).isEmpty()
                        ) {
                            @Suppress("TestOnlyProblems")
                            val element: PsiElement? = GotoDeclarationAction.findElementToShowUsagesOf(editor, editor.caretModel.offset)

                            if (element != null && startFindUsages(editor, project, element)) {
                                return@withContext
                            }

                            if (isKeywordUnderCaret(project, file, offset)) return@withContext
                        }
                        chooseAmbiguousTarget1(project, editor, offset, elements, nextWindowPane)


                    }
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val dataContext: DataContext = e.dataContext
        val editor: Editor? = CommonDataKeys.EDITOR.getData(dataContext)
        val file: PsiFile? = CommonDataKeys.PSI_FILE.getData(dataContext)
        val presentation: Presentation = e.presentation

        if (file == null || editor == null) {
            presentation.isEnabled = false
            return
        }

        val offset: Int = editor.caretModel.offset
        val elementAt: PsiElement? = file.findElementAt(offset)

        if (elementAt == null) {
            presentation.isEnabled = false
            return
        }

        val project: Project = elementAt.project

        // Perform PSI analysis within a read action because
        // setting `getActionUpdateThread()` with `ActionUpdateThread.BGT` tells the platform
        // to call our `update()` on a background thread, so we are not under a read.
        // `ReadAction.compute` is a safe way to do this. `ReadAction.run()` and `ReadAction.compute()`
        // require a blocking context and are always executed on the calling thread.
        val targets: Array<PsiElement> = ReadAction.compute<Array<PsiElement>, Throwable> {
            // According to the documentation, `DumbService.getInstance(project).isAlternativeResolveEnabled` is deprecated
            // and we should use `DumbService.getInstance(project).computeWithAlternativeResolveEnabled` instead
            DumbService.getInstance(project).computeWithAlternativeResolveEnabled<Array<PsiElement>, Throwable> {
                @Suppress("TestOnlyProblems")
                GotoDeclarationAction.findAllTargetElements(project, editor, offset)
            }
        }

        val teste = elementAt is NavigatablePsiElement || elementAt.parent is NavigatablePsiElement

        // We found valid `PsiElement` target, so we enable our action
        if (targets.isNotEmpty()) {
            presentation.isEnabled = true
            return
        } else {
            // We found no target for our own action, but maybe we can execute the `GotoDeclaration` action
            gotoDeclarationAction.update(e)
        }
    }

    /**
     * @param fileEditorManager
     * @param project
     * @param dataContext
     * @return If there are already split tabs, return the one on the right. If not, creates a vertically splitted tab on the right
     */
    private fun receiveNextWindowPane(
        project: Project,
        fileEditorManager: FileEditorManagerEx,
        dataContext: DataContext
    ): EditorWindow? {
        val activeWindowPane: EditorWindow = EditorWindow.DATA_KEY.getData(dataContext)
            ?: return null // Action invoked when no files are open. Do nothing.

        var nextWindowPane: EditorWindow? = fileEditorManager.getNextWindow(activeWindowPane)

        if (nextWindowPane == activeWindowPane) {
            val fileManagerEx: FileEditorManagerEx = FileEditorManagerEx.getInstanceEx(project)
            fileManagerEx.createSplitter(SwingConstants.VERTICAL, fileManagerEx.currentWindow)
            nextWindowPane = fileEditorManager.getNextWindow(activeWindowPane)
        }
        return nextWindowPane
    }

    /**
     * @return An array of `PsiElement` that is found by `GotoDeclarationAction.findAllTargetElements`
     * for the currently selected symbol at the caret `offset`.
     */
    private suspend fun getTargetsAtOffset(editor: Editor, project: Project, offset: Int): Array<PsiElement> {
        // We are not under a `ReadAction` read lock (e.g., during `AnAction.update()` with `ActionUpdateThread.EDT` enabled).
        // So we must compute `GotoDeclarationAction.findAllTargetElements` in the background
        // with the calling coroutine dispatcher and a read lock.
        return withBackgroundProgress(project, "Resolving References...") {
            // We are in a coroutine scope. So we should be prepared to be rescheduled and use `readAction()`
            // instead of `ReadAction.run()` or `ReadAction.compute()`
            readAction {
                // According to the documentation, `DumbService.getInstance(project).isAlternativeResolveEnabled` is deprecated
                // and we should use `DumbService.getInstance(project).computeWithAlternativeResolveEnabled` instead
                DumbService.getInstance(project).computeWithAlternativeResolveEnabled<Array<PsiElement>, Throwable> {
                    @Suppress("TestOnlyProblems")
                    GotoDeclarationAction.findAllTargetElements(project, editor, offset)
                }
            }
        }
    }
}
