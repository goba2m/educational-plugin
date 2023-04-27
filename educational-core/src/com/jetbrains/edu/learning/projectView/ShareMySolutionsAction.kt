package com.jetbrains.edu.learning.projectView

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.jetbrains.edu.learning.marketplace.settings.MarketplaceSettings
import com.jetbrains.edu.learning.messages.EduCoreBundle

object ShareMySolutionsAction : DumbAwareToggleAction(EduCoreBundle.message("action.share.my.solutions.text")) {

    override fun isSelected(e: AnActionEvent): Boolean = MarketplaceSettings.INSTANCE.isShareMySolutions()

    override fun setSelected(e: AnActionEvent, state: Boolean) = MarketplaceSettings.INSTANCE.setShareMySolutions(state)

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}