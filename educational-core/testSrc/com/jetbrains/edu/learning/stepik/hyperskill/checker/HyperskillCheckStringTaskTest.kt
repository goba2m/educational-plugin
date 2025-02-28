package com.jetbrains.edu.learning.stepik.hyperskill.checker

import com.jetbrains.edu.learning.actions.CheckAction
import com.jetbrains.edu.learning.checker.CheckActionListener
import com.jetbrains.edu.learning.course
import com.jetbrains.edu.learning.courseFormat.Course
import com.jetbrains.edu.learning.courseFormat.ext.allTasks
import com.jetbrains.edu.learning.courseFormat.tasks.AnswerTask
import com.jetbrains.edu.learning.courseFormat.tasks.StringTask
import com.jetbrains.edu.learning.messages.EduCoreBundle
import com.jetbrains.edu.learning.navigation.NavigationUtils
import com.jetbrains.edu.learning.stepik.Step
import com.jetbrains.edu.learning.stepik.StepSource
import com.jetbrains.edu.learning.stepik.StepikTaskBuilder
import com.jetbrains.edu.learning.stepik.hyperskill.api.HyperskillConnector
import com.jetbrains.edu.learning.stepik.hyperskill.api.MockHyperskillConnector
import com.jetbrains.edu.learning.stepik.hyperskill.courseFormat.HyperskillCourse
import com.jetbrains.edu.learning.stepik.hyperskill.logInFakeHyperskillUser
import com.jetbrains.edu.learning.stepik.hyperskill.logOutFakeHyperskillUser
import com.jetbrains.edu.learning.testAction
import com.jetbrains.edu.learning.ui.getUICheckLabel
import org.apache.http.HttpStatus

class HyperskillCheckStringTaskTest : HyperskillCheckAnswerTaskTest() {
  override val defaultResponseCode: Int = HttpStatus.SC_OK

  override val mockConnector: MockHyperskillConnector
    get() = HyperskillConnector.getInstance() as MockHyperskillConnector

  override fun setUp() {
    super.setUp()
    logInFakeHyperskillUser()
  }

  override fun tearDown() {
    logOutFakeHyperskillUser()
    super.tearDown()
  }

  override fun createCourse(): Course = course(courseProducer = ::HyperskillCourse) {
    section(SECTION) {
      lesson(LESSON) {
        stringTask(stepId = 1, name = "0_string_task") {
          taskFile(AnswerTask.ANSWER_FILE_NAME, text = "<p>answer</p>")
        }
        stringTask(stepId = 1, name = "1_string_task") {
          taskFile(AnswerTask.ANSWER_FILE_NAME, "<p></p>")
        }
        stringTask(stepId = 1, name = "2_string_task") {
          taskFile(AnswerTask.ANSWER_FILE_NAME, "<p>answer</p>")
          taskFile("taskFile.txt", "text")
        }
        stringTask(stepId = 1, name = "3_answer_file_in_src") {
          dir("src") {
            taskFile(AnswerTask.ANSWER_FILE_NAME, "<p>answer</p>")
          }
        }
      }
    }
  }

  fun `test string task correct`() {
    configureResponses(true)

    CheckActionListener.reset()
    CheckActionListener.expectedMessage { "<html>Succeed solution</html>" }
    val task = myCourse.allTasks[0] as StringTask
    NavigationUtils.navigateToTask(project, task)
    testAction(CheckAction(task.getUICheckLabel()))
    assertEquals("answer", task.getInputAnswer(project))
  }

  fun `test string task incorrect`() {
    configureResponses(false)

    CheckActionListener.shouldFail()
    CheckActionListener.expectedMessage { "Wrong solution" }
    val task = myCourse.allTasks[0]
    NavigationUtils.navigateToTask(project, task)
    testAction(CheckAction(task.getUICheckLabel()))
  }

  fun `test string task input is empty`() {
    CheckActionListener.shouldFail()
    CheckActionListener.expectedMessage { EduCoreBundle.message("hyperskill.string.task.empty.text") }
    val task = myCourse.allTasks[1]
    NavigationUtils.navigateToTask(project, task)
    testAction(CheckAction(task.getUICheckLabel()))
  }

  fun `test creating placeholder`() {
    val task = myCourse.allTasks[0] as StringTask
    val lesson = task.lesson
    val stepSource = StepSource().apply {
      block = Step().apply {
        name = "string"
      }
    }

    val createdTask = StepikTaskBuilder(myCourse, lesson, stepSource).createTask(stepSource.block?.name!!) ?: error("")
    assertEquals(1, createdTask.taskFiles.size)
    assertEquals(1, createdTask.getTaskFile(AnswerTask.ANSWER_FILE_NAME)?.answerPlaceholders?.size)
    assertEquals(
      EduCoreBundle.message("string.task.comment.file"),
      createdTask.getTaskFile(AnswerTask.ANSWER_FILE_NAME)?.answerPlaceholders?.first()?.placeholderText)
    assertEquals(0, createdTask.getTaskFile(AnswerTask.ANSWER_FILE_NAME)?.answerPlaceholders?.first()?.offset)
    assertEquals(
      EduCoreBundle.message("string.task.comment.file").length,
      createdTask.getTaskFile(AnswerTask.ANSWER_FILE_NAME)?.answerPlaceholders?.first()?.endOffset)
  }

  /**
   * test method
   * [TrailingSpacesOptionsAnswerTaskProvider.AnswerOptions.getEnsureNewLineAtEOF]
   *
   * method getEnsureNewLineAtEOF allow adding blank line to the end of file.
   * This option is enabled from the settings by checking the box "ensure every saved file ends with a line break".
   * In the answerTask for file with name [AnswerTask.ANSWER_FILE_NAME] this option must be disabled.
   */
  fun `test string task new line at eof for answer_txt`() {
    testWithEnabledEnsureNewLineAtEOFSetting {
      val task = myCourse.allTasks[2] as StringTask
      val textForSaving = "test StringTask new line at eof for answer_txt"
      val text = getSavedTextInFile(task, AnswerTask.ANSWER_FILE_NAME, textForSaving, project)
      assertEquals(textForSaving, text)
      assertEquals(textForSaving, task.getInputAnswer(project))
    }
  }

  /**
   * Test that new line at the end of file for AnswerTask appear only in [AnswerTask.ANSWER_FILE_NAME].
   * Blank line must add for others files at AnswerTask
   */
  fun `test string task new line at eof for task file`() {
    testWithEnabledEnsureNewLineAtEOFSetting {
      val task = myCourse.allTasks[2] as StringTask
      val textForSaving = "test StringTask new line at eof for task file"
      val text = getSavedTextInFile(task, "taskFile.txt", textForSaving, project)
      assertEquals("$textForSaving\n", text)
    }
  }

  fun `test src answer file`() {
    testWithEnabledEnsureNewLineAtEOFSetting {
      val task = myCourse.allTasks[3] as StringTask
      val textForSaving = "test src answer file"
      val text = getSavedTextInFile(task, "src/${AnswerTask.ANSWER_FILE_NAME}", textForSaving, project)
      assertEquals("$textForSaving\n", text)
    }
  }
}