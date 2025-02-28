package com.jetbrains.edu.learning.stepik.hyperskill.checker

import com.jetbrains.edu.learning.EduTestCase
import com.jetbrains.edu.learning.MockResponseFactory
import com.jetbrains.edu.learning.actions.CheckAction
import com.jetbrains.edu.learning.checker.CheckActionListener
import com.jetbrains.edu.learning.pathWithoutPrams
import com.jetbrains.edu.learning.navigation.NavigationUtils
import com.jetbrains.edu.learning.stepik.hyperskill.*
import com.jetbrains.edu.learning.stepik.hyperskill.api.HyperskillConnector
import com.jetbrains.edu.learning.stepik.hyperskill.api.MockHyperskillConnector
import com.jetbrains.edu.learning.stepik.hyperskill.courseFormat.HyperskillCourse
import com.jetbrains.edu.learning.testAction
import com.jetbrains.edu.learning.ui.getUICheckLabel
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.intellij.lang.annotations.Language

class HyperskillCheckCodeTaskTest : EduTestCase() {
  private val mockConnector: MockHyperskillConnector get() = HyperskillConnector.getInstance() as MockHyperskillConnector

  override fun setUp() {
    super.setUp()
    courseWithFiles(courseProducer = ::HyperskillCourse) {
      lesson("Problems") {
        codeTask("task1", stepId = 4) {
          taskFile("Task.txt", "fun foo() {}")
        }
      }
    } as HyperskillCourse

    logInFakeHyperskillUser()
    NavigationUtils.navigateToTask(project, findTask(0, 0))
  }

  override fun tearDown() {
    logOutFakeHyperskillUser()
    super.tearDown()
  }

  fun `test successful check via web socket`() {
    configureResponses()

    var state: MockWebSocketState = MockWebSocketState.INITIAL

    mockConnector.withWebSocketListener(object : WebSocketListener() {
      override fun onMessage(webSocket: WebSocket, text: String) {
        when (state) {
          MockWebSocketState.INITIAL -> {
            webSocket.confirmConnection()
            state = MockWebSocketState.CONNECTION_CONFIRMED
          }
          MockWebSocketState.CONNECTION_CONFIRMED -> {
            webSocket.confirmSubscription()
            webSocket.send(submissionResult)
          }
        }
      }
    })

    doTest()
  }

  fun `test submission made, result not received via web socket`() {
    configureResponses()

    var state: MockWebSocketState = MockWebSocketState.INITIAL

    mockConnector.withWebSocketListener(object : WebSocketListener() {
      override fun onMessage(webSocket: WebSocket, text: String) {
        when (state) {
          MockWebSocketState.INITIAL -> {
            webSocket.confirmConnection()
            state = MockWebSocketState.CONNECTION_CONFIRMED
          }
          MockWebSocketState.CONNECTION_CONFIRMED -> {
            webSocket.confirmSubscription()
            Thread.sleep(500)
            webSocket.cancel() // close violently otherwise need to wait until full timeout exceeded
          }
        }
      }
    })

    doTest()
  }

  fun `test no submission made, result received via REST API`() {
    configureResponses()

    mockConnector.withWebSocketListener(object : WebSocketListener() {
      override fun onMessage(webSocket: WebSocket, text: String) {
        webSocket.cancel() // close violently
      }
    })

    doTest()
  }

  fun `test failed to get submission status via API`() {
    mockConnector.withResponseHandler(testRootDisposable) { request, _ ->
      MockResponseFactory.fromString(
        when (val path = request.pathWithoutPrams) {
          "/api/ws" -> webSocketConfiguration
          "/api/attempts" -> attempt
          "/api/submissions" -> submission
          else -> if (path.startsWith("/api/submissions/")) submissionWithEvaluationStatus else "{}"
        }
      )
    }

    mockConnector.withWebSocketListener(object : WebSocketListener() {
      override fun onMessage(webSocket: WebSocket, text: String) {
        webSocket.cancel() // close violently
      }
    })

    CheckActionListener.shouldSkip()
    doTest()
//    val task = findTask(0, 0)
//    testAction(CheckAction(task))
  }

  private fun configureResponses() {
    mockConnector.withResponseHandler(testRootDisposable) { request, _ ->
      MockResponseFactory.fromString(
        when (val path = request.pathWithoutPrams) {
          "/api/ws" -> webSocketConfiguration
          "/api/attempts" -> attempt
          "/api/submissions" -> submission
          else -> if (path.startsWith("/api/submissions/")) submissionWithWrongStatus else "{}"
        }
      )
    }
  }

  private fun doTest() {
    CheckActionListener.shouldFail()
    CheckActionListener.expectedMessage { "Failed" }
    val task = findTask(0, 0)
    testAction(CheckAction(task.getUICheckLabel()))
  }

  @Language("JSON")
  private val submissionWithWrongStatus = """
    {
      "meta": {
        "page": 1,
        "has_next": false,
        "has_previous": false
      },
      "submissions": [
        {
          "id": 7565000,
          "attempt": 7565799,
          "feedback": {
            "message": "Failed"
          },
          "hint": "Failed",
          "reply": {
            "language": "kotlin",
            "code": "fun main() {\n    TODO(\"Remove this line and write your solution here\")\n}\n"
          },
          "status": "wrong",
          "step": 4368,
          "time": "2020-04-29T13:39:55Z"
        }
      ]
    }
  """

  @Language("JSON")
  private val submissionWithEvaluationStatus = """
    {
      "meta": {
        "page": 1,
        "has_next": false,
        "has_previous": false
      },
      "submissions": [
        {
          "id": 7565000,
          "attempt": 7565799,
          "feedback": {
            "message": "Failed"
          },
          "hint": "Failed",
          "reply": {
            "language": "kotlin",
            "code": "fun main() {\n    TODO(\"Remove this line and write your solution here\")\n}\n"
          },
          "status": "evaluation",
          "step": 4368,
          "time": "2020-04-29T13:39:55Z"
        }
      ]
    }
  """

  @Language("JSON")
  private val submissionResult: String = """
    {
      "push": {
        "channel": "submission#6242591-0",
        "pub": {
          "data": $submissionWithWrongStatus
        }
      }
    }    
   """

  @Language("JSON")
  private val attempt = """
    {
      "meta": {
        "page": 1,
        "has_next": false,
        "has_previous": false
      },
      "attempts": [
        {
          "dataset": "",
          "id": 7565799,
          "status": "active",
          "step": 4368,
          "time": "2020-04-29T11:44:20.422Z",
          "user": 6242591
        }
      ]
    }
  """

  @Language("JSON")
  private val submission = """
    {
      "meta": {
        "page": 1,
        "has_next": false,
        "has_previous": false
      },
      "submissions": [
        {
          "attempt": "7565799",
          "id": "7565000",
          "status": "evaluation",
          "step": 4368,
          "time": "2020-04-29T11:44:20.422Z",
          "user": 6242591
        }
      ]
    }
  """
}