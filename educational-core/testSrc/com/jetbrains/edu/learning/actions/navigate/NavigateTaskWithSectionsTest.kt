package com.jetbrains.edu.learning.actions.navigate

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.jetbrains.edu.learning.EduTestCase
import com.jetbrains.edu.learning.actions.EduActionUtils.getCurrentTask
import com.jetbrains.edu.learning.actions.NextTaskAction
import com.jetbrains.edu.learning.actions.PreviousTaskAction
import com.jetbrains.edu.learning.getTaskFile
import com.jetbrains.edu.learning.taskDescription.ui.TaskDescriptionView
import com.jetbrains.edu.learning.testAction
import junit.framework.TestCase

class NavigateTaskWithSectionsTest : EduTestCase() {

  fun `test next from lesson to section`() {
    configureByTaskFile(1, 1, "taskFile.txt")
    testAction(NextTaskAction.ACTION_ID)
    val currentFile = FileEditorManagerEx.getInstanceEx(myFixture.project).currentFile
    val taskFile = currentFile!!.getTaskFile(myFixture.project)
    val task = taskFile!!.task
    TestCase.assertEquals(1, task.index)
    val lesson = task.lesson
    TestCase.assertEquals(1, lesson.index)
    TestCase.assertNotNull(lesson.section)
    TestCase.assertEquals(2, lesson.section!!.index)
  }

  fun `test next for same lesson in section`() {
    configureByTaskFile(2, 1, 1, "taskFile.txt")
    testAction(NextTaskAction.ACTION_ID)
    val currentFile = FileEditorManagerEx.getInstanceEx(myFixture.project).currentFile
    val taskFile = currentFile!!.getTaskFile(myFixture.project)
    val task = taskFile!!.task
    TestCase.assertEquals(2, task.index)
    val lesson = task.lesson
    TestCase.assertEquals(1, lesson.index)
    TestCase.assertNotNull(lesson.section)
    TestCase.assertEquals(2, lesson.section!!.index)
  }

  fun `test next lesson in section`() {
    configureByTaskFile(2, 1, 2, "taskFile.txt")
    testAction(NextTaskAction.ACTION_ID)
    val currentFile = FileEditorManagerEx.getInstanceEx(myFixture.project).currentFile
    val taskFile = currentFile!!.getTaskFile(myFixture.project)
    val task = taskFile!!.task
    TestCase.assertEquals(1, task.index)
    val lesson = task.lesson
    TestCase.assertEquals(2, lesson.index)
    TestCase.assertNotNull(lesson.section)
    TestCase.assertEquals(2, lesson.section!!.index)
  }

  fun `test next from section to section`() {
    configureByTaskFile(2, 2, 1, "taskFile.txt")
    testAction(NextTaskAction.ACTION_ID)
    val currentFile = FileEditorManagerEx.getInstanceEx(myFixture.project).currentFile
    val taskFile = currentFile!!.getTaskFile(myFixture.project)
    val task = taskFile!!.task
    TestCase.assertEquals(1, task.index)
    val lesson = task.lesson
    TestCase.assertEquals(1, lesson.index)
    TestCase.assertNotNull(lesson.section)
    TestCase.assertEquals(3, lesson.section!!.index)
  }

  fun `test next from section to lesson`() {
    configureByTaskFile(3, 1, 1, "taskFile.txt")
    testAction(NextTaskAction.ACTION_ID)
    val currentFile = FileEditorManagerEx.getInstanceEx(myFixture.project).currentFile
    val taskFile = currentFile!!.getTaskFile(myFixture.project)
    val task = taskFile!!.task
    TestCase.assertEquals(1, task.index)
    val lesson = task.lesson
    TestCase.assertEquals(4, lesson.index)
  }

