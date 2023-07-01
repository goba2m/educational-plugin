package com.jetbrains.edu.codeInsight.taskDescription

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.openapi.options.Configurable

abstract class EduSettingsIdCompletionContributorBase : EduUriPathCompletionContributorBase() {

  override fun collectPathCompletionVariants(parameters: CompletionParameters): List<UriPathCompletionVariant> {
    val configurableEPs = Configurable.APPLICATION_CONFIGURABLE.extensionList +
                          Configurable.PROJECT_CONFIGURABLE.getExtensions(parameters.position.project)

    return configurableEPs
      .filter { it.id.orEmpty().isNotEmpty() }
      .map {
        UriPathCompletionVariant(it.id, it.getDisplayName().ifEmpty { it.id }, null)
      }
  }
}
