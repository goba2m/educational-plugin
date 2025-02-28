package com.jetbrains.edu.learning.framework

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.edu.learning.courseFormat.FrameworkLesson
import com.jetbrains.edu.learning.courseFormat.tasks.Task

interface FrameworkLessonManager {
  fun prepareNextTask(lesson: FrameworkLesson, taskDir: VirtualFile, showDialogIfConflict: Boolean)
  fun preparePrevTask(lesson: FrameworkLesson, taskDir: VirtualFile, showDialogIfConflict: Boolean)

  fun saveExternalChanges(task: Task, externalState: Map<String, String>)
  fun updateUserChanges(task: Task, newInitialState: Map<String, String>)

  fun getChangesTimestamp(task: Task): Long

  companion object {
    fun getInstance(project: Project): FrameworkLessonManager = project.service()
  }
}
