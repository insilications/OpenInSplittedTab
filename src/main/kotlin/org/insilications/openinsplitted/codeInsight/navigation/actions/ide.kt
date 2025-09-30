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
import com.intellij.lang.LanguageNamesValidation
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadAction.*
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
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.insilications.openinsplitted.codeInsight.navigation.impl.gtdTargetNavigatable
import org.insilications.openinsplitted.codeInsight.navigation.impl.navigationOptionsRequestFocus
import org.insilications.openinsplitted.codeInsight.navigation.impl.progressTitlePreparingNavigation
import org.insilications.openinsplitted.debug
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.AWTEvent
import java.awt.event.MouseEvent
import javax.swing.SwingConstants
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

val LOG: Logger = Logger.getInstance("org.insilications.openinsplitted")

data class PreparedNavigation(
    val request: NavigationRequest,
    val preResolvedFile: VirtualFile?
)

@JvmInline
value class PackedPoint(private val request: NavigationRequest, private val preResolvedFile: VirtualFile?) {
//    // We can add properties to unpack the values on demand.
//    // The `inline` keyword on the getter helps ensure no overhead.
//    inline val x: Int
//        get() = (packedValue shr 32).toInt() // Get the upper 32 bits
//
//    inline val y: Int
//        get() = (packedValue and 0xFFFFFFFF).toInt() // Get the lower 32 bits

    // We can also enable destructuring declarations.
    // These must be `operator` functions.
    operator fun component1(): Int = x
    operator fun component2(): Int = y
}

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

inline fun navigateToLookupItem(project: Project, editor: Editor): Boolean {
    val activeLookup: LookupEx = LookupManager.getInstance(project).activeLookup ?: return false
    val currentItem: LookupElement? = activeLookup.currentItem
    navigateRequestLazy(project, {
        TargetElementUtil.targetElementFromLookupElement(currentItem)
            ?.gtdTargetNavigatable()
            ?.navigationRequest()
    }, editor)
    return true
}

inline fun process(onResult: (x: String, y: String) -> Unit) {
    // Some logic to calculate x and y
    val string1 = " Test2 "
    val string2 = " Test2 "
    onResult(string1, string2)
}

fun main() {

    var returned1: String
    var returned2: String
    // The code inside the lambda is effectively moved to the call site.
    // No new objects are allocated for the return values.
    process { x, y ->
        returned1 = "Hello$x"
        returned2 = "Hello$y"
    }
}

inline fun parseFullName(
    fullName: String,
    onResult: (firstName: String, lastName: String) -> Unit
) {
    val parts = fullName.split(" ", limit = 2)
    if (parts.size == 2) {
        onResult(parts[0], parts[1])
    }
}

fun processUser() {
    // The logic to handle the results is right here.
    // No allocation for a Pair, data class, or Function object.
    parseFullName("John Doe") { firstName, lastName ->
        println("First: $firstName, Last: $lastName")
    }
}

//@OptIn(ExperimentalContracts::class)
//inline fun getStuff(block: (Bonus) -> Unit): Stuff {
//    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
//    val stuff = grabStuffFromCargoBikeBasket()
//    val bonus = inspirationElixir()
//    block(bonus)
//    return stuff
//}
@OptIn(ExperimentalContracts::class)
inline suspend fun getStuff(requestor: NavigationRequestor, crossinline block: suspend (request: NavigationRequest?, preResolvedFile: VirtualFile?) -> Unit) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
//    readAction {
//    request = requestor.navigationRequest() ?: return@readAction
//    preResolvedFile = preResolveFileForWindowPane(request)
//    }
    readAction {
        val request: NavigationRequest = requestor.navigationRequest() ?: return block(null, null)
        val preResolvedFile: VirtualFile? = preResolveFileForWindowPane(request)

    }
    block(request, preResolvedFile)
}

@OptIn(ExperimentalContracts::class)
inline fun readActionPairLike(
//    crossinline compute: () -> Pair<A, B>,
    crossinline useSecond: (it1: String, it2: String) -> Unit
): A {
    contract { callsInPlace(useSecond, InvocationKind.EXACTLY_ONCE) }
    return run<RuntimeException> {
//        val (a, b) = compute()
        useSecond("asd", "asd")   // still inside read lock!
//        a
    }
}

