package com.jetbrains.edu.learning.marketplace.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.jetbrains.edu.learning.marketplace.isMarketplaceCourse
import com.jetbrains.edu.learning.marketplace.settings.MarketplaceSettings
import com.jetbrains.edu.learning.messages.EduCoreBundle

object ShareMySolutionsAction : DumbAwareToggleAction(EduCoreBundle.message("marketplace.solutions.sharing.action")) {

    override fun update(e: AnActionEvent) {
        super.update(e)
        val project = e.project ?: return
        e.presentation.isEnabled = MarketplaceSettings.INSTANCE.solutionsSharing != null
        e.presentation.isVisible = project.isMarketplaceCourse()
    }

    override fun isSelected(e: AnActionEvent): Boolean = MarketplaceSettings.INSTANCE.solutionsSharing ?: false

    override fun setSelected(e: AnActionEvent, state: Boolean) = MarketplaceSettings.INSTANCE.updateSharingPreference(state)

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}