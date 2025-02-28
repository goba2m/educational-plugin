package com.jetbrains.edu.coursecreator.taskDescription

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.ThrowableRunnable
import com.jetbrains.edu.learning.EduTestCase
import com.jetbrains.edu.learning.courseFormat.CourseMode
import com.jetbrains.edu.learning.courseFormat.DescriptionFormat
import org.intellij.lang.annotations.Language

class TaskDescriptionLiveTemplateTest : EduTestCase() {

  override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
    // https://youtrack.jetbrains.com/issue/EDU-6069/Fix-TaskDescriptionLiveTemplateTest-for-232
    if (ApplicationInfo.getInstance().build < BUILD_232) {
      super.runTestRunnable(testRunnable)
    }
  }
  fun `test hint live template in task description file in cc mode`() {
    createCourse(CourseMode.EDUCATOR)

    expandSnippet("lesson/task/task.html", """
      <html>
      hint<caret>
      </html>
    """, """
      <html>
      <div class="hint">
        Hint text
      </div>
      
      </html>
    """)
  }

  fun `test no hint live template in task description file in student mode`() {
    createCourse(CourseMode.STUDENT)

    expandSnippet("lesson/task/task.html", """
      <html>
      hint<caret>
      </html>
    """, """
      <html>
      <hint></hint>
      </html>
    """)
  }

  fun `test no hint live template in non task description file`() {
    createCourse(CourseMode.EDUCATOR)

    expandSnippet("lesson/task/taskFile.html", """
      <html>
      hint<caret>
      </html>
    """, """
      <html>
      <hint></hint>
      </html>
    """)
  }

  private fun createCourse(courseMode: CourseMode) {
    courseWithFiles(courseMode = courseMode) {
      lesson("lesson") {
        eduTask("task", taskDescriptionFormat = DescriptionFormat.HTML) {
          taskFile("taskFile.html",)
        }
      }
    }
  }

  private fun expandSnippet(filePath: String, @Language("HTML") before: String, @Language("HTML") after: String) {
    val file = myFixture.findFileInTempDir(filePath)

    runWriteAction {
      VfsUtil.saveText(file, before.trimIndent())
    }
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.performEditorAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB)
    myFixture.checkResult(after.trimIndent())
  }

  companion object {
    private val BUILD_232: BuildNumber = BuildNumber.fromString("232")!!
  }
}
