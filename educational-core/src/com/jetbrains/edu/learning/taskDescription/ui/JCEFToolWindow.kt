package com.jetbrains.edu.learning.taskDescription.ui

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefClient
import com.intellij.ui.jcef.JCEFHtmlPanel
import com.intellij.util.ui.JBUI
import com.jetbrains.edu.learning.JavaUILibrary
import com.jetbrains.edu.learning.courseFormat.tasks.Task
import com.jetbrains.edu.learning.taskDescription.ui.jcefSpecificQueries.TaskQueryManager
import org.cef.browser.CefBrowser
import org.cef.handler.CefFocusHandlerAdapter
import org.jetbrains.annotations.TestOnly
import javax.swing.JComponent

class JCEFToolWindow(project: Project) : TaskDescriptionToolWindow(project) {
  private val taskInfoJBCefBrowser = JCEFHtmlPanel(true, JBCefApp.getInstance().createClient(), null)
  private val taskSpecificJBCefBrowser = JCEFHtmlPanel(true, JBCefApp.getInstance().createClient(), null)
  private var taskSpecificQueryManager: TaskQueryManager<out Task>? = null

  init {
    val jcefLinkInToolWindowHandler = JCefToolWindowLinkHandler(project)
    val taskInfoRequestHandler = JCEFToolWindowRequestHandler(jcefLinkInToolWindowHandler)
    taskInfoJBCefBrowser.jbCefClient.addRequestHandler(taskInfoRequestHandler, taskInfoJBCefBrowser.cefBrowser)
    val taskInfoLifeSpanHandler = JCEFTaskInfoLifeSpanHandler(jcefLinkInToolWindowHandler)
    taskInfoJBCefBrowser.jbCefClient.addLifeSpanHandler(taskInfoLifeSpanHandler, taskInfoJBCefBrowser.cefBrowser)

    taskSpecificJBCefBrowser.apply {
      setProperty(JBCefBrowser.Properties.FOCUS_ON_NAVIGATION, true)

      val taskSpecificFocusHandlerAdapter = object : CefFocusHandlerAdapter() {
        override fun onTakeFocus(browser: CefBrowser?, next: Boolean) {
          component.requestFocusInWindow()
        }
      }
      jbCefClient.addFocusHandler(taskSpecificFocusHandlerAdapter, cefBrowser)
      jbCefClient.setProperty(JBCefClient.Properties.JS_QUERY_POOL_SIZE, TASK_SPECIFIC_PANEL_JS_QUERY_POOL_SIZE)
    }

    Disposer.register(this, taskInfoJBCefBrowser)
    Disposer.register(this, taskSpecificJBCefBrowser)

    ApplicationManager.getApplication().messageBus.connect(this)
      .subscribe(LafManagerListener.TOPIC,
                 LafManagerListener { TaskDescriptionView.updateAllTabs(project) })
  }

  override val taskInfoPanel: JComponent
    get() = taskInfoJBCefBrowser.component

  override val taskSpecificPanel: JComponent
    get() = taskSpecificJBCefBrowser.component

  override val uiMode: JavaUILibrary
    get() = JavaUILibrary.JCEF

  override fun setText(text: String) {
    taskInfoJBCefBrowser.loadHTML(text)
  }

  override fun updateTaskSpecificPanel(task: Task?) {
    taskSpecificJBCefBrowser.component.isVisible = false

    val taskText = getHTMLTemplateText(task) ?: return

    // Dispose taskSpecificQueryManager manually because this disposes existing JSQueries and removes them from JS_QUERY_POOL
    taskSpecificQueryManager?.let {
      Disposer.dispose(it)
    }

    taskSpecificQueryManager = getTaskSpecificQueryManager(task, taskSpecificJBCefBrowser)

    taskSpecificJBCefBrowser.component.preferredSize = JBUI.size(Int.MAX_VALUE, 250)
    val html = htmlWithResources(project, taskText, task)
    taskSpecificJBCefBrowser.loadHTML(html)
    taskSpecificJBCefBrowser.component.isVisible = true
  }

  override fun dispose() {
    super.dispose()
    // Dispose undisposed yet taskSpecificQueryManager
    taskSpecificQueryManager?.let {
      Disposer.dispose(it)
    }
  }

  companion object {
    // maximum number of created qs queries in taskSpecificQueryManager
    private const val TASK_SPECIFIC_PANEL_JS_QUERY_POOL_SIZE = 2

    @TestOnly
    fun processContent(content: String, project: Project): String {
      return htmlWithResources(project, content)
    }
  }
}
