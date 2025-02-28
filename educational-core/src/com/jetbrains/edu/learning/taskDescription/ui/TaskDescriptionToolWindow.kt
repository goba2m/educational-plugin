/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.edu.learning.taskDescription.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.jetbrains.edu.learning.JavaUILibrary
import com.jetbrains.edu.learning.courseFormat.ext.getTaskTextFromTask
import com.jetbrains.edu.learning.courseFormat.tasks.Task
import com.jetbrains.edu.learning.messages.EduCoreBundle
import com.jetbrains.edu.learning.taskDescription.htmlTransformers.HtmlTransformerContext
import com.jetbrains.edu.learning.taskDescription.htmlTransformers.TaskDescriptionTransformer
import javax.swing.JComponent


abstract class TaskDescriptionToolWindow(protected val project: Project) : Disposable {
  //default value of merging time span is 300 milliseconds, can be set in educational-core.xml
  @Suppress("LeakingThis")
  private val updateQueue = MergingUpdateQueue(TASK_DESCRIPTION_UPDATE,
                                               Registry.intValue(TASK_DESCRIPTION_UPDATE_DELAY_REGISTRY_KEY),
                                               true,
                                               null,
                                               this)

  abstract val taskInfoPanel: JComponent

  abstract val taskSpecificPanel: JComponent

  abstract val uiMode: JavaUILibrary

  open fun updateTaskSpecificPanel(task: Task?) {}

  fun setTaskText(project: Project, task: Task?) {
    updateQueue.queue(Update.create(TASK_DESCRIPTION_UPDATE) {
      setText(getTaskDescription(project, task, uiMode))
    })
  }

  protected abstract fun setText(text: String)

  override fun dispose() {}

  companion object {
    private const val TASK_DESCRIPTION_UPDATE: String = "Task Description Update"
    const val TASK_DESCRIPTION_UPDATE_DELAY_REGISTRY_KEY: String = "edu.task.description.update.delay"

    fun getTaskDescription(project: Project, task: Task?, uiMode: JavaUILibrary): String {
      val taskText = task?.getTaskTextFromTask(project) ?: return EduCoreBundle.message("label.open.task")
      val transformerContext = HtmlTransformerContext(project, task, uiMode)
      return TaskDescriptionTransformer.transform(taskText, transformerContext)
    }
  }
}
