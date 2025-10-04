package org.insilications.openinsplitted.codeInsight.navigation.actions

import com.intellij.codeInsight.navigation.GotoImplementationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.pom.Navigatable
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.insilications.openinsplitted.codeInsight.navigation.impl.fetchDataContext
import org.insilications.openinsplitted.codeInsight.navigation.impl.navigationOptionsRequestFocus
import org.insilications.openinsplitted.codeInsight.navigation.impl.progressTitlePreparingNavigation
import org.insilications.openinsplitted.debug

class GotoImplementationHandlerSplitted : GotoImplementationHandler() {
    companion object {
        private val LOG: Logger = Logger.getInstance("org.insilications.openinsplitted")
    }

    @RequiresEdt
    override fun navigateToElement(project: Project?, descriptor: Navigatable) {
        if (project == null) return

        runWithModalProgressBlocking(project, progressTitlePreparingNavigation) {
            LOG.debug { "GotoImplementationHandlerSplitted - navigateToElement - descriptor is ${descriptor::class.simpleName}" }

            val dataContext: DataContext?
            // Switch to EDT for UI side-effects
            withContext(Dispatchers.EDT) {
                // Acquire DataContext on EDT
                dataContext = fetchDataContext(project)
                // History update on EDT
                IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation()
                receiveNextWindowPane(project) {
                    getVirtualFileFromNavigatable(descriptor)
                }
            }

            // Delegate to the platform's `IdeNavigationService.kt` to perform actual navigation
            project.serviceAsync<NavigationService>().navigate(descriptor, navigationOptionsRequestFocus, dataContext)
        }
    }
}