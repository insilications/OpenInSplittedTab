package org.insilications.openinsplitted.find.actions

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.find.FindBundle
import com.intellij.find.FindManager
import com.intellij.find.FindUsagesSettings
import com.intellij.find.actions.ShowUsagesAction
import com.intellij.find.actions.ShowUsagesAction.CLOSE_REASON_RESET_FILTERS
import com.intellij.find.actions.ShowUsagesAction.areAllUsagesInOneLine
import com.intellij.find.actions.ShowUsagesActionHandler
import com.intellij.find.actions.UsageNavigation
import com.intellij.find.findUsages.FindUsagesHandlerBase
import com.intellij.find.findUsages.FindUsagesHandlerFactory.OperationMode.USAGES_WITH_DEFAULT_OPTIONS
import com.intellij.find.findUsages.FindUsagesManager
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.find.findUsages.PersistentFindUsagesOptions
import com.intellij.find.impl.FindManagerImpl
import com.intellij.find.usages.api.SearchTarget
import com.intellij.ide.DataManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IntRef
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.WindowStateService
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.actions.VcsAnnotateUtil.getEditorFor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.ui.ColorUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBSplitter
import com.intellij.ui.ScreenUtil
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.AbstractPopup
import com.intellij.ui.scale.JBUIScale
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewBundle
import com.intellij.usageView.UsageViewUtil
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.impl.GroupNode
import com.intellij.usages.impl.NullUsage
import com.intellij.usages.impl.UsageNode
import com.intellij.usages.impl.UsageViewImpl
import com.intellij.usages.impl.UsageViewManagerImpl
import com.intellij.usages.rules.UsageFilteringRuleProvider
import com.intellij.util.SlowOperations
import com.intellij.util.SmartList
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.EdtScheduler
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.JBUI
import org.insilications.openinsplitted.debug
import org.insilications.openinsplitted.find.usages.impl.Psi2UsageInfo2UsageAdapter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.gradleTooling.get
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Rectangle
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Method
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Predicate
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.table.TableCellRenderer
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.max
import kotlin.math.min

