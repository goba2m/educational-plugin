package com.jetbrains.edu.learning.stepik.hyperskill.checker

import com.jetbrains.edu.learning.MockResponseFactory
import com.jetbrains.edu.learning.actions.CheckAction
import com.jetbrains.edu.learning.checker.CheckActionListener
import com.jetbrains.edu.learning.course
import com.jetbrains.edu.learning.courseFormat.Course
import com.jetbrains.edu.learning.courseFormat.ext.allTasks
import com.jetbrains.edu.learning.courseFormat.tasks.AnswerTask
import com.jetbrains.edu.learning.courseFormat.tasks.EduTask
import com.jetbrains.edu.learning.courseFormat.tasks.NumberTask
import com.jetbrains.edu.learning.messages.EduCoreBundle
import com.jetbrains.edu.learning.navigation.NavigationUtils
import com.jetbrains.edu.learning.pathWithoutPrams
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

class HyperskillCheckNumberTaskTest : HyperskillCheckAnswerTaskTest() {
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
        numberTask(stepId = 1, name = "0_number_task_non_number") {
          taskFile(AnswerTask.ANSWER_FILE_NAME, text = "<p>answer</p>")
        }
        numberTask(stepId = 1, name = "1_number_task_empty") {
          taskFile(AnswerTask.ANSWER_FILE_NAME, "<p></p>")
        }
        numberTask(stepId = 1, name = "2_number_task_correct") {
          taskFile(AnswerTask.ANSWER_FILE_NAME, text = "<p>12</p>")
        }
        numberTask(stepId = 1, name = "3_number_task_with_space") {
          taskFile(AnswerTask.ANSWER_FILE_NAME, text = "<p>12   </p>")
        }
        numberTask(stepId = 1, name = "4_number_task_with_comma") {
          taskFile(AnswerTask.ANSWER_FILE_NAME, text = "<p>\n12   </p>")
        }
        numberTask(stepId = 1, name = "5_number_task_with_comma") {
          taskFile(AnswerTask.ANSWER_FILE_NAME, text = "<p>  12   </p>")
        }
        numberTask(stepId = 1, name = "6_number_task_with_space") {
          taskFile(AnswerTask.ANSWER_FILE_NAME, text = "<p>1,2</p>")
        }
        numberTask(stepId = 1, name = "7_number_task_with_comma") {
          taskFile(AnswerTask.ANSWER_FILE_NAME, text = "<p>1.2</p>")
        }
        numberTask(stepId = 1, name = "8_number_task_ne_line_at_eof") {
          taskFile(AnswerTask.ANSWER_FILE_NAME, text = "<p>1.2</p>")
          taskFile("taskFile.txt", text = "")
        }
        eduTask(name = "9_edu_task") {
          taskFile("test.txt", text = "text")
        }
      }
    }
  }

  fun `test number task correct`() {
    configureResponses(true)

    CheckActionListener.reset()
    CheckActionListener.expectedMessage { "<html>Succeed solution</html>" }
    val task = myCourse.allTasks[2] as NumberTask
    NavigationUtils.navigateToTask(project, task)
    testAction(CheckAction(task.getUICheckLabel()))
    assertEquals("12", task.getInputAnswer(project))
  }

  fun `test number task incorrect`() {
    configureResponses(false)

    CheckActionListener.shouldFail()
    CheckActionListener.expectedMessage { "Wrong solution" }
    val task = myCourse.allTasks[2]
    NavigationUtils.navigateToTask(project, task)
    testAction(CheckAction(task.getUICheckLabel()))
  }

  fun `test number task validation on number`() {
    mockConnector.withResponseHandler(testRootDisposable) { request, _ ->
      MockResponseFactory.fromString(
        when (val path = request.pathWithoutPrams) {
          "/api/attempts" -> attempt
          "/api/submissions" -> submission
          "/api/submissions/11" -> submissionWithFailedStatus
          else -> error("Wrong path: ${path}")
        }
      )
    }

    CheckActionListener.shouldFail()
    CheckActionListener.expectedMessage { EduCoreBundle.message("hyperskill.number.task.not.number") }
    val task = myCourse.allTasks[0]
    NavigationUtils.navigateToTask(project, task)
    testAction(CheckAction(task.getUICheckLabel()))
  }

  fun `test number task input is empty`() {
    CheckActionListener.shouldFail()
    CheckActionListener.expectedMessage { EduCoreBundle.message("hyperskill.string.task.empty.text") }
    val task = myCourse.allTasks[1]
    NavigationUtils.navigateToTask(project, task)
    testAction(CheckAction(task.getUICheckLabel()))
  }

  fun `test creating placeholder`() {
    val task = myCourse.allTasks[0] as NumberTask
    val lesson = task.lesson
    val stepSource = StepSource().apply {
      block = Step().apply {
        name = "number"
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

  fun `test task with space`() {
    assertNull((myCourse.allTasks[3] as NumberTask).validateAnswer (project))
    assertNull((myCourse.allTasks[4] as NumberTask).validateAnswer (project))
    assertNull((myCourse.allTasks[5] as NumberTask).validateAnswer (project))
  }

  fun `test task with comma and dot`() {
    assertNull((myCourse.allTasks[6] as NumberTask).validateAnswer (project))
    assertNull((myCourse.allTasks[7] as NumberTask).validateAnswer (project))
  }

  /**
   * test method
   * [TrailingSpacesOptionsAnswerTaskProvider.AnswerOptions.getEnsureNewLineAtEOF]
   *
   * method getEnsureNewLineAtEOF allow adding blank line to the end of file.
   * This option is enabled from the settings by checking the box "ensure every saved file ends with a line break".
   * In the answerTask for file with name [AnswerTask.ANSWER_FILE_NAME] this option must be disabled.
   */
  fun `test numberTask new line at eof for answer_txt`() {
    testWithEnabledEnsureNewLineAtEOFSetting {
      val task = myCourse.allTasks[8] as NumberTask
      val textForSaving = "test numberTask new line at eof for answer_txt"
      val text = getSavedTextInFile(task, AnswerTask.ANSWER_FILE_NAME, textForSaving, project)
      assertEquals(textForSaving, text)
      assertEquals(textForSaving, task.getInputAnswer(project))
    }
  }

  /**
   * Test that new line at the end of file for AnswerTask appear only in [AnswerTask.ANSWER_FILE_NAME].
   * Blank line must add for others files at AnswerTask
   */
  fun `test numberTask new line at eof for task file`() {
    testWithEnabledEnsureNewLineAtEOFSetting {
      val task = myCourse.allTasks[8] as NumberTask
      val textForSaving = "test numberTask new line at eof for task file"
      val text = getSavedTextInFile(task, "taskFile.txt", textForSaving, project)
      assertEquals("$textForSaving\n", text)
    }
  }

  /**
   * Test that new line at the end of file for AnswerTask appear only in [AnswerTask.ANSWER_FILE_NAME] file.
   * Blank line must add for others files at any task type
   */
  fun `test new line at eof for not answer task`() {
    testWithEnabledEnsureNewLineAtEOFSetting {
      val task = myCourse.allTasks[9] as EduTask
      val textForSaving = "test new line at eof for not answer task"
      val text = getSavedTextInFile(task, "test.txt", textForSaving, project)
      assertEquals("$textForSaving\n", text)
    }
  }
}