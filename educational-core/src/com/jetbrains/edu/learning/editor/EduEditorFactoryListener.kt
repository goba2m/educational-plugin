package com.jetbrains.edu.learning.editor

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.problems.WolfTheProblemSolver
import com.jetbrains.edu.learning.EduUtilsKt.updateToolWindows
import com.jetbrains.edu.learning.StudyTaskManager
import com.jetbrains.edu.learning.courseFormat.CheckStatus
import com.jetbrains.edu.learning.courseFormat.EduCourse
import com.jetbrains.edu.learning.courseFormat.TaskFile
import com.jetbrains.edu.learning.courseFormat.tasks.Task
import com.jetbrains.edu.learning.courseFormat.tasks.TheoryTask
import com.jetbrains.edu.learning.getTaskFile
import com.jetbrains.edu.learning.marketplace.api.MarketplaceConnector
import com.jetbrains.edu.learning.navigation.NavigationUtils.getPlaceholderOffsets
import com.jetbrains.edu.learning.navigation.NavigationUtils.navigateToFirstAnswerPlaceholder
import com.jetbrains.edu.learning.placeholder.PlaceholderHighlightingManager.showPlaceholders
import com.jetbrains.edu.learning.placeholderDependencies.PlaceholderDependencyManager.updateDependentPlaceholders
import com.jetbrains.edu.learning.statistics.EduLaunchesReporter.sendStats
import com.jetbrains.edu.learning.stepik.hyperskill.courseFormat.HyperskillCourse
import com.jetbrains.edu.learning.stepik.hyperskill.markTheoryTaskAsCompleted
import com.jetbrains.edu.learning.taskDescription.ui.TaskDescriptionToolWindowFactory
import com.jetbrains.edu.learning.taskDescription.ui.TaskDescriptionView.Companion.getInstance
import com.jetbrains.edu.learning.yaml.YamlFormatSynchronizer.saveItem

class EduEditorFactoryListener : EditorFactoryListener {
  private class AnswerPlaceholderSelectionListener(private val taskFile: TaskFile) : EditorMouseListener {
    override fun mouseClicked(e: EditorMouseEvent) {
      val editor = e.editor
      val point = e.mouseEvent.point
      val pos = editor.xyToLogicalPosition(point)
      val answerPlaceholder = taskFile.getAnswerPlaceholder(editor.logicalPositionToOffset(pos))
      if (answerPlaceholder == null || !answerPlaceholder.isVisible || answerPlaceholder.selected) {
        return
      }
      val offsets = getPlaceholderOffsets(answerPlaceholder)
      editor.selectionModel.setSelection(offsets.getFirst(), offsets.getSecond())
      answerPlaceholder.selected = true
      saveItem(taskFile.task)
    }
  }

  override fun editorCreated(event: EditorFactoryEvent) {
    val editor = event.editor
    val project = editor.project ?: return
    val course = StudyTaskManager.getInstance(project).course ?: return
    val openedFile = FileDocumentManager.getInstance().getFile(editor.document) ?: return
    val taskFile = openedFile.getTaskFile(project) ?: return
    WolfTheProblemSolver.getInstance(project).clearProblems(openedFile)
    showTaskDescriptionToolWindow(project, taskFile, true)
    val task = taskFile.task
    markTheoryTaskCompleted(project, task)
    if (taskFile.answerPlaceholders.isNotEmpty() && taskFile.isValid(editor.document.text)) {
      updateDependentPlaceholders(project, task)
      navigateToFirstAnswerPlaceholder(editor, taskFile)
      showPlaceholders(project, taskFile, editor)
      if (course.isStudy) {
        editor.addEditorMouseListener(AnswerPlaceholderSelectionListener(taskFile))
      }
    }
    sendStats(course)
  }

  override fun editorReleased(event: EditorFactoryEvent) = event.editor.selectionModel.removeSelection()

  private fun showTaskDescriptionToolWindow(project: Project, taskFile: TaskFile, retry: Boolean) {
    val toolWindowManager = ToolWindowManager.getInstance(project)
    val studyToolWindow = toolWindowManager.getToolWindow(TaskDescriptionToolWindowFactory.STUDY_TOOL_WINDOW)
    if (studyToolWindow == null) {
      if (retry) {
        toolWindowManager.invokeLater { showTaskDescriptionToolWindow(project, taskFile, false) }
      }
      else {
        LOG.warn(String.format("Failed to get toolwindow with `%s` id", TaskDescriptionToolWindowFactory.STUDY_TOOL_WINDOW))
      }
      return
    }
    if (taskFile.task != getInstance(project).currentTask) {
      updateToolWindows(project)
      studyToolWindow.show(null)
    }
  }

  private fun markTheoryTaskCompleted(project: Project, task: Task) {
    if (task !is TheoryTask) return
    val course = task.course
    if (course.isStudy && task.postSubmissionOnOpen && task.status !== CheckStatus.Solved) {
      if (course is HyperskillCourse) {
        markTheoryTaskAsCompleted(project, task)
      }
      else if (course is EduCourse && course.isMarketplaceRemote) {
        MarketplaceConnector.getInstance().isLoggedInAsync().thenAcceptAsync { markTheoryTaskCompleted(project, task) }
      }
      task.status = CheckStatus.Solved
      saveItem(task)
      ProjectView.getInstance(project).refresh()
    }
  }


  companion object {
    private val LOG = logger<EduEditorFactoryListener>()
  }
}