class ShowUsagesActionSplitted {
    companion object {
        private val LOG: Logger = Logger.getInstance("org.insilications.openinsplitted")

        private const val ourPopupDelayTimeout: Int = 300
        private const val PREVIEW_PROPERTY_KEY: String = "ShowUsagesActions.previewPropertyKey"
        private const val DIMENSION_SERVICE_KEY: String = "ShowUsagesActions.dimensionServiceKey"
        private const val PLATFORM_SHOW_USAGES_TABLE_CELL_RENDERER = "com.intellij.find.actions.ShowUsagesTableCellRenderer"


        private val showUsagesTableCellRendererCachedConstructor: ((Predicate<in Usage>, AtomicInteger, SearchScope) -> TableCellRenderer)? by lazy(
            LazyThreadSafetyMode.PUBLICATION
        ) {
            try {
                val klass: Class<*> = Class.forName(PLATFORM_SHOW_USAGES_TABLE_CELL_RENDERER)
                val constructorMethod = klass.getConstructor(Predicate::class.java, AtomicInteger::class.java, SearchScope::class.java).apply {
                    isAccessible = true
                }

                val constructorHandle: MethodHandle = MethodHandles.lookup().unreflectConstructor(constructorMethod).asType(
                    MethodType.methodType(
                        klass,
                        Predicate::class.java,
                        AtomicInteger::class.java,
                        SearchScope::class.java
                    )
                );

                { originUsageCheck: Predicate<in Usage>, outOfScopeUsages: AtomicInteger, searchScope: SearchScope ->
                    constructorHandle.invokeWithArguments(originUsageCheck, outOfScopeUsages, searchScope) as TableCellRenderer
                }
            } catch (t: Throwable) {
                LOG.warn("Failed to resolve ShowUsagesTableCellRenderer(Predicate<? super Usage>, AtomicInteger, SearchScope)  class.", t)
                null
            }
        }

        private val createShowTargetUsagesActionHandlerCachedInvoker: ((Project, SearchScope, SearchTarget) -> ShowUsagesActionHandler)?
                by lazy(LazyThreadSafetyMode.PUBLICATION) {
                    try {
                        // 1. Get the private method from the ShowUsagesAction class.
                        // We use `getDeclaredMethod` because the method is not public.
                        val method: Method = ShowUsagesAction::class.java.getDeclaredMethod(
                            "createActionHandler",
                            Project::class.java,
                            SearchScope::class.java,
                            SearchTarget::class.java
                        ).apply {
                            // 2. Make it accessible. This is crucial for private members.
                            isAccessible = true
                        }

                        // 2. Create a `MethodHandle` with a specific type using `.asType(...)`.
                        // This provides a runtime guarantee and acts as an assertion during initialization.
                        // Additionally, the method is static, we do not need to bind it to an instance.
                        // By creating an adapted handle with a specific type, you provide more information
                        // to the JVM's JIT compiler, which can lead to better performance optimizations
                        // compared to a completely generic handle.
                        val handle: MethodHandle = MethodHandles.lookup().unreflect(method).asType(
                            MethodType.methodType(
                                ShowUsagesActionHandler::class.java,
                                Project::class.java,
                                SearchScope::class.java,
                                SearchTarget::class.java
                            )
                        );

                        // 3. Return a strongly-typed lambda that wraps the handle.
                        // The result is cast to the known return type for type safety.
                        { project: Project, searchScope: SearchScope, target: SearchTarget ->
                            // Call the handle. Because `invokeWithArguments` is statically typed to
                            // return `Object`, we still need a cast to satisfy the Kotlin compiler.
                            // This cast is safe because of the `.asType(...)` guarantee above.
                            handle.invokeWithArguments(project, searchScope, target) as ShowUsagesActionHandler
                        }
                    } catch (t: Throwable) {
                        LOG.warn("Failed to resolve ShowUsagesAction.createActionHandler via reflection.", t)
                        // If reflection fails for any reason, the invoker will be null.
                        null
                    }
                }

        private val createActionHandlerCachedInvoker: ((FindUsagesHandlerBase, FindUsagesOptions, String) -> ShowUsagesActionHandler)?
                by lazy(LazyThreadSafetyMode.PUBLICATION) {
                    try {
                        // 1. Get the private method from the ShowUsagesAction class.
                        // We use `getDeclaredMethod` because the method is not public.
                        val method = ShowUsagesAction::class.java.getDeclaredMethod(
                            "createActionHandler",
                            FindUsagesHandlerBase::class.java,
                            FindUsagesOptions::class.java,
                            String::class.java
                        ).apply {
                            // 2. Make it accessible. This is crucial for private members.
                            isAccessible = true
                        }

                        // 2. Create a `MethodHandle` with a specific type using `.asType(...)`.
                        // This provides a runtime guarantee and acts as an assertion during initialization.
                        // Additionally, the method is static, we do not need to bind it to an instance.
                        // By creating an adapted handle with a specific type, you provide more information
                        // to the JVM's JIT compiler, which can lead to better performance optimizations
                        // compared to a completely generic handle.
                        val handle: MethodHandle = MethodHandles.lookup().unreflect(method).asType(
                            MethodType.methodType(
                                ShowUsagesActionHandler::class.java,
                                FindUsagesHandlerBase::class.java,
                                FindUsagesOptions::class.java,
                                String::class.java
                            )
                        );

                        // 3. Return a strongly-typed lambda that wraps the handle.
                        // The result is cast to the known return type for type safety.
                        { handler, options, title ->
                            // Call the handle. Because `invokeWithArguments` is statically typed to
                            // return `Object`, we still need a cast to satisfy the Kotlin compiler.
                            // This cast is safe because of the `.asType(...)` guarantee above.
                            handle.invokeWithArguments(handler, options, title) as ShowUsagesActionHandler
                        }
                    } catch (t: Throwable) {
                        LOG.warn("Failed to resolve ShowUsagesAction.createActionHandler(FindUsagesHandlerBase, FindUsagesOptions, String) via reflection.", t)
                        // If reflection fails for any reason, the invoker will be null.
                        null
                    }
                }

        @ApiStatus.Internal
        fun showUsages(
            project: Project,
            targetVariants: List<Any>,
            popupPosition: RelativePoint,
            editor: Editor,
            searchScope: SearchScope
        ) {
            LOG.debug { "ShowUsagesActionSplitted - showUsages" }
//            SlowOperations.startSection(SlowOperations.ACTION_PERFORM).use { ignored ->
            findShowUsages(
                project, editor, popupPosition, targetVariants, FindBundle.message("show.usages.ambiguous.title"),
                createVariantHandler(project, editor, popupPosition, searchScope)
            )
//            }
        }

        private fun createVariantHandler(
            project: Project,
            editor: Editor,
            popupPosition: RelativePoint,
            searchScope: SearchScope
        ): UsageVariantHandler {
            return object : UsageVariantHandler {
                override fun handleTarget(target: SearchTarget) {
                    showElementUsages(
                        project, searchScope, target,
                        ShowUsagesParameters.initial(project, editor, popupPosition)
                    )
                }

                override fun handlePsi(element: PsiElement) {
                    startFindUsages(element, popupPosition, editor)
                }
            }
        }

        fun startFindUsages(element: PsiElement, popupPosition: RelativePoint, editor: Editor?) {
            LOG.debug { "ShowUsagesActionSplitted - startFindUsages" }
            // TODO: Implement my custom `startFindUsages` functionality here.
            ReadAction.nonBlocking(Callable { getUsagesTitle(element) }
            ).expireWhen { editor != null && editor.isDisposed }
                .finishOnUiThread(ModalityState.nonModal()) { title ->
                    startFindUsagesWithResult(element, popupPosition, editor, null, title)
                }
                .submit(AppExecutorUtil.getAppExecutorService())
        }

        @ApiStatus.Internal
        fun startFindUsagesWithResult(
            element: PsiElement,
            popupPosition: RelativePoint,
            editor: Editor?,
            scope: SearchScope?,
            @Nls title: String
        ): Future<Collection<Usage>>? {
            val project: Project = element.project
            val findUsagesManager: FindUsagesManager = (FindManager.getInstance(project) as FindManagerImpl).findUsagesManager
            val handler: FindUsagesHandlerBase?
            val actionHandler: ShowUsagesActionHandler
            val options: FindUsagesOptions
            // The 'run' block computes the actionHandler. If it returns null (e.g., no handler found),
            // the elvis operator `?: return null` will cause the entire function to return null.
//            val actionHandler: ShowUsagesActionHandler = run {
            // The 'use' function is Kotlin's equivalent of Java's try-with-resources,
            // ensuring the AccessToken from SlowOperations is always closed.
            SlowOperations.startSection(SlowOperations.ACTION_PERFORM).use {
                handler = findUsagesManager.getFindUsagesHandler(element, USAGES_WITH_DEFAULT_OPTIONS)
                if (handler == null) return null
                @Suppress("DEPRECATION")
                val dataContext: DataContext = DataManager.getInstance().dataContext
                options = handler.getFindUsagesOptions(dataContext)
                if (options is PersistentFindUsagesOptions) {
                    options.setDefaults(project)
                }
                if (scope != null) {
                    options.searchScope = scope
                } else {
                    options.searchScope = FindUsagesOptions.findScopeByName(project, dataContext, FindUsagesSettings.getInstance().defaultScopeName)
                }
                val createActionHandlerCachedInvokerInvoker: (FindUsagesHandlerBase, FindUsagesOptions, String) -> ShowUsagesActionHandler =
                    createActionHandlerCachedInvoker ?: return null
                try {
                    actionHandler = createActionHandlerCachedInvokerInvoker(handler, options, title)
                } catch (t: Throwable) {
                    LOG.warn("Failed to invoke gotoDeclarationOrUsages", t)
                    return null
                }
            }

            return showElementUsagesWithResult(
                project,
                ShowUsagesParameters.initial(project, editor, popupPosition),
                actionHandler
            )
        }

        private fun showElementUsagesWithResult(
            project: Project,
            parameters: ShowUsagesParameters,
            actionHandler: ShowUsagesActionHandler
        ): Future<Collection<Usage>> {
            ThreadingAssertions.assertEventDispatchThread()
            val usageView: UsageViewImpl = actionHandler.createUsageView(project)
            return showElementUsagesWithResult(project, parameters, actionHandler, usageView)
        }

        @OptIn(ExperimentalAtomicApi::class)
        fun showElementUsagesWithResult(
            project: Project,
            parameters: ShowUsagesParameters,
            actionHandler: ShowUsagesActionHandler,
            usageView: UsageViewImpl
        ): Future<Collection<Usage>> {
            // Tracing spans (same structure as Java version)
//            val findUsageSpan = myFindUsagesTracer.spanBuilder("findUsages").startSpan()
//            val opentelemetryScope = findUsageSpan.makeCurrent()
//            val popupSpan = myFindUsagesTracer.spanBuilder("findUsage_popup").startSpan()
//            val firstUsageSpan = myFindUsagesTracer.spanBuilder("findUsages_firstUsage").startSpan()

//            val project = parameters.project

//            ReadAction.nonBlocking<MutableList<EventPair<*>>> { actionHandler.eventData }
//                .submit(AppExecutorUtil.getAppExecutorService())
//                .onSuccess { eventData ->
//                    UsageViewStatisticsCollector.logSearchStarted(
//                        project,
//                        usageView,
//                        CodeNavigateSource.ShowUsagesPopup,
//                        eventData
//                    )
//                }

            val searchScope = actionHandler.selectedScope
            val outOfScopeUsages = AtomicInteger()
            val manuallyResized = AtomicBoolean()
            val preselectedRow = Ref<UsageNode>()

            val originUsageCheck: Predicate<in Usage> = SlowOperations.knownIssue("IJPL-162330").use {
                originUsageCheck(parameters.editor)
            }

            val showUsagesTableCellRendererConstructor: (Predicate<in Usage>, AtomicInteger, SearchScope) -> TableCellRenderer =
                showUsagesTableCellRendererCachedConstructor ?: return CompletableFuture<Collection<Usage>>()
            val renderer: TableCellRenderer = showUsagesTableCellRendererConstructor(originUsageCheck, outOfScopeUsages, searchScope)
            val table = ShowUsagesTable(renderer, usageView)

            // Force initial tree traversal (mirrors original code path)
            addUsageNodes(usageView.root, ArrayList<UsageNode>())

            val usages: MutableList<Usage> = ArrayList()
            val visibleUsages: MutableSet<Usage> = mutableSetOf()
            table.setTableModel(SmartList<UsageNode>(StringNode(UsageViewBundle.message("progress.searching"))))

            val showUsagesPopupData = ShowUsagesPopupData(parameters, table, actionHandler, usageView)

            val itemChosenCallback = table.prepareTable(
                showMoreUsagesRunnable(parameters, actionHandler),
                showUsagesInMaximalScopeRunnable(parameters, actionHandler, showUsagesPopupData),
                actionHandler,
                parameters
            )

            val tableResizer = Consumer<AbstractPopup?> { popup ->
                if (popup != null && popup.isVisible && !manuallyResized.get()) {
                    val properties = PropertiesComponent.getInstance(project)
                    val dataSize = table.model.rowCount
                    setPopupSize(
                        table,
                        popup,
                        parameters.popupPosition,
                        parameters.minWidth,
                        properties.isValueSet(PREVIEW_PROPERTY_KEY),
                        dataSize
                    )
                }
            }

            val popup = createUsagePopup(usageView, showUsagesPopupData, itemChosenCallback, tableResizer)
            popup.addResizeListener({ manuallyResized.set(true) }, popup)
            val popupShownTimeRef: Ref<Long> = Ref.create<Long>()
//            popup.addListener(object : JBPopupListener {
//                override fun onClosed(event: LightweightWindowEvent) {
//                    val node = getSelectedUsageNode(table)
//                    val usage = node?.usage
//                    val usageAdapter = ObjectUtils.tryCast(usage, UsageInfo2UsageAdapter::class.java)
//                    val usageInfo = usageAdapter?.usageInfo
//                    val preselectedRowNumber = getRowNumber(preselectedRow.get(), table)
//                    val selectedRowNumber = getRowNumber(node, table)
//                    val popupShownTime = popupShownTimeRef.get()
//                    val durationTime = popupShownTime?.let { System.currentTimeMillis() - it }
//
//                    ReadAction.nonBlocking<List<EventPair<*>>> {
//                        actionHandler.buildFinishEventData(usageInfo)
//                    }.submit(AppExecutorUtil.getAppExecutorService()).onSuccess { finishEventData ->
//                        UsageViewStatisticsCollector.logPopupClosed(
//                            project,
//                            usageView,
//                            /* ok = */ event.isOk,
//                            preselectedRowNumber,
//                            selectedRowNumber,
//                            visibleUsages.size,
//                            durationTime,
//                            finishEventData
//                        )
//                    }
//                }
//            })

            val indicator: ProgressIndicator = ProgressIndicatorBase()
            if (!popup.isDisposed) {
                Disposer.register(popup, usageView)
                Disposer.register(popup) { indicator.cancel() }

                // Delay popup to avoid flicker (same semantics)
                EdtScheduler.getInstance().schedule(ourPopupDelayTimeout) {
                    if (!usageView.isDisposed) {
                        showPopupIfNeedTo(popup, parameters.popupPosition, popupShownTimeRef)
//                        popupSpan.end()
                    }
                }
            }

            val USAGES_OUTSIDE_SCOPE_NODE = UsageNode(null, table.USAGES_OUTSIDE_SCOPE_SEPARATOR)
            val MORE_USAGES_SEPARATOR_NODE = UsageNode(null, table.MORE_USAGES_SEPARATOR)

            val rebuildRunnable = Runnable {
                if (popup.isDisposed) return@Runnable

                val nodes = ArrayList<UsageNode>(usages.size)
                val copy: List<Usage>
                synchronized(usages) {
                    if (!popup.isVisible &&
                        (usages.isEmpty() || !showPopupIfNeedTo(popup, parameters.popupPosition, popupShownTimeRef))
                    ) {
                        return@Runnable
                    }
                    addUsageNodes(usageView.root, nodes)
                    copy = ArrayList(usages)
                }

                val shouldShowMoreSeparator = copy.contains(table.MORE_USAGES_SEPARATOR)
                if (shouldShowMoreSeparator) {
                    nodes.add(MORE_USAGES_SEPARATOR_NODE)
                }
                val hasOutsideScopeUsages = copy.contains(table.USAGES_OUTSIDE_SCOPE_SEPARATOR)
                if (hasOutsideScopeUsages && !shouldShowMoreSeparator) {
                    nodes.add(USAGES_OUTSIDE_SCOPE_NODE)
                }

                val data: ArrayList<UsageNode> = ArrayList(nodes)
                val filteredOutCount = usageView.filteredOutNodeCount
                if (filteredOutCount != 0) {
                    val filteringActions = popup.getUserData(DefaultActionGroup::class.java) ?: return@Runnable
                    val unselectedActions = filteringActions
                        .getChildren(ActionManager.getInstance())
                        .asSequence()
                        .filterIsInstance<ToggleAction>()
                        .filter { !it.isSelected(fakeEvent(it)) }
                        .filter { !StringUtil.isEmpty(it.templatePresentation.text) }
                        .toList()

                    data.add(object : FilteredOutUsagesNode(
                        table.USAGES_FILTERED_OUT_SEPARATOR,
                        UsageViewBundle.message("usages.were.filtered.out", filteredOutCount),
                        UsageViewBundle.message("usages.were.filtered.out.tooltip")
                    ) {
                        override fun onSelected() {
                            actionHandler.beforeClose(CLOSE_REASON_RESET_FILTERS)
                            toggleFilters(unselectedActions)
                            showElementUsagesWithResult(project, parameters, actionHandler)
                        }
                    })
                }

                data.sortWith(UsageNodeComparator(table))

                val hasMore = shouldShowMoreSeparator || hasOutsideScopeUsages
                val totalCount = copy.size
                val visibleCount = totalCount - filteredOutCount
                showUsagesPopupData.header.setStatusText(hasMore, visibleCount, totalCount)
                rebuildTable(
                    project,
                    originUsageCheck,
                    data,
                    table,
                    popup,
                    parameters.popupPosition,
                    parameters.minWidth,
                    manuallyResized
                )
                preselectedRow.set(getSelectedUsageNode(table))
            }

            val pingEDT = PingEDT(
                "Rebuild popup in EDT",
                { popup.isDisposed },
                100,
                rebuildRunnable
            )

            val messageBusConnection = project.messageBus.connect(usageView)
            messageBusConnection.subscribe(
                UsageFilteringRuleProvider.RULES_CHANGED
            ) {
                rulesChanged(usageView, pingEDT, popup)
            }

            val firstUsageAddedTS = AtomicLong()
            val tooManyResults = AtomicBoolean()
            val everythingScope = GlobalSearchScope.everythingScope(project)

            val collect = Processor<Usage> { usage ->
                if (!UsageViewManagerImpl.isInScope(usage, searchScope, everythingScope)) {
                    if (outOfScopeUsages.getAndIncrement() == 0) {
                        visibleUsages.add(USAGES_OUTSIDE_SCOPE_NODE.usage)
                        usages.add(table.USAGES_OUTSIDE_SCOPE_SEPARATOR)
                    }
                    return@Processor true
                }
                synchronized(usages) {
//                    firstUsageSpan.end()
                    if (visibleUsages.size >= parameters.maxUsages) {
                        tooManyResults.set(true)
                        return@Processor false
                    }

                    val nodes = ReadAction.compute<UsageNode?, Throwable> { usageView.doAppendUsage(usage) }
                    usages.add(usage)
                    firstUsageAddedTS.compareAndSet(0, System.nanoTime())

                    if (nodes != null) {
                        visibleUsages.add(nodes.usage)
                        var continueSearch = true
                        if (visibleUsages.size == parameters.maxUsages) {
                            visibleUsages.add(MORE_USAGES_SEPARATOR_NODE.usage)
                            usages.add(table.MORE_USAGES_SEPARATOR)
                            continueSearch = false
                        }
                        pingEDT.ping()
                        return@Processor continueSearch
                    }
                }
                true
            }

            val usageSearcher = actionHandler.createUsageSearcher()
            val searchStarted = System.nanoTime()
            val result = CompletableFuture<Collection<Usage>>()

            FindUsagesManager.startProcessUsages(
                indicator,
                project,
                usageSearcher,
                collect
            ) {
                ApplicationManager.getApplication().invokeLater(
                    {
                        showUsagesPopupData.header.disposeProcessIcon()
                        pingEDT.ping()
                        synchronized(usages) {
//                            findUsageSpan.setAttribute("number", usages.size())
                            if (visibleUsages.isEmpty()) {
                                if (usages.isEmpty()) {
                                    val hint = UsageViewBundle.message("no.usages.found.in", searchScope.displayName)
                                    cancelAndShowHint(popup, false, hint, parameters, actionHandler)
                                }
                                // else: all usages filtered out
                            } else if (visibleUsages.size == 1 && actionHandler.navigateToSingleUsageImmediately()) {
                                val onReady = BiConsumer<Usage, String> { usage, hint ->
                                    val newEditor = getEditorFor(usage)
                                    if (newEditor != null && parameters.editor != null) {
                                        cancelAndShowHint(popup, false, hint, parameters, actionHandler)
                                    } else {
                                        cancel(popup)
                                    }
                                }

                                if (usages.size == 1) {
                                    val usage = visibleUsages.iterator().next()
                                    if (usage == table.USAGES_OUTSIDE_SCOPE_SEPARATOR) {
                                        val hint = UsageViewManagerImpl.outOfScopeMessage(outOfScopeUsages.get(), searchScope)
                                        cancelAndShowHint(popup, true, hint, parameters, actionHandler)
                                    } else {
                                        val hint = UsageViewBundle.message("show.usages.only.usage", searchScope.displayName)
                                        UsageNavigation.getInstance(project).navigateAndHint(
                                            project,
                                            usage
                                        ) { onReady.accept(usage, hint) }
                                    }
                                } else {
                                    check(usages.size > 1) { usages }
                                    val visibleUsage = visibleUsages.iterator().next()
                                    if (areAllUsagesInOneLine(visibleUsage, usages)) {
                                        val hint = UsageViewBundle.message(
                                            "all.usages.are.in.this.line",
                                            usages.size,
                                            searchScope.displayName
                                        )
                                        UsageNavigation.getInstance(project).navigateAndHint(
                                            project,
                                            visibleUsage
                                        ) { onReady.accept(visibleUsage, hint) }
                                    }
                                }
                            }
                            result.complete(usages)
                        }

//                        findUsageSpan.end()
                        val current = System.nanoTime()
                        val firstUsageTimestamp = firstUsageAddedTS.get()
                        val durationFirstResults =
                            if (firstUsageTimestamp != 0L)
                                TimeUnit.NANOSECONDS.toMillis(firstUsageTimestamp - searchStarted)
                            else
                                -1L

//                        UsageViewManagerImpl.informRankerMlService(
//                            project,
//                            usages,
//                            FileRankerMlService.CallSource.SHOW_USAGES
//                        )
//                        UsageViewStatisticsCollector.logSearchFinished(
//                            project,
//                            usageView,
//                            actionHandler.targetClass,
//                            searchScope,
//                            actionHandler.targetLanguage,
//                            visibleUsages.size,
//                            durationFirstResults,
//                            TimeUnit.NANOSECONDS.toMillis(current - searchStarted),
//                            tooManyResults.get(),
//                            indicator.isCanceled,
//                            CodeNavigateSource.ShowUsagesPopup
//                        )
                    },
                    project.disposed
                )
            }

//            opentelemetryScope.close()
            actionHandler.afterOpen(popup)
            return result
        }

        @ApiStatus.Experimental
        fun showElementUsages(project: Project, searchScope: SearchScope, target: SearchTarget, parameters: ShowUsagesParameters) {
            val createShowTargetUsagesActionHandlerInvoker: (Project, SearchScope, SearchTarget) -> ShowUsagesActionHandler =
                createShowTargetUsagesActionHandlerCachedInvoker ?: return
            val showTargetUsagesActionHandler: ShowUsagesActionHandler = try {
                createShowTargetUsagesActionHandlerInvoker(project, searchScope, target)
            } catch (t: Throwable) {
                LOG.warn("Failed to invoke gotoDeclarationOrUsages", t)
                return
            }
            LOG.debug { "ShowUsagesActionSplitted - showElementUsages" }
            // TODO: Implement my custom `showElementUsagesWithResult` functionality here.
            // showElementUsagesWithResult(parameters, actionHandler)
        }


        private fun getLocationString(@Nls locationString: String): HtmlChunk {
            val color: Color = if (ExperimentalUI.isNewUI()) JBUI.CurrentTheme.ContextHelp.FOREGROUND else SimpleTextAttributes.GRAY_ATTRIBUTES.fgColor
            return HtmlChunk.text(locationString).wrapWith("font").attr("color", "#" + ColorUtil.toHex(color))
        }

        @Nls
        private fun getUsagesTitle(element: PsiElement): String {
            val builder = HtmlBuilder()

            var type = HtmlChunk.text(StringUtil.capitalize(UsageViewUtil.getType(element)))
            if (ExperimentalUI.isNewUI()) {
                type = type.bold()
            }

            builder.append(type).nbsp().append(HtmlChunk.text(UsageViewUtil.getLongName(element)).bold())

            if (element is NavigationItem) {
                val itemPresentation: ItemPresentation? = (element as NavigationItem).presentation
                if (itemPresentation != null) {
                    val locationString: String? = itemPresentation.locationString
                    if (locationString != null && StringUtil.isNotEmpty(locationString)) {
                        builder.nbsp().append(getLocationString(locationString))
                    }
                }
            }

            return builder.toString()
        }

        private fun addUsageNodes(root: GroupNode, outNodes: MutableList<UsageNode>) {
            for (node in root.usageNodes) {
                node.setParent(root)
                outNodes.add(node)
            }
            for (groupNode in root.subGroups) {
                groupNode.setParent(root)
                addUsageNodes(groupNode, outNodes)
            }
        }

        private fun originUsageCheck(editor: Editor?): Predicate<in Usage> {
            if (editor != null) {
                // First, try to find a specific PSI reference at the caret.
                val reference: PsiReference? = TargetElementUtil.findReference(editor)
                if (reference != null) {
                    val originUsageInfo = UsageInfo(reference)
                    // Return a predicate that checks for a usage wrapping the exact same origin UsageInfo.
                    return Predicate { usage ->
                        // In Kotlin, `instanceof` is replaced by an `is` check, which also smart-casts the variable.
                        // Java's `getUsageInfo()` becomes property access `usageInfo`, and `.equals()` becomes the `==` operator.
                        usage is UsageInfo2UsageAdapter && usage.usageInfo == originUsageInfo
                    }
                }

                // Fallback: If no reference is found, use file, line, and offset for matching.
                val file: VirtualFile? = editor.virtualFile
                val offset: Int = editor.caretModel.offset
                if (file == null || offset <= 0) {
                    // If file or offset is invalid, return a predicate that never matches.
                    return Predicate { false }
                }

                val line: Int = editor.document.getLineNumber(offset)
                return Predicate { usage ->
                    // This predicate checks if a given usage is located at the original caret position.
                    if (usage is Psi2UsageInfo2UsageAdapter) {
                        // Fast path: check if the usage is in the same file and on the same line.
                        // Java's `adapter.getFile()` becomes property access `usage.file`.
                        if (line != usage.line || file != usage.file) {
                            // A labeled return is used to exit only the lambda, not the whole function.
                            return@Predicate false
                        }

                        // If file and line match, perform a more precise check on the text range.
                        for (info in usage.mergedInfos) {
                            // The original Java code used a custom `doIfNotNull` helper.
                            // The idiomatic Kotlin equivalent is the safe-call operator `?.` with a `let` block.
                            val range: Segment? = info.psiFileRange?.let { psiFileRange ->
                                // `ReadAction.compute` is called to safely access PSI elements from a background thread.
                                // The Java method reference `it::getRange` becomes a lambda with property access `{ it.range }`.
                                ReadAction.compute<Segment, Throwable> { psiFileRange.range }
                            }
                            if (range != null && range.containsInclusive(offset)) {
                                // Found a precise match.
                                return@Predicate true
                            }
                        }
                    }

                    // If the usage is not the right type, or no matching range was found, it's not a match.
                    // In a Kotlin lambda, the last expression is its return value.
                    false
                }
            }
            // If no editor is provided, return a predicate that never matches.
            return Predicate { false }
        }


        class StringNode(@Nls string: String) :
            UsageNode(null, NullUsage.INSTANCE) {

            @Nls
            val myString: String = string

            @Nls
            fun getString(): String = myString

            override fun toString(): String = myString
        }

        private fun isCodeWithMeClientInstance(popup: JBPopup): Boolean {
            val content: JComponent = popup.getContent()
            return content.getClientProperty("THIN_CLIENT") != null
        }

        private fun calcMaxWidth(table: JTable): Int {
            // In Java, the parameter was annotated with @NotNull.
            // Kotlin's type system handles this with the non-nullable type `JTable`,
            // providing compile-time null safety.
            val colsNum = table.columnModel.columnCount

            var totalWidth = 0
            for (col in 0 until colsNum) {
                val column = table.columnModel.getColumn(col)
                val preferred = column.preferredWidth
                val width = max(preferred, columnMaxWidth(table, col))
                totalWidth += width
                column.minWidth = min(ShowUsagesTable.MIN_COLUMN_WIDTH, width)
                column.maxWidth = width
                column.width = width
                column.preferredWidth = width
            }
            // The last column should grow to fill the rest of the table width.
            // In Kotlin, Integer.MAX_VALUE is Int.MAX_VALUE.
            table.columnModel.getColumn(colsNum - 1).maxWidth = Int.MAX_VALUE

            return totalWidth
        }

        private fun columnMaxWidth(table: JTable, col: Int): Int {
            val column = table.columnModel.getColumn(col)
            var width = 0
            for (i in 0 until table.rowCount) {
                // The original Java code had `int row = i;` which is redundant here.
                // The cast `(ShowUsagesTable.MyModel)table.getModel()` becomes `as ShowUsagesTable.MyModel`.
                val model = table.model as ShowUsagesTable.MyModel

                // The Java lambda `() -> { ... }` is translated into a Kotlin lambda block.
                // Using Kotlin's trailing lambda syntax makes the code more readable.
                val rendererWidth = model.getOrCalcCellWidth(i, col) {
                    val component: Component = table.prepareRenderer(column.cellRenderer, i, col)
                    component.preferredSize.width
                }

                width = max(width, rendererWidth + table.intercellSpacing.width)
            }
            return min(ShowUsagesTable.MAX_COLUMN_WIDTH, width)
        }

        private fun getPreferredBounds(
            table: JTable,
            point: Point,
            width: Int,
            minHeight: Int,
            modelRows: Int,
            showCodePreview: Boolean
        ): Rectangle {
            // In Kotlin, function parameters are immutable (val).
            // A local mutable variable (`var`) is needed to replicate the Java code's
            // modification of the `minHeight` parameter within the function's scope.
            var effectiveMinHeight = minHeight

            val addExtraSpace: Boolean = Registry.`is`("ide.preferred.scrollable.viewport.extra.space")
            val visibleRows: Int = minOf(if (showCodePreview) 20 else 30, modelRows)
            val rowHeight: Int = table.rowHeight
            val space: Int = if (addExtraSpace && visibleRows < modelRows) rowHeight / 2 else 0
            var height: Int = visibleRows * rowHeight + minHeight + space;
            if (ExperimentalUI.isNewUI() && space == 0 && visibleRows == modelRows) {
                height += JBUIScale.scale(4)
            }
            val bounds = Rectangle(point.x, point.y, width, height)
            ScreenUtil.fitToScreen(bounds)
            if (bounds.height != height) {
                effectiveMinHeight += if (addExtraSpace && space == 0) rowHeight / 2 else space
                bounds.height = maxOf(1, (bounds.height - effectiveMinHeight) / rowHeight) * rowHeight + effectiveMinHeight
            }
            return bounds
        }

        private fun setPopupSize(
            table: JTable,
            popup: AbstractPopup,
            popupPosition: RelativePoint,
            minWidth: IntRef,
            showCodePreview: Boolean,
            dataSize: Int
        ) {
            if (Registry.`is`("find.usages.disable.smart.size", false)) {
                calcMaxWidth(table)
                return
            }

            if (isCodeWithMeClientInstance(popup)) return

            val toolbarComponent: Component? = (popup.component.layout as? BorderLayout)?.getLayoutComponent(BorderLayout.NORTH)
            val toolbarSize: Dimension = toolbarComponent?.preferredSize ?: JBUI.emptySize()
            val headerSize: Dimension = popup.headerPreferredSize

            var width: Int = calcMaxWidth(table)
            width = maxOf(headerSize.width, width)
            width = maxOf(toolbarSize.width, width)
            width = maxOf(minWidth.get(), width)

            minWidth.set(width)

            val minHeight: Int = headerSize.height + toolbarSize.height

            val rectangle: Rectangle = getPreferredBounds(table, popupPosition.screenPoint, width, minHeight, dataSize, showCodePreview)
            table.size = Dimension(rectangle.width, rectangle.height - minHeight)
            if (dataSize > 0) ScrollingUtil.ensureSelectionExists(table)

            val savedSize = WindowStateService.getInstance().getSize(DIMENSION_SERVICE_KEY)
            // In Kotlin, SomeClass.class is written as SomeClass::class.java for Java interoperability.
            val splitter = popup.getUserData(JBSplitter::class.java)

            if (savedSize != null) {
                rectangle.width = minOf(savedSize.width, rectangle.width)
            }

            if (splitter != null) {
                var newHeight = rectangle.height + splitter.dividerWidth + splitter.secondComponent.minimumSize.height
                if (savedSize != null) {
                    savedSize.height -= popup.adComponentHeight
                    newHeight = maxOf(newHeight, savedSize.height)
                }
                rectangle.height = newHeight
            }

            popup.size = rectangle.size
            popup.moveToFitScreen()
        }
    }
}
