package com.jetbrains.edu.html.taskDescription

import com.jetbrains.edu.codeInsight.taskDescription.EduToolWindowIdCompletionContributorBase
import com.jetbrains.edu.learning.taskDescription.ui.TaskDescriptionLinkProtocol

class EduHtmlToolWindowIdCompletionContributor : EduToolWindowIdCompletionContributorBase() {
  override val elementTextPrefix: String = TaskDescriptionLinkProtocol.TOOL_WINDOW.protocol
}
