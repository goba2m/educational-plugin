package com.jetbrains.edu.learning.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.RightAlignedToolbarAction
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.edu.EducationalCoreIcons
import com.jetbrains.edu.learning.EduUtilsKt.isStudentProject
import com.jetbrains.edu.learning.actions.EduActionUtils.getCurrentTask
import com.jetbrains.edu.learning.course
import com.jetbrains.edu.learning.messages.EduCoreBundle
import com.jetbrains.edu.learning.statistics.EduCounterUsageCollector
import com.jetbrains.edu.learning.uIfeedback.InIdeFeedbackDialog
import org.jetbrains.annotations.NonNls

class LeaveInIdeFeedbackAction : DumbAwareAction(
  EduCoreBundle.lazyMessage("action.leave.feedback.text"),
  EduCoreBundle.lazyMessage("action.leave.feedback.text"),
  EducationalCoreIcons.CommentTask
), RightAlignedToolbarAction {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val task = project.getCurrentTask() ?: return

    val dialog = InIdeFeedbackDialog(project, task, false)
    if (dialog.showAndGet()) {
      dialog.showThanksNotification()
      EduCounterUsageCollector.leaveInIdeFeedback()
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false

    val project = e.project ?: return
    if (!project.isStudentProject()) return
    project.getCurrentTask() ?: return
    if (project.course?.isMarketplace != true) return

    e.presentation.isEnabledAndVisible = true
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  companion object {
    @NonNls
    const val ACTION_ID: String = "Educational.LeaveInIdeFeedbackAction"
  }
}