// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.insilications.openinsplitted.codeInsight.navigation.impl

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.EditSourceUtil
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
val navigationOptionsRequestFocus: NavigationOptions = NavigationOptions.requestFocus()
val progressTitlePreparingNavigation: String = IdeBundle.message("progress.title.preparing.navigation")

inline fun PsiElement.gtdTargetNavigatable(): Navigatable? {
    return TargetElementUtil.getInstance()
        .getGotoDeclarationTarget(this, navigationElement)
        ?.psiNavigatable()
}

inline fun PsiElement.psiNavigatable(): Navigatable? {
    return this as? Navigatable
        ?: EditSourceUtil.getDescriptor(this)
}
