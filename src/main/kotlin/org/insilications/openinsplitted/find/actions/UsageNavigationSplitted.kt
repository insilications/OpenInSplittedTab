// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.insilications.openinsplitted.find.actions

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.insilications.openinsplitted.codeInsight.navigation.actions.receiveNextWindowPane
import org.insilications.openinsplitted.codeInsight.navigation.impl.navigationOptionsRequestFocus
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NotNull

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class UsageNavigationSplitted(private val project: Project, private val cs: CoroutineScope) {
    companion object {
        @JvmStatic
        fun getInstance(project: Project): UsageNavigationSplitted = project.getService(UsageNavigationSplitted::class.java)

        private val LOG: Logger = Logger.getInstance("org.insilications.openinsplitted")
    }

    fun navigateAndHint(
        project: Project,
        usage: Usage,
        onReady: Runnable,
        editor: Editor?,
    ) {
        cs.launch(Dispatchers.EDT) {
            val dataContext = editor?.let {
                DataManager.getInstance().getDataContext(it.component)
            }
            if (usage is UsageInfo2UsageAdapter) {
                receiveNextWindowPane(project, usage.file)
            } else {
                receiveNextWindowPane(project, null)

            }
            NavigationService.getInstance(project).navigate(usage, navigationOptionsRequestFocus, dataContext)
            writeIntentReadAction {
                onReady.run()
            }
        }
    }

    @RequiresEdt
    fun navigateUsageInfo(@NotNull info: UsageInfo, dataContext: DataContext?) {
        cs.launch {
            val (request: NavigationRequest?, preResolvedFile: VirtualFile?) = readAction {
                val file: VirtualFile = info.virtualFile ?: return@readAction null to null
                NavigationRequest
                    .sourceNavigationRequest(project, file, info.navigationOffset) to file

            }

            if (request == null) {
                LOG.warn("navigateUsageInfo - Failed to create navigation request")
                return@launch
            }

            withContext(Dispatchers.EDT) {
                // History update belongs on EDT
                IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation()
                receiveNextWindowPane(project, preResolvedFile)
            }

            NavigationService.getInstance(project).navigate(
                request,
                navigationOptionsRequestFocus,
                dataContext
            )
        }
    }
//
//    private suspend fun navigateUsageInfo(
//        info: UsageInfo,
//        requestFocus: Boolean,
//        dataContext: DataContext?,
//    ) {
////        val request = readAction {
////            val offset = info.navigationOffset
////            val project = info.project
////            val file = info.virtualFile ?: return@readAction null
////            NavigationRequest.sourceNavigationRequest(project, file, offset
////        }
//        val prepared = readAction {
//            val file = info.virtualFile ?: return@readAction null
//            val offset = info.navigationOffset
//            val req = NavigationRequest
//                .sourceNavigationRequest(project /* class property */, file, offset)
//                ?: return@readAction null
//            PreparedNav(file, req)
//        } ?: return
//
//        withContext(Dispatchers.EDT) {
//            receiveNextWindowPane(project, prepared.file)
//        }
//
//        NavigationService.getInstance(project).navigate(
//            prepared.request,
//            navigationOptionsRequestFocus,
//            dataContext
//        )
////        request?.let {
////            readActionBlocking { UsageViewStatisticsCollector.logUsageNavigate(project, info) }
////            NavigationService.getInstance(project).navigate(it, NavigationOptions.defaultOptions().requestFocus(requestFocus), dataContext)
////        }
//    }
}