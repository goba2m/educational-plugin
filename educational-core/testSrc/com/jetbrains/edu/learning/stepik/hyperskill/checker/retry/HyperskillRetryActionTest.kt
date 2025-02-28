package com.jetbrains.edu.learning.stepik.hyperskill.checker.retry

import com.jetbrains.edu.learning.MockResponseFactory
import com.jetbrains.edu.learning.actions.RetryAction
import com.jetbrains.edu.learning.checker.CheckersTestBase
import com.jetbrains.edu.learning.checker.EduCheckerFixture
import com.jetbrains.edu.learning.checker.PlaintTextCheckerFixture
import com.jetbrains.edu.learning.course
import com.jetbrains.edu.learning.courseFormat.CheckStatus
import com.jetbrains.edu.learning.courseFormat.Course
import com.jetbrains.edu.learning.courseFormat.ext.allTasks
import com.jetbrains.edu.learning.courseFormat.tasks.choice.ChoiceOptionStatus
import com.jetbrains.edu.learning.courseFormat.tasks.choice.ChoiceTask
import com.jetbrains.edu.learning.navigation.NavigationUtils
import com.jetbrains.edu.learning.newproject.EmptyProjectSettings
import com.jetbrains.edu.learning.stepik.StepikTestUtils.format
import com.jetbrains.edu.learning.stepik.hyperskill.api.HyperskillConnector
import com.jetbrains.edu.learning.stepik.hyperskill.api.MockHyperskillConnector
import com.jetbrains.edu.learning.stepik.hyperskill.courseFormat.HyperskillCourse
import com.jetbrains.edu.learning.pathWithoutPrams
import com.jetbrains.edu.learning.stepik.hyperskill.logInFakeHyperskillUser
import com.jetbrains.edu.learning.stepik.hyperskill.logOutFakeHyperskillUser
import com.jetbrains.edu.learning.testAction
import org.intellij.lang.annotations.Language
import java.util.*

class HyperskillRetryActionTest : CheckersTestBase<EmptyProjectSettings>() {
  private val mockConnector: MockHyperskillConnector get() = HyperskillConnector.getInstance() as MockHyperskillConnector

  override fun createCheckerFixture(): EduCheckerFixture<EmptyProjectSettings> = PlaintTextCheckerFixture()

  override fun createCourse(): Course = course(courseProducer = ::HyperskillCourse) {
    section("Topics") {
      lesson("1_lesson_correct") {
        choiceTask(stepId = 5545,
                   name = "5545_choice_task",
                   isMultipleChoice = true,
                   choiceOptions = mapOf("2" to ChoiceOptionStatus.UNKNOWN,
                                         "1" to ChoiceOptionStatus.UNKNOWN,
                                         "0" to ChoiceOptionStatus.UNKNOWN),
                   status = CheckStatus.Failed) {
          taskFile("Task.txt", "")
        }
        eduTask(stepId = 2,
                name = "2_edu_task") {
          taskFile("Task.txt", "")
        }
      }
    }
  }

  override fun setUp() {
    super.setUp()
    logInFakeHyperskillUser()
  }

  override fun tearDown() {
    logOutFakeHyperskillUser()
    super.tearDown()
  }

  fun `test choice task correct`() {
    mockConnector.withResponseHandler(testRootDisposable) { request, _ ->
      MockResponseFactory.fromString(
        when (val path = request.pathWithoutPrams) {
          "/api/attempts" -> attempt
          else -> error("Wrong path: ${path}")
        }
      )
    }
    val task = myCourse.allTasks[0] as ChoiceTask

    NavigationUtils.navigateToTask(project, task)
    testAction(RetryAction.ACTION_ID)

    assertEquals("Name for ${task.name} doesn't match", "5545_choice_task", task.name)
    assertEquals("Status for ${task.name} doesn't match", CheckStatus.Unchecked, task.status)
    assertTrue("isMultipleChoice for ${task.name} doesn't match", task.isMultipleChoice)
    assertTrue("choiceOptions for ${task.name} doesn't match", task.selectedVariants.isEmpty())
    assertEquals("choiceOptions for ${task.name} doesn't match", mutableListOf("0", "1", "2"), task.choiceOptions.map { it.text })

  }

  fun `test is not changed on failed task`() {
    NavigationUtils.navigateToTask(project, myCourse.allTasks[1])
    testAction(RetryAction.ACTION_ID)
  }

  fun `test empty dataset`() {
    mockConnector.withResponseHandler(testRootDisposable) { request, _ ->
      MockResponseFactory.fromString(
        when (val path = request.pathWithoutPrams) {
          "/api/attempts" -> emptyDatasetAttempt
          else -> error("Wrong path: ${path}")
        }
      )
    }

    val task = myCourse.allTasks[0] as ChoiceTask
    NavigationUtils.navigateToTask(project, task)
    testAction(RetryAction.ACTION_ID)
    assertEquals("choiceOptions for ${task.name} doesn't match", mutableListOf("2", "1", "0"), task.choiceOptions.map { it.text })
  }

  @Language("JSON")
  private val attempt = """
    {
      "meta" : {
        "page" : 1,
        "has_next" : false,
        "has_previous" : false
      },
      "attempts" : [
        {
          "dataset" : {
            "is_multiple_choice" : true,
            "options" : [
              "0",
              "1",
              "2"
            ]
          },
          "id" : 48510847,
          "status" : "active",
          "step" : 5545,
          "time": "${Date().format()}",
          "user" : 1,
          "time_left" : null
        }
      ]
    }
  """

  @Language("JSON")
  private val emptyDatasetAttempt = """
    {
      "meta" : {
        "page" : 1,
        "has_next" : false,
        "has_previous" : false
      },
      "attempts" : [
        {
          "id" : 48510847,
          "status" : "active",
          "step" : 5545,
          "time": "${Date().format()}",
          "user" : 1,
          "time_left" : null
        }
      ]
    }
  """

}