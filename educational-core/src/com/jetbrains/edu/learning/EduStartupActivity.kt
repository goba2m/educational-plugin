package com.jetbrains.edu.learning

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.jetbrains.edu.coursecreator.CCUtils
import com.jetbrains.edu.coursecreator.SynchronizeTaskDescription
import com.jetbrains.edu.coursecreator.handlers.CCVirtualFileListener
import com.jetbrains.edu.learning.EduUtilsKt.isEduProject
import com.jetbrains.edu.learning.EduUtilsKt.isNewlyCreated
import com.jetbrains.edu.learning.EduUtilsKt.isStudentProject
import com.jetbrains.edu.learning.courseFormat.Course
import com.jetbrains.edu.learning.courseFormat.ext.configurator
import com.jetbrains.edu.learning.courseFormat.ext.isPreview
import com.jetbrains.edu.learning.courseFormat.tasks.choice.ChoiceTask
import com.jetbrains.edu.learning.handlers.UserCreatedFileListener
import com.jetbrains.edu.learning.messages.EduCoreBundle
import com.jetbrains.edu.learning.navigation.NavigationUtils
import com.jetbrains.edu.learning.navigation.NavigationUtils.setHighlightLevelForFilesInTask
import com.jetbrains.edu.learning.newproject.coursesStorage.CoursesStorage
import com.jetbrains.edu.learning.projectView.CourseViewPane
import com.jetbrains.edu.learning.statistics.EduCounterUsageCollector
import com.jetbrains.edu.learning.stepik.StepikNames
import com.jetbrains.edu.learning.stepik.course.StepikCourse
import com.jetbrains.edu.learning.stepik.hyperskill.courseFormat.HyperskillCourse
import com.jetbrains.edu.learning.taskDescription.ui.TaskDescriptionView

class EduStartupActivity : StartupActivity.DumbAware {

  private val YAML_MIGRATED = "Edu.Yaml.Migrate"

  override fun runActivity(project: Project) {
    if (!project.isEduProject()) return

    val manager = StudyTaskManager.getInstance(project)
    val connection = ApplicationManager.getApplication().messageBus.connect(manager)
    if (!isUnitTestMode) {
      val vfsListener = if (project.isStudentProject()) UserCreatedFileListener(project) else CCVirtualFileListener(project, manager)
      connection.subscribe(VirtualFileManager.VFS_CHANGES, vfsListener)

      if (CCUtils.isCourseCreator(project)) {
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(SynchronizeTaskDescription(project), manager)
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(CourseIgnoreDocumentListener(project), manager)
      }
      EduDocumentListener.setGlobalListener(project, manager)
    }

    connection.subscribe(EditorColorsManager.TOPIC, EditorColorsListener {
      TaskDescriptionView.updateAllTabs(project)
    })

    StartupManager.getInstance(project).runWhenProjectIsInitialized {
      val course = manager.course
      if (course == null) {
        LOG.warn("Opened project is with null course")
        return@runWhenProjectIsInitialized
      }

      if (course is StepikCourse) {
        val notification = Notification("JetBrains Academy", StepikNames.STEPIK, EduCoreBundle.message("stepik.is.not.supported"),
                                        NotificationType.WARNING)
        notification.notify(null)
      }

      val fileEditorManager = FileEditorManager.getInstance(project)
      if (!fileEditorManager.hasOpenFiles()) {
        NavigationUtils.openFirstTask(course, project)
      }
      selectProjectView(project, true)

      migrateYaml(project, course)

      setupProject(project, course)
      val coursesStorage = CoursesStorage.getInstance()
      val location = project.basePath
      if (!coursesStorage.hasCourse(course) && location != null && !course.isPreview) {
        coursesStorage.addCourse(course, location)
      }

      runWriteAction {
        if (project.isStudentProject()) {
          course.visitTasks {
            setHighlightLevelForFilesInTask(it, project)
          }
        }

        EduCounterUsageCollector.eduProjectOpened(course)
      }
    }
  }

  private fun migrateYaml(project: Project, course: Course) {
    val propertyComponent = PropertiesComponent.getInstance(project)
    if (propertyComponent.getBoolean(YAML_MIGRATED)) return
    propertyComponent.setValue(YAML_MIGRATED, true)
    if (course !is HyperskillCourse && course !is StepikCourse) return

    course.visitTasks {
      if (it is ChoiceTask) {
        it.canCheckLocally = false
      }
    }
  }

  // In general, it's hack to select proper Project View pane for course projects
  // Should be replaced with proper API
  private fun selectProjectView(project: Project, retry: Boolean) {
    ToolWindowManager.getInstance(project).invokeLater(Runnable {
      val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW)
      // Since 2020.1 project view tool window can be uninitialized here yet
      if (toolWindow == null) {
        if (retry) {
          selectProjectView(project, false)
        }
        else {
          LOG.warn("Failed to show Course View because Project View is not initialized yet")
        }
        return@Runnable
      }
      val projectView = ProjectView.getInstance(project)
      if (projectView != null) {
        val selectedViewId = ProjectView.getInstance(project).currentViewId
        if (CourseViewPane.ID != selectedViewId) {
          projectView.changeView(CourseViewPane.ID)
        }
      }
      else {
        LOG.warn("Failed to select Project View")
      }
      toolWindow.show()
    })
  }

  private fun setupProject(project: Project, course: Course) {
    val configurator = course.configurator
    if (configurator == null) {
      LOG.warn("Failed to refresh gradle project: configurator for `${course.languageId}` is null")
      return
    }

    if (!isUnitTestMode && project.isNewlyCreated()) {
      configurator.courseBuilder.refreshProject(project, RefreshCause.PROJECT_CREATED)
    }

    // Android Studio creates `gradlew` not via VFS, so we have to refresh project dir
    VfsUtil.markDirtyAndRefresh(false, true, true, project.courseDir)
  }

  companion object {
    private val LOG = Logger.getInstance(EduStartupActivity::class.java)
  }
}
