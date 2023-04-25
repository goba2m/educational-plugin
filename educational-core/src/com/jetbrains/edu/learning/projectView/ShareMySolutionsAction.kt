package com.jetbrains.edu.learning.projectView

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.jetbrains.edu.learning.marketplace.api.MarketplaceSubmissionsConnector
import com.jetbrains.edu.learning.messages.EduCoreBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ShareMySolutionsAction : DumbAwareToggleAction(EduCoreBundle.message("action.share.my.solutions.text")) {

    private var shareMySolutions : Boolean? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch {
            shareMySolutions = try {
                MarketplaceSubmissionsConnector.getInstance().getSharingPreference()
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun isSelected(e: AnActionEvent): Boolean = shareMySolutions ?: false

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        shareMySolutions = state
        scope.launch {
            try {
                if (state) {
                    MarketplaceSubmissionsConnector.getInstance().enableSolutionSharing()
                } else {
                    MarketplaceSubmissionsConnector.getInstance().disableSolutionSharing()
                }
            } catch (e: Exception) {
                // todo: add some logging and (or) prompting?
                shareMySolutions = !state
            }
        }
    }
}