package org.insilications.openinsplitted.codeInsight.navigation.actions

import com.intellij.codeInsight.navigation.GotoImplementationHandler
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.impl.RawNavigationRequest
import com.intellij.platform.backend.navigation.impl.SharedSourceNavigationRequest
import com.intellij.platform.backend.navigation.impl.SourceNavigationRequest
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.pom.Navigatable
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.insilications.openinsplitted.debug

class GotoImplementationHandlerSplitted : GotoImplementationHandler() {
    companion object {
        private val LOG: Logger = Logger.getInstance("org.insilications.openinsplitted")

    }

    private val requestFocus: NavigationOptions = NavigationOptions.requestFocus()

    @RequiresEdt
    private fun fetchDataContext(project: Project): DataContext? {
        val component = IdeFocusManager.getInstance(project).focusOwner
        return component?.let { DataManager.getInstance().getDataContext(it) }
    }

    @RequiresEdt
    override fun navigateToElement(project: Project?, descriptor: Navigatable) {
//        EDT.assertIsEdt()

        if (project == null) return
        IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation()
        val dataContext: DataContext? = fetchDataContext(project)

        LOG.debug { "0 GotoImplementationHandlerSplitted - navigateToElement - descriptor is ${descriptor::class.simpleName}" }
        // NavigationService.getInstance(project).navigate(
        runWithModalProgressBlocking(project, IdeBundle.message("progress.title.preparing.navigation")) {
            if (descriptor is OpenFileDescriptor) {
                LOG.debug { "1 GotoImplementationHandlerSplitted - navigateToElement - descriptor is ${descriptor::class.simpleName}" }
                withContext(Dispatchers.EDT) {
                    receiveNextWindowPane(project, descriptor.file)
                }
                project.serviceAsync<NavigationService>().navigate(descriptor, requestFocus, dataContext)
            } else {
                val navigationRequest: NavigationRequest? = readAction {
                    descriptor.navigationRequest()
                }
                if (navigationRequest != null) {
                    LOG.debug { "2 GotoImplementationHandlerSplitted - navigateToElement - navigationRequest is ${navigationRequest::class.simpleName}" }

                    when (navigationRequest) {
                        // 1. SharedSourceNavigationRequest is a subclass of SourceNavigationRequest, so check for it first.
                        is SharedSourceNavigationRequest -> {
                            LOG.debug { "3 GotoImplementationHandlerSplitted - navigateToElement - navigationRequest is SharedSourceNavigationRequest" }
                            withContext(Dispatchers.EDT) {
                                receiveNextWindowPane(project, navigationRequest.file)
                            }
                        }

                        // 2. Check for the superclass next.
                        // This branch will only be hit if the object is a SourceNavigationRequest but NOT a SharedSourceNavigationRequest.
                        is SourceNavigationRequest -> {
                            LOG.debug { "4 GotoImplementationHandlerSplitted - navigateToElement - navigationRequest is SourceNavigationRequest" }
                            withContext(Dispatchers.EDT) {
                                receiveNextWindowPane(project, navigationRequest.file)
                            }
                        }

                        // 3. Check for the sibling type. Its order relative to the others doesn't matter.
                        is RawNavigationRequest -> {
                            LOG.debug { "5 GotoImplementationHandlerSplitted - navigateToElement - navigationRequest is RawNavigationRequest" }
                            withContext(Dispatchers.EDT) {
                                receiveNextWindowPane(project, null)
                            }
                        }

                        else -> {
                            // Optional: Handle any other possible subtypes of NavigationRequest
                            LOG.debug { "6 GotoImplementationHandlerSplitted - navigateToElement - navigationRequest is Unknown NavigationRequest: ${navigationRequest::class.simpleName}" }
                        }
                    }

                    project.serviceAsync<NavigationService>().navigate(navigationRequest, requestFocus, dataContext)
                } else {
                    LOG.debug { "7 GotoImplementationHandlerSplitted - navigateToElement - navigationRequest is null" }
                    return@runWithModalProgressBlocking
                }
            }
        }

//        navigateBlocking(project, descriptor, NavigationOptions.requestFocus(), null)
    }

//    override fun getFeatureUsedKey(): String? {
//        return null
//    }

//    override fun invoke(project: Project, editor: Editor, psiFile: PsiFile) {
//        val featureId = featureUsedKey
//        if (featureId != null) {
//            FeatureUsageTracker.getInstance().triggerFeatureUsed(featureId)
//        }
//
//        try {
//            val gotoData = getSourceAndTargetElements(editor, psiFile)
//            val showPopupProcedure: Consumer<JBPopup> = Consumer { popup: JBPopup ->
//                if (!editor.isDisposed) {
//                    popup.showInBestPositionFor(editor)
//                }
//            }
//            if (gotoData != null) {
//                show(project, editor, psiFile, gotoData, showPopupProcedure)
//            } else {
//                chooseFromAmbiguousSources(
//                    editor,
//                    psiFile,
//                    Consumer { data: GotoData? -> show(project, editor, psiFile, data!!, showPopupProcedure) })
//            }
//        } catch (e: IndexNotReadyException) {
//            DumbService.getInstance(project).showDumbModeNotificationForFunctionality(
//                CodeInsightBundle.message("message.navigation.is.not.available.here.during.index.update"),
//                DumbModeBlockedFunctionality.GotoTarget
//            )
//        }
//    }

//    protected override fun show(
//        project: Project,
//        editor: Editor,
//        file: PsiFile,
//        gotoData: GotoData,
//        showPopup: Consumer<in JBPopup>
//    ) {
//        if (gotoData.isCanceled) return
//
//        val targets = gotoData.targets
//        val additionalActions = gotoData.additionalActions
//
//        if (targets.size == 0 && additionalActions.isEmpty()) {
//            HintManager.getInstance().showErrorHint(editor, getNotFoundMessage(project, editor, file))
//            return
//        }
//
//        showNotEmpty(project, gotoData, showPopup)
//    }

//    fun showNotEmpty(project: Project, gotoData: GotoData, showPopup: Consumer<in JBPopup>) {
//        val targets: Array<PsiElement> = gotoData.targets
//        val additionalActions: List<AdditionalAction> = gotoData.additionalActions
//
//        val finished = gotoData.listUpdaterTask == null || gotoData.listUpdaterTask.isFinished
//        if (targets.size == 1 && additionalActions.isEmpty() && finished) {
//            navigateToElement(targets[0])
//            return
//        }
//
//        val name = (gotoData.source as NavigationItem).getName()
//        val title = getChooserTitle(gotoData.source, name, targets.size, finished)
//
//        gotoData.initPresentations()
//        val allElements = ArrayList<ItemWithPresentation>(targets.size + additionalActions.size)
//        allElements.addAll(gotoData.items)
//        if (shouldSortTargets()) {
//            allElements.sortWith(createComparator(gotoData))
//        }
//        allElements.addAll(ContainerUtil.map(additionalActions) { action ->
//            ItemWithPresentation(action, TargetPresentation.builder(action.text).icon(action.icon).presentation())
//        })
//
//        val builder: IPopupChooserBuilder<ItemWithPresentation> = JBPopupFactory.getInstance().createPopupChooserBuilder(allElements)
//        val usageView: Ref<UsageView?> = Ref()
//        builder.setNamerForFiltering { item: ItemWithPresentation ->
//            if (item.item is AdditionalAction) {
//                return@setNamerForFiltering (item.item as AdditionalAction).text
//            }
//            item.presentation.presentableText
//        }.setTitle(title)
//        if (useEditorFont()) {
//            builder.setFont(EditorUtil.getEditorFont())
//        }
////        val renderer: GotoTargetRendererNew = GotoTargetRendererNew({ o -> (o as ItemWithPresentation).getPresentation() })
////        builder.setRenderer(renderer).setItemsChosenCallback
////        (com.intellij.util.Consumer { selectedElements: MutableSet<out ItemWithPresentation?>? ->
////            for (element in selectedElements!!) {
////                if (element.getItem() is AdditionalAction) {
////                    (element.getItem() as AdditionalAction).execute()
////                } else {
////                    navigate(project, element, Consumer { navigatable: Navigatable? -> navigateToElement(project, navigatable!!) })
////                }
////            }
////        }).withHintUpdateSupply
////        ().setMovable
////        (true).setCancelCallback
////        (Computable {
////            val task: BackgroundUpdaterTaskBase<ItemWithPresentation?>? = gotoData.listUpdaterTask
////            if (task != null) {
////                task.cancelTask()
////            }
////            true
////        }).setCouldPin
////        (Processor { popup1: JBPopup? ->
////            usageView.set(
////                FindUtil.showInUsageView(
////                    gotoData.source, gotoData.targets,
////                    getFindUsagesTitle(gotoData.source, name, gotoData.targets.size),
////                    gotoData.source.getProject()
////                )
////            )
////            popup1!!.cancel()
////            false
////        }).setAdText
////        (getAdText(gotoData.source, targets.size))
////        val popup = builder.createPopup()
////
////        val pane = if (builder is PopupChooserBuilder<*>) builder.getScrollPane() else null
////        if (pane != null) {
////            if (!ExperimentalUI.isNewUI()) {
////                pane.setBorder(null)
////            }
////            pane.setViewportBorder(null)
////        }
////
////        if (gotoData.listUpdaterTask != null) {
////            val alarm = Alarm(popup)
////            alarm.addRequest(Runnable { showPopup.accept(popup) }, 300)
////            gotoData.listUpdaterTask.init(popup, builder.getBackgroundUpdater(), usageView)
////            ProgressManager.getInstance().run(gotoData.listUpdaterTask)
////        } else {
////            showPopup.accept(popup)
////        }
////        if (ApplicationManager.getApplication().isUnitTestMode()) {
////            popup.closeOk(null)
////        }
//    }


//    override fun getSourceAndTargetElements(editor: Editor, file: PsiFile?): GotoData? {
//        var offset = editor.caretModel.offset
//        var source = TargetElementUtil.getInstance().findTargetElement(editor, ImplementationSearcher.getFlags(), offset)
//        if (source == null) {
//            offset = tryGetNavigationSourceOffsetFromGutterIcon(editor, IdeActions.ACTION_GOTO_IMPLEMENTATION)
//            if (offset >= 0) {
//                source = TargetElementUtil.getInstance().findTargetElement(editor, ImplementationSearcher.getFlags(), offset)
//            }
//        }
//        if (source == null) return null
//        return createDataForSource(editor, offset, source)
//    }


//    override fun createDataForSource(editor: Editor, offset: Int, source: PsiElement): GotoData {
//        val targets: Array<PsiElement?>? = findTargets(editor, offset, source)
//        if (targets == null) {
//            //canceled search
//            val data = GotoData(source, PsiElement.EMPTY_ARRAY, mutableListOf<AdditionalAction>())
//            data.isCanceled = true
//            return data
//        }
//        val reference = TargetElementUtil.findReference(editor, offset)
//        val gotoData = GotoData(source, targets, mutableListOf<AdditionalAction>())
//        gotoData.listUpdaterTask = object : ImplementationsUpdaterTask(gotoData, editor, offset, reference) {
//            override fun onSuccess() {
//                super.onSuccess()
//                val oneElement = getTheOnlyOneElement()
//                if (oneElement == null || oneElement.getItem() !is SmartPsiElementPointer<*>) {
//                    return
//                }
//                val success: Boolean
//                SlowOperations.knownIssue("IJPL-162968").use { ignore ->
//                    success = navigateToElement(o.getElement())
//                }
//                if (success) {
//                    myPopup.cancel()
//                }
//            }
//        }
//        return gotoData
//    }
}