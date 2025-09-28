// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package org.insilications.openinsplitted.find.actions

import com.intellij.CommonBundle
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.navigation.targetPresentation
import com.intellij.find.FindBundle
import com.intellij.find.usages.api.SearchTarget
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts.PopupTitle
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.list.createTargetPopup
import com.intellij.usages.UsageTarget
import org.insilications.openinsplitted.debug
import org.jetbrains.annotations.ApiStatus

private val LOG: Logger = Logger.getInstance("org.insilications.openinsplitted")

interface UsageVariantHandler {
    @ApiStatus.Internal
    fun handleTarget(target: SearchTarget)
    fun handlePsi(element: PsiElement)
}

fun findShowUsages(
    project: Project,
    editor: Editor?,
    popupPosition: RelativePoint,
    allTargets: List<TargetVariant>,
    @PopupTitle popupTitle: String,
    handler: UsageVariantHandler
) {
    LOG.debug { "resolver - findShowUsages" }
    when (allTargets.size) {
        0 -> {
            val message = FindBundle.message("find.no.usages.at.cursor.error")
            if (editor == null) {
                Messages.showMessageDialog(project, message, CommonBundle.getErrorTitle(), Messages.getErrorIcon())
            } else {
                HintManager.getInstance().showErrorHint(editor, message)
            }
        }

        1 -> {
            LOG.debug { "resolver - allTargets.single().handle(handler)" }
            allTargets.single().handle(handler)
        }

        else -> {
            LOG.debug { "resolver - createTargetPopup(...)" }
            createTargetPopup(popupTitle, allTargets, TargetVariant::presentation) {
                it.handle(handler)
            }.show(popupPosition)
        }
    }
}

sealed class TargetVariant {
    @get:ApiStatus.Internal
    @get:ApiStatus.Experimental
    abstract val presentation: TargetPresentation
    abstract fun handle(handler: UsageVariantHandler)
}

@ApiStatus.Experimental
class SearchTargetVariant(private val target: SearchTarget) : TargetVariant() {
    @ApiStatus.Internal
    @ApiStatus.Experimental
    override val presentation: TargetPresentation = target.presentation()
    override fun handle(handler: UsageVariantHandler): Unit = handler.handleTarget(target)
}

class PsiTargetVariant(private val element: PsiElement) : TargetVariant() {
    @ApiStatus.Internal
    override val presentation: TargetPresentation = targetPresentation(element)
    override fun handle(handler: UsageVariantHandler): Unit = handler.handlePsi(element)
}

class CustomTargetVariant(private val target: UsageTarget) : TargetVariant() {
    @ApiStatus.Internal
    override val presentation: TargetPresentation = targetPresentation(target.presentation!!)
    override fun handle(handler: UsageVariantHandler): Unit = target.findUsages()
}