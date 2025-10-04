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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.insilications.openinsplitted.codeInsight.navigation.actions.receiveNextWindowPane
import org.insilications.openinsplitted.codeInsight.navigation.impl.navigationOptionsRequestFocus
import org.insilications.openinsplitted.debug
import org.insilications.openinsplitted.printCurrentThreadContext
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class UsageNavigationSplitted(private val project: Project, private val cs: CoroutineScope) {
    companion object {
        @JvmStatic
        fun getInstance(project: Project): UsageNavigationSplitted = project.getService(UsageNavigationSplitted::class.java)

        private val LOG: Logger = Logger.getInstance("org.insilications.openinsplitted")
    }

    fun navigateToUsageAndHint(
        usage: Usage,
        onReady: Runnable,
        editor: Editor?,
    ) {
        printCurrentThreadContext("navigateToUsageAndHint - 0")
        cs.launch(Dispatchers.EDT) {
            // We have implicit write intent lock under `Dispatchers.EDT`, which implies an **IMPLICIT** read lock too
            // Therefore, in the latest Intellij Platform API, we don't need to explictly get a read lock
            // This will change in future releases, so keep an eye on it

            printCurrentThreadContext("navigateToUsageAndHint - 1")
            val dataContext: DataContext? = editor?.let {
                DataManager.getInstance().getDataContext(it.component)
            }

            // History update on EDT
            IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation()
            if (usage is UsageInfo2UsageAdapter) {
                receiveNextWindowPane(project, usage.file)
                LOG.debug { "0 navigateAndHint - usage is ${usage::class.simpleName}" }
            } else {
                receiveNextWindowPane(project, null)
                LOG.debug { "1 navigateAndHint - usage is ${usage::class.simpleName}" }
            }

            NavigationService.getInstance(project).navigate(usage, navigationOptionsRequestFocus, dataContext)
            writeIntentReadAction {
                onReady.run()
            }
        }
    }

    fun navigateToUsageInfo(info: UsageInfo, dataContext: DataContext?) {
        printCurrentThreadContext("navigateToUsageInfo - 0")
        cs.launch {
            printCurrentThreadContext("navigateToUsageInfo - 1")
            val (request: NavigationRequest?, file: VirtualFile?) = readAction {
                printCurrentThreadContext("navigateToUsageInfo - 2")
                val file: VirtualFile = info.virtualFile ?: return@readAction null to null
                NavigationRequest
                    .sourceNavigationRequest(info.project, file, info.navigationOffset) to file

            }

            if (request == null) {
                LOG.warn("navigateUsageInfo - Failed to create navigation request")
                return@launch
            }

            withContext(Dispatchers.EDT) {
                // We have implicit write intent lock under `Dispatchers.EDT`, which implies an **IMPLICIT** read lock too
                // Therefore, in the latest Intellij Platform API, we don't need to explictly get a read lock
                // This will change in future releases, so keep an eye on it

                printCurrentThreadContext("navigateToUsageInfo - 3")
                // History update on EDT
                IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation()
                receiveNextWindowPane(project, file)
            }

            printCurrentThreadContext("navigateToUsageInfo - 4")

            NavigationService.getInstance(project).navigate(
                request,
                navigationOptionsRequestFocus,
                dataContext
            )
        }
    }
}