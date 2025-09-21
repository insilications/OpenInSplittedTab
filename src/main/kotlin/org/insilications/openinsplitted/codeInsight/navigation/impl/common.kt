// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.insilications.openinsplitted.codeInsight.navigation.impl

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.ide.util.EditSourceUtil
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement

internal fun PsiElement.gtdTargetNavigatable(): Navigatable? {
    return TargetElementUtil.getInstance()
        .getGotoDeclarationTarget(this, navigationElement)
        ?.psiNavigatable()
}

internal fun PsiElement.psiNavigatable(): Navigatable? {
    return this as? Navigatable
        ?: EditSourceUtil.getDescriptor(this)
}