/**
 * This function retrieves a navigation request from the provided [requestor] and navigates to it.
 * We call `receiveNextWindowPane` to preemptively set the current window to the next splitted tab or a new splitted tab.
 * This forces the calls to the `navigate` method to reuse that tab. This workaround might be fragile, but it works perfectly.
 */
@OptIn(ExperimentalContracts::class)
@Internal
@RequiresEdt
inline fun navigateRequestLazy(project: Project, requestor: NavigationRequestor, editor: Editor) {

    // Acquire DataContext on EDT before blocking background thread
    val dataContext: DataContext = DataManager.getInstance().getDataContext(editor.component)
    runWithModalProgressBlocking(project, progressTitlePreparingNavigation) {
        lateinit var bonus: String
        lateinit var bonus2: String
        readActionPairLike(
//            compute = { computeStuffAndBonus() },   // returns Pair<Stuff, Bonus>
            useSecond = { it1, it2 -> bonus = it1; bonus2 = it2 }   // still inside read lock!
        )
        val request2: NavigationRequest?
        val preResolvedFile2: VirtualFile?
//        val theBonus: Bonus
//        getStuff(requestor) { request, preResolvedFile ->
//            request2 = request
//            preResolvedFile2 = preResolvedFile
//        }
//        getStuff(requestor) { request, preResolvedFile ->
//            request2 = request
//            preResolvedFile2 = preResolvedFile
//        }

//
////                val navRequest = requestor.navigationRequest() ?: return@readAction null
////                val file = preResolveFileForWindowPane(navRequest)
//        contract { callsInPlace(res, InvocationKind.EXACTLY_ONCE) }

//        readAction {
//            request2 = requestor.navigationRequest() ?: return@readAction
//            preResolvedFile2 = preResolveFileForWindowPane(request2)
//        }
//        }

        if (request == null) {
            return@runWithModalProgressBlocking
        }

        // Switch to EDT for UI side-effects
        withContext(Dispatchers.EDT) {
            // History update belongs on EDT
            IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation()

            when (request) {
                is SourceNavigationRequest -> {
                    LOG.debug { "navigateRequestLazy - SourceNavigationRequest (file=${req.file.path})" }
                    receiveNextWindowPane(project, prepared.preResolvedFile ?: req.file)
                }

                is RawNavigationRequest -> {
                    if (request.canNavigateToSource) {
                        LOG.debug {
                            "navigateRequestLazy - RawNavigationRequest(canNavigateToSource) navigatable=${req.navigatable.javaClass.name} " +
                                    "preResolvedFile=${prepared.preResolvedFile?.path}"
                        }
                        receiveNextWindowPane(project, preResolvedFile)
                    } else {
                        LOG.debug {
                            "navigateRequestLazy - RawNavigationRequest(non-source) navigatable=${req.navigatable.javaClass.name}"
                        }
                        receiveNextWindowPane(project, null)
                    }
                }

                else -> {
                    // DirectoryNavigationRequest is 'non-source', but you might still want a placeholder.
                    LOG.debug { "navigateRequestLazy - Non-source request: ${req::class.simpleName}" }
                    receiveNextWindowPane(project, preResolvedFile)
                }
            }

            // Delegate to the platform to perform actual navigation (single unified call).
            project.serviceAsync<NavigationService>().navigate(request, navigationOptionsRequestFocus, dataContext)
        }
    }

//    runWithModalProgressBlocking(project, progressTitlePreparingNavigation) {
//        val request: NavigationRequest? = readAction {
//            requestor.navigationRequest()
//        }
//
//        if (request != null) {
//            withContext(Dispatchers.EDT) {
//                IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation()
//                when (request) {
//                    // `SharedSourceNavigationRequest` is a subclass of `SourceNavigationRequest`.
//                    is SourceNavigationRequest -> {
//                        LOG.debug { "navigateRequestLazy - SourceNavigationRequest" }
//                        // We already have the target file, so we pass it to open it in the case that a new split must be created.
//                        receiveNextWindowPane(project, request.file)
//                    }
//
//                    // TODO: make RawNavigationRequest navigate to the next splitted tab or a new splitted tab too
//                    // TODO: We need to get the target `VirtualFile` file from `RawNavigationRequest.navigatable` to pass to `receiveNextWindowPane`
//                    is RawNavigationRequest -> {
//                        if (request.canNavigateToSource) {
//                            val requestNavigatable: Navigatable = request.navigatable
//
//                            LOG.debug { "navigateRequestLazy - RawNavigationRequest - request.navigatable is ${requestNavigatable::class.simpleName}" }
//                            when (requestNavigatable) {
//                                is OpenFileDescriptor -> {
//                                    LOG.debug { "navigateRequestLazy - RawNavigationRequest - request.navigatable is OpenFileDescriptor" }
//                                    receiveNextWindowPane(project, requestNavigatable.file)
//                                }
//
//                                is PsiElement -> {
//                                    LOG.debug { "navigateRequestLazy - RawNavigationRequest - request.navigatable is PsiElement" }
//                                    // The `EditSourceUtil.getDescriptor` makes a best-effort attempt to extract as `OpenFileDescriptor`
//                                    // It even calls the `PsiUtilCore.getVirtualFile` if necessary
//                                    receiveNextWindowPane(project, (EditSourceUtil.getDescriptor(requestNavigatable) as OpenFileDescriptor).file)
//                                }
//
//                                else -> {
//                                    receiveNextWindowPane(project, null)
//                                }
//                            }
//                        } else {
//                            LOG.debug { "navigateRequestLazy - RawNavigationRequest - request.canNavigateToSource == false" }
//                        }
//                    }
//                    // `DirectoryNavigationRequest` is non-source, so we don't need to handle it
//                    // It will be handled by `NavigationService.navigate` below
//                    else -> {
//                        LOG.error("navigateRequestLazy - Unsupported request: ${request::class.simpleName}")
//                    }
//                }
//                project.serviceAsync<NavigationService>().navigate(request, navigationOptionsRequestFocus, dataContext)
//            }
//        }
//    }
}

