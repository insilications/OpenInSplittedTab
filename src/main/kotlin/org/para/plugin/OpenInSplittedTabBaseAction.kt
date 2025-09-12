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
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.util.PsiUtilCore
import com.intellij.ui.awt.RelativePoint
import com.intellij.usages.Usage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.MessageFormat
import java.util.concurrent.Future
import javax.swing.SwingConstants
import javax.swing.Timer

class FindUsagesArguments {
    var position: String? = null

    var scope: String = "All Places"

    var expectedName: String? = null
}

/**
 * Base action to open the target symbol in a splitted tab
 * If a splitted tab to the right already exists, use it to navigate to the symbol. If not, open a new one.
 *
 * @param closePreviousTab True: if the target symbol is not in the same file of the current tab,
 * close the current file in the current tab (`EditorWindow`). False: open the symbol in a new splitted tab (`EditorWindow`).
 */
open class OpenInSplittedTabBaseAction(private val closePreviousTab: Boolean) : DumbAwareAction() {
    companion object {
        private val LOG = Logger.getInstance(OpenInSplittedTabBaseAction::class.java)

        private suspend fun startFindUsages(editor: Editor, project: Project, element: PsiElement): Boolean {
            val dumbServiceInstance: DumbService = DumbService.getInstance(project)
            if (dumbServiceInstance.isDumb) {
                LOG.info("startFindUsages - dumbServiceInstance.isDumb == true - element: ${element.text}")
                val action: AnAction = ActionManager.getInstance().getAction(ShowUsagesAction.ID)
                val name: String = action.templatePresentation.text

                @Suppress("DEPRECATION")
                dumbServiceInstance.showDumbModeNotification(ActionUtil.getUnavailableMessage(name, false))
            } else {
                LOG.info("startFindUsages - dumbServiceInstance.isDumb == false - element: ${element.text}")
                val edt = ApplicationManager.getApplication().isDispatchThread
                LOG.info("startFindUsages - edt: $edt")
                val popupPosition: RelativePoint = JBPopupFactory.getInstance().guessBestPopupLocation(editor)

//                val navigationCallback = Consumer<Usage> { usage ->
//                    val elementFound = usage.element
//                    if (elementFound != null && elementFound.isValid) {
//                        navigateToElementInSplit(project, element)
//                        LOG.info("startFindUsages - result: ${elementFound.text}")
//                    }
//                }
//                ShowUsagesAction.showUsages(
//                    project,
//                    editor,
//                    popupPosition,
//                    element,
//                    navigationCallback
//                )

//                var findUsagesFuture: Future<Collection<Usage>>? = null
//                val options = FindUsagesArguments()

//                withContext(Dispatchers.EDT) {
//                val findUsagesFuture = withBackgroundProgress(project, "Resolving Usages...") {
//                val findUsagesFuture = writeIntentReadAction {
//                        val title = ShowUsagesAction.getUsagesTitle(element)
//                val title = "TESTE"
//                    return@writeIntentReadAction ShowUsagesAction.startFindUsagesWithResult(element, popupPosition, editor, null, title)
//                }
//                val results = WriteAction.compute<Collection<Usage>, RuntimeException> {
//                    val findUsagesFuture: Future<MutableCollection<Usage>> =
//                        ShowUsagesAction.startFindUsagesWithResult(element, popupPosition, editor, null, "TESTE")
//
//                    return@compute findUsagesFuture.get() ?: return@compute null
//                }

                var findUsagesFuture: Future<Collection<Usage>>? = null
                findUsagesFuture =
                    writeIntentReadAction { ShowUsagesAction.startFindUsagesWithResult(element, popupPosition, editor, null) }
//                val findUsagesFuture: Future<MutableCollection<Usage>> =
//                    ShowUsagesAction.startFindUsagesWithResult(element, popupPosition, editor, null, "TESTE")

                LOG.info("startFindUsages - edt 1: $edt")

                if (findUsagesFuture == null) {
                    throw Exception("Can't find an element or search target under offset.")
                }

                LOG.info("startFindUsages - edt 2: $edt")
                withBackgroundProgress(project, "Resolving References...") {
                    readAction {
                        try {
                            val results = findUsagesFuture.get() // Blocks until the task is complete
                            for (result in results) {
                                LOG.info("startFindUsages - result: ${result.toString()}")
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
//                val results = findUsagesFuture!!.get()


//                withBackgroundProgress(project, "Resolving References...") {
//                    val results = findUsagesFuture.get() ?: return@withBackgroundProgress false
//
////                    val scope = readAction {
////                        FindUsagesOptions.findScopeByName(project, null, options.scope)
////                    }
////                    findUsagesFuture =
////                        writeIntentReadAction { ShowUsagesAction.startFindUsagesWithResult(element, popupPosition, editor, scope) }
////
////                    val results = findUsagesFuture?.get() ?: return@withContext true
////                    for (result in results) {
////                        LOG.info("startFindUsages - result: ${result.toString()}")
////                    }
////                }
////                val results = findUsagesFuture?.get() ?: return true
//                    for (result in results) {
//                        LOG.info("startFindUsages - result: ${result.toString()}")
//                    }
//                }
//                ShowUsagesAction.startFindUsages(element, popupPosition, editor)
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
            val targetElements: Array<PsiElement> = if (elements.isNotEmpty()) {
                elements
            } else {
                if (reference == null) {
                    PsiElement.EMPTY_ARRAY
                } else {
                    getSuggestCandidates(project, reference)
                }
            }

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

                    LOG.info("chooseAmbiguousTarget2 - targetElements.size > 1 - reference: $reference")

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
                // Requires EDT context
                scrollToTarget(element, nextWindowPane)
                return@PsiElementProcessor true
            }

            LOG.info("chooseAmbiguousTarget1")

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

        // Requires EDT context
        private fun scrollToTarget(target: PsiElement, nextWindowPane: EditorWindow) {
            // Defer the scrolling of the new tab, otherwise the scrolling may not work properly
            val delayingScrollToCaret = Timer(10) { _ ->
                if (!nextWindowPane.isShowing) {
                    scrollToTarget(target, nextWindowPane)
                } else {
//                    nextWindowPane.setAsCurrentWindow(true)

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
        val targetElement = TargetElementUtil.findTargetElement(editor, TargetElementUtil.getInstance().allAccepted)

        if (elementAt == null) {
            e.presentation.isEnabled = false
            return
        }

        LOG.info("actionPerformed - elementAt: ${elementAt.text}")

        if (targetElement != null) {
            LOG.info("actionPerformed - targetElement")
        } else {
            LOG.info("actionPerformed - targetElement == null")
        }

        val project: Project = elementAt.project

        @Suppress("UnstableApiUsage")
        currentThreadCoroutineScope().launch {
            val elements: Array<PsiElement> = getTargetsAtOffset(editor, project, offset)

            withContext(Dispatchers.EDT) {
                val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
                val nextWindowPane = receiveNextWindowPane(fileEditorManager) ?: return@withContext

                when {
                    // If we get exactly one `PsiElement` at `offset`, we will open it in a splitted tab
                    elements.size == 1 -> {
                        val element: PsiElement = elements[0]
                        fileEditorManager.currentWindow = nextWindowPane

                        LOG.info("actionPerformed - elements.size == 1 - element: ${element.text}")

                        // Use the internal method `openFileImpl2` instead of the public API method `openFile`
                        // because the latter opens a new window when the assigned shortcut for this action includes the shift key.
                        @Suppress("DEPRECATION_ERROR")
                        nextWindowPane.manager.openFileImpl2(nextWindowPane, element.containingFile.virtualFile, true)

                        if (closePreviousTab) {
                            // We save which file is currently open in the current tab
                            val fileToClose = fileEditorManager.currentFile

                            // We don't want to close the current file in the current tab if the target symbol
                            // is in the same file of the current tab
                            if (fileToClose != null && fileToClose != element.containingFile.virtualFile) {
                                LOG.info("actionPerformed - close current tab - fileToClose: ${fileToClose.name}")
                                fileEditorManager.currentWindow?.closeFile(fileToClose)
                            }
                        }
                        // Requires EDT context
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

                            LOG.info("actionPerformed - elements.size != 1 - 0")

                            if (element != null && startFindUsages(editor, project, element)) {
                                LOG.info("actionPerformed - elements.size != 1 - 1")
                                return@withContext
                            }

                            LOG.info("actionPerformed - elements.size != 1 - 2")
                            if (isKeywordUnderCaret(project, file, offset)) return@withContext
                            LOG.info("actionPerformed - elements.size != 1 - 3")
                        }
//                        val element: PsiElement? = GotoDeclarationAction.findElementToShowUsagesOf(editor, editor.caretModel.offset)
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

        if (TargetElementUtil.getInstance().isNavigatableSource(elementAt)) {
            e.presentation.isEnabled = true
            return
        } else {
            e.presentation.isEnabled = false
            return
        }
    }

    /**
     * @param fileEditorManager
     * @return If there are already splitted tabs, return the one on the right. If not, creates a vertically splitted tab on the right
     */
    private fun receiveNextWindowPane(
        fileEditorManager: FileEditorManagerEx,
    ): EditorWindow? {
        val activeWindowPane: EditorWindow = fileEditorManager.currentWindow ?: return null
        var nextWindowPane: EditorWindow? = fileEditorManager.getNextWindow(activeWindowPane)

        if (nextWindowPane == activeWindowPane) {
            // Create a new vertical split relative to the current window.
            nextWindowPane = activeWindowPane.split(SwingConstants.VERTICAL, true, virtualFile = null, true)
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