  fun `test previous from lesson to section`() {
    configureByTaskFile(4, 1, "taskFile.txt")
    testAction(PreviousTaskAction.ACTION_ID)
    val currentFile = FileEditorManagerEx.getInstanceEx(myFixture.project).currentFile
    val taskFile = currentFile!!.getTaskFile(myFixture.project)
    val task = taskFile!!.task
    TestCase.assertEquals(1, task.index)
    val lesson = task.lesson
    TestCase.assertEquals(1, lesson.index)
    TestCase.assertNotNull(lesson.section)
    TestCase.assertEquals(3, lesson.section!!.index)
  }

  fun `test previous from section to section`() {
    configureByTaskFile(3, 1, 1, "taskFile.txt")
    testAction(PreviousTaskAction.ACTION_ID)
    val currentFile = FileEditorManagerEx.getInstanceEx(myFixture.project).currentFile
    val taskFile = currentFile!!.getTaskFile(myFixture.project)
    val task = taskFile!!.task
    TestCase.assertEquals(1, task.index)
    val lesson = task.lesson
    TestCase.assertEquals(2, lesson.index)
    TestCase.assertNotNull(lesson.section)
    TestCase.assertEquals(2, lesson.section!!.index)
  }

  fun `test previous lesson in same section`() {
    configureByTaskFile(2, 2, 1, "taskFile.txt")
    testAction(PreviousTaskAction.ACTION_ID)
    val currentFile = FileEditorManagerEx.getInstanceEx(myFixture.project).currentFile
    val taskFile = currentFile!!.getTaskFile(myFixture.project)
    val task = taskFile!!.task
    TestCase.assertEquals(2, task.index)
    val lesson = task.lesson
    TestCase.assertEquals(1, lesson.index)
    TestCase.assertNotNull(lesson.section)
    TestCase.assertEquals(2, lesson.section!!.index)
  }

  fun `test previous task same lesson in same section`() {
    configureByTaskFile(2, 1, 2, "taskFile.txt")
    testAction(PreviousTaskAction.ACTION_ID)
    val currentFile = FileEditorManagerEx.getInstanceEx(myFixture.project).currentFile
    val taskFile = currentFile!!.getTaskFile(myFixture.project)
    val task = taskFile!!.task
    TestCase.assertEquals(1, task.index)
    val lesson = task.lesson
    TestCase.assertEquals(1, lesson.index)
    TestCase.assertNotNull(lesson.section)
    TestCase.assertEquals(2, lesson.section!!.index)
  }

  fun `test previous from section to lesson`() {
    configureByTaskFile(2, 1, 1, "taskFile.txt")
    testAction(PreviousTaskAction.ACTION_ID)
    val currentFile = FileEditorManagerEx.getInstanceEx(myFixture.project).currentFile
    val taskFile = currentFile!!.getTaskFile(myFixture.project)
    val task = taskFile!!.task
    TestCase.assertEquals(1, task.index)
    val lesson = task.lesson
    TestCase.assertEquals(1, lesson.index)
  }

  override fun createCourse() {
    courseWithFiles {
      lesson {
        eduTask {
          taskFile("taskFile.txt")
        }
      }
      section {
        lesson {
          eduTask {
            taskFile("taskFile.txt")
          }
          eduTask {
            taskFile("taskFile.txt")
          }
        }
        lesson {
          eduTask {
            taskFile("taskFile.txt")
          }
        }
      }
      section {
        lesson {
          eduTask {
            taskFile("taskFile.txt")
          }
        }
      }
      lesson(name="lesson4") {
        eduTask {
          taskFile("taskFile.txt")
        }
      }
    }
  }

  private fun configureByTaskFile(sectionIndex: Int, lessonIndex: Int, taskIndex: Int, taskFileName: String) {
    val fileName = "section$sectionIndex/lesson$lessonIndex/task$taskIndex/$taskFileName"
    val file = myFixture.findFileInTempDir(fileName)
    myFixture.configureFromExistingVirtualFile(file)
    FileEditorManager.getInstance(myFixture.project).openFile(file, true)
    TaskDescriptionView.getInstance(myFixture.project).currentTask = myFixture.project.getCurrentTask()
  }
}