@RequiresReadLock
fun preResolveFileForWindowPane(request: NavigationRequest): VirtualFile? {
    return when (request) {
        // `SharedSourceNavigationRequest` is a subclass of `SourceNavigationRequest`.
        is SourceNavigationRequest -> {
            LOG.debug { "navigateRequestLazy - SourceNavigationRequest" }
            request.file
        }

        is RawNavigationRequest -> extractFileFromNavigatable(request.navigatable)
        // `DirectoryNavigationRequest` is non-source, so we don't need to handle it
        // It will be handled by `NavigationService.navigate` below
        else -> null
    }
}

@RequiresReadLock
fun extractFileFromNavigatable(nav: Navigatable): VirtualFile? {
    // 1. OpenFileDescriptor
    if (nav is OpenFileDescriptor) {
        return nav.file
    }

    // 2. PSI-based
    if (nav is PsiElement) {
        // Try a descriptor derived from PSI (often yields an OpenFileDescriptor)
        val descriptor: Navigatable? = EditSourceUtil.getDescriptor(nav)
        if (descriptor is OpenFileDescriptor) {
            LOG.debug { "navigateRequestLazy - RawNavigationRequest - descriptor is OpenFileDescriptor" }
            return descriptor.file
        }

        if (descriptor != null) {
            if (descriptor is PsiElement && descriptor.isValid) {
                LOG.debug { "navigateRequestLazy - RawNavigationRequest - descriptor is PsiElement or ${descriptor::class.simpleName}" }
                PsiUtilCore.getVirtualFile(descriptor)?.let { return it }
            }
        }
    }

    // 3. Non-PSI, non-OpenFileDescriptor Navigatable → no recoverable file
    return null
}

//@RequiresReadLock
//private fun extractFileFromNavigatable(nav: Navigatable): VirtualFile? {
//    // 1. Direct OpenFileDescriptor
//    if (nav is OpenFileDescriptor) {
//        return nav.file
//    }
//
//    // 2. Try descriptor adaptation (works for many Psi-based navigatables too)
//    EditSourceUtil.getDescriptor(nav)?.file?.let { return it }
//
//    // 3. PSI path (only if we can safely treat it as PSI)
//    if (nav is PsiElement && nav.isValid) {
//        PsiUtilCore.getVirtualFile(nav)?.let { return it }
//        nav.containingFile?.virtualFile?.let { return it }
//    }
//
//    return null
//}

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
