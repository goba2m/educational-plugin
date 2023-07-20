package com.jetbrains.edu.learning

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.jetbrains.edu.coursecreator.CCUtils
import com.jetbrains.edu.coursecreator.courseignore.CourseIgnoreChecker

class CourseIgnoreDocumentListener(private val project: Project) : EduDocumentListenerBase(project) {

  private val courseIgnoreChecker = CourseIgnoreChecker.getInstance(project)

  private val updateQueue = MergingUpdateQueue(
    COURSE_IGNORE_UPDATE,
    Registry.intValue(COURSE_IGNORE_UPDATE_DELAY_REGISTRY_KEY),
    true,
    null,
    courseIgnoreChecker
  ).apply { setRestartTimerOnAdd(true) }

  override fun documentChanged(event: DocumentEvent) {
    if (!event.isInProjectContent()) return
    val file = fileDocumentManager.getFile(event.document) ?: return
    if (file.name != EduNames.COURSE_IGNORE) return
    if (!CCUtils.isCourseCreator(project)) return

    updateQueue.queue(Update.create(COURSE_IGNORE_UPDATE) {
      courseIgnoreChecker.refresh(event.document.text)
      ProjectView.getInstance(project).refresh()
    })
  }

  companion object {
    private const val COURSE_IGNORE_UPDATE = ".courseignore update"
    private const val COURSE_IGNORE_UPDATE_DELAY_REGISTRY_KEY = "edu.courseignore.update.delay"
  }
}
