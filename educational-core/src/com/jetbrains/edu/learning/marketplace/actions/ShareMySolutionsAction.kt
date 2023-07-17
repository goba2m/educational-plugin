package com.jetbrains.edu.learning.marketplace.actions

import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.edu.learning.EduUtilsKt.isStudentProject
import com.jetbrains.edu.learning.marketplace.api.MarketplaceSubmissionsConnector
import com.jetbrains.edu.learning.marketplace.isMarketplaceCourse
import com.jetbrains.edu.learning.marketplace.settings.MarketplaceSettings
import com.jetbrains.edu.learning.messages.EduCoreBundle
import org.jetbrains.annotations.NonNls
import java.util.concurrent.CompletableFuture

class ShareMySolutionsAction : DumbAwareToggleAction(EduCoreBundle.message("marketplace.solutions.sharing.action")) {

    override fun update(e: AnActionEvent) {
        super.update(e)
        val project = e.project ?: return

        CompletableFuture.runAsync({
            e.presentation.isEnabled = isAvailableInSettings() || MarketplaceSubmissionsConnector.getInstance().getSharingPreference() != null
        }, ProcessIOExecutorService.INSTANCE)

        e.presentation.isVisible =
          project.isMarketplaceCourse()
          && project.isStudentProject()
          && !Registry.`is`(REGISTRY_KEY, false)
    }

    override fun isSelected(e: AnActionEvent): Boolean = MarketplaceSettings.INSTANCE.solutionsSharing ?: false

    override fun setSelected(e: AnActionEvent, state: Boolean) = MarketplaceSettings.INSTANCE.updateSharingPreference(state)

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun isAvailableInSettings() = MarketplaceSettings.INSTANCE.solutionsSharing != null

    companion object {

        @NonNls
        const val REGISTRY_KEY = "edu.learning.marketplace.solutions.sharing"
    }
}