package com.jetbrains.edu.coursecreator.actions.checkAllTasks

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.jetbrains.edu.coursecreator.CCUtils
import com.jetbrains.edu.learning.StudyTaskManager
import com.jetbrains.edu.learning.courseFormat.Course
import com.jetbrains.edu.learning.courseFormat.StudyItem
import com.jetbrains.edu.learning.getStudyItem
import com.jetbrains.edu.learning.messages.EduCoreBundle
import org.jetbrains.annotations.NonNls

class CCCheckAllTasksAction : AnAction(EduCoreBundle.lazyMessage("action.check.tasks.text")) {
  private class CheckAllTasksProgressTask(
    project: Project,
    private val course: Course,
    private val studyItems: List<StudyItem>,
  ) : Task.Backgroundable(
    project,
    EduCoreBundle.message("progress.title.checking.tasks"),
    true) {
    override fun run(indicator: ProgressIndicator) {
      val failedTasks = checkAllStudyItems(project, course, studyItems, indicator) ?: return
      val notification = if (failedTasks.isEmpty()) {
        Notification(
          "JetBrains Academy",
          EduCoreBundle.message("notification.title.check.finished"),
          EduCoreBundle.message("notification.content.all.tasks.solved.correctly"),
          NotificationType.INFORMATION
        )
      }
      else {
        val tasksNum = getNumberOfTasks(studyItems)
        createFailedTasksNotification(failedTasks, tasksNum, project)
      }
      Notifications.Bus.notify(notification, project)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val course = StudyTaskManager.getInstance(project).course ?: return

    val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return

    val studyItems = selectedFiles.mapNotNull { it.getStudyItem(project) }.toList()
    if (studyItems.isEmpty()) return

    ProgressManager.getInstance().run(CheckAllTasksProgressTask(project, course, studyItems))
  }

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    e.presentation.isEnabledAndVisible = CCUtils.isCourseCreator(project)
  }

  companion object {
    @NonNls
    const val ACTION_ID = "Educational.Educator.CheckAllTasks"
  }
}