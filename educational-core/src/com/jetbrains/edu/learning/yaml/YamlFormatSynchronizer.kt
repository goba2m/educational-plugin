package com.jetbrains.edu.learning.yaml

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.jsontype.NamedType
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.util.ui.JBUI
import com.jetbrains.edu.learning.*
import com.jetbrains.edu.learning.EduUtilsKt.isStudentProject
import com.jetbrains.edu.learning.checkio.courseFormat.CheckiOCourse
import com.jetbrains.edu.learning.checkio.courseFormat.CheckiOMission
import com.jetbrains.edu.learning.checkio.courseFormat.CheckiOStation
import com.jetbrains.edu.learning.checkio.utils.CheckiONames
import com.jetbrains.edu.learning.codeforces.CodeforcesNames
import com.jetbrains.edu.learning.codeforces.courseFormat.CodeforcesCourse
import com.jetbrains.edu.learning.codeforces.courseFormat.CodeforcesTask
import com.jetbrains.edu.learning.codeforces.courseFormat.CodeforcesTaskWithFileIO
import com.jetbrains.edu.learning.courseFormat.*
import com.jetbrains.edu.learning.courseFormat.ext.getDir
import com.jetbrains.edu.learning.courseFormat.ext.project
import com.jetbrains.edu.learning.courseFormat.tasks.CodeTask
import com.jetbrains.edu.learning.courseFormat.tasks.Task
import com.jetbrains.edu.learning.courseFormat.tasks.TheoryTask
import com.jetbrains.edu.learning.courseFormat.tasks.choice.ChoiceOption
import com.jetbrains.edu.learning.courseFormat.tasks.choice.ChoiceTask
import com.jetbrains.edu.learning.courseFormat.tasks.data.DataTask
import com.jetbrains.edu.learning.courseFormat.tasks.data.DataTaskAttempt
import com.jetbrains.edu.learning.courseFormat.tasks.matching.MatchingTask
import com.jetbrains.edu.learning.courseFormat.tasks.matching.SortingTask
import com.jetbrains.edu.learning.coursera.CourseraCourse
import com.jetbrains.edu.learning.coursera.CourseraNames
import com.jetbrains.edu.learning.json.encrypt.EncryptionModule
import com.jetbrains.edu.learning.json.encrypt.TEST_AES_KEY
import com.jetbrains.edu.learning.json.encrypt.getAesKey
import com.jetbrains.edu.learning.messages.EduCoreBundle
import com.jetbrains.edu.learning.stepik.StepikNames
import com.jetbrains.edu.learning.stepik.course.StepikCourse
import com.jetbrains.edu.learning.stepik.course.StepikLesson
import com.jetbrains.edu.learning.stepik.hyperskill.HYPERSKILL_TYPE_YAML
import com.jetbrains.edu.learning.stepik.hyperskill.api.HyperskillProject
import com.jetbrains.edu.learning.stepik.hyperskill.api.HyperskillStage
import com.jetbrains.edu.learning.stepik.hyperskill.api.HyperskillTopic
import com.jetbrains.edu.learning.stepik.hyperskill.courseFormat.HyperskillCourse
import com.jetbrains.edu.learning.stepik.hyperskill.courseFormat.RemoteEduTask
import com.jetbrains.edu.learning.yaml.YamlFormatSettings.COURSE_CONFIG
import com.jetbrains.edu.learning.yaml.YamlFormatSettings.LESSON_CONFIG
import com.jetbrains.edu.learning.yaml.YamlFormatSettings.REMOTE_COURSE_CONFIG
import com.jetbrains.edu.learning.yaml.YamlFormatSettings.REMOTE_LESSON_CONFIG
import com.jetbrains.edu.learning.yaml.YamlFormatSettings.REMOTE_SECTION_CONFIG
import com.jetbrains.edu.learning.yaml.YamlFormatSettings.REMOTE_TASK_CONFIG
import com.jetbrains.edu.learning.yaml.YamlFormatSettings.SECTION_CONFIG
import com.jetbrains.edu.learning.yaml.YamlFormatSettings.TASK_CONFIG
import com.jetbrains.edu.learning.yaml.format.*
import com.jetbrains.edu.learning.yaml.format.student.*
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.util.*
import javax.swing.JLabel
import javax.swing.JPanel

object YamlFormatSynchronizer {
  val LOAD_FROM_CONFIG = Key<Boolean>("Edu.loadItem")

  val MAPPER: ObjectMapper by lazy {
    val mapper = createMapper()
    addMixIns(mapper)

    mapper
  }

  @VisibleForTesting
  val REMOTE_MAPPER: ObjectMapper by lazy {
    val mapper = createMapper()
    addRemoteMixIns(mapper)

    mapper
  }

  @VisibleForTesting
  val STUDENT_MAPPER: ObjectMapper by lazy {
    val mapper = createMapper()
    addMixIns(mapper)
    mapper.addMixIn(TaskFile::class.java, StudentTaskFileYamlMixin::class.java)
    mapper.addMixIn(AnswerPlaceholder::class.java, StudentAnswerPlaceholderYamlMixin::class.java)
    mapper.addStudentMixIns()

    mapper
  }

  @VisibleForTesting
  val STUDENT_MAPPER_WITH_ENCRYPTION: ObjectMapper by lazy {
    val mapper = createMapper()
    addMixIns(mapper)
    val aesKey = if (!isUnitTestMode) getAesKey() else TEST_AES_KEY
    mapper.registerModule(EncryptionModule(aesKey))
    mapper.addMixIn(TaskFile::class.java, StudentEncryptedTaskFileYamlMixin::class.java)
    mapper.addMixIn(AnswerPlaceholder::class.java, StudentEncryptedAnswerPlaceholderYamlMixin::class.java)
    mapper.addStudentMixIns()

    mapper
  }

  private fun createMapper(): ObjectMapper {
    val yamlFactory = YAMLFactory.builder()
      .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
      .disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID)
      .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
      .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
      .build()

    return JsonMapper.builder(yamlFactory)
      .addModule(kotlinModule())
      .addModule(JavaTimeModule())
      .defaultLocale(Locale.ENGLISH)
      .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
      .serializationInclusion(JsonInclude.Include.NON_EMPTY)
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .disable(MapperFeature.AUTO_DETECT_FIELDS, MapperFeature.AUTO_DETECT_GETTERS, MapperFeature.AUTO_DETECT_IS_GETTERS)
      .build()
  }

  private fun addMixIns(mapper: ObjectMapper) {
    mapper.addMixIn(CodeforcesCourse::class.java, CodeforcesCourseYamlMixin::class.java)
    mapper.addMixIn(CourseraCourse::class.java, CourseraCourseYamlMixin::class.java)
    mapper.addMixIn(CheckiOCourse::class.java, RemoteCourseYamlMixin::class.java)
    mapper.addMixIn(HyperskillCourse::class.java, RemoteCourseYamlMixin::class.java)
    mapper.addMixIn(StepikCourse::class.java, RemoteCourseYamlMixin::class.java)
    mapper.addMixIn(Course::class.java, CourseYamlMixin::class.java)
    mapper.addMixIn(Section::class.java, SectionYamlMixin::class.java)
    mapper.addMixIn(StepikLesson::class.java, StepikLessonYamlMixin::class.java)
    mapper.addMixIn(Lesson::class.java, LessonYamlMixin::class.java)
    mapper.addMixIn(FrameworkLesson::class.java, FrameworkLessonYamlMixin::class.java)
    mapper.addMixIn(Task::class.java, TaskYamlMixin::class.java)
    mapper.addMixIn(ChoiceTask::class.java, ChoiceTaskYamlMixin::class.java)
    mapper.addMixIn(CodeTask::class.java, CodeTaskYamlMixin::class.java)
    mapper.addMixIn(ChoiceOption::class.java, ChoiceOptionYamlMixin::class.java)
    mapper.addMixIn(TaskFile::class.java, TaskFileYamlMixin::class.java)
    mapper.addMixIn(AnswerPlaceholder::class.java, AnswerPlaceholderYamlMixin::class.java)
    mapper.addMixIn(AnswerPlaceholderDependency::class.java, AnswerPlaceholderDependencyYamlMixin::class.java)

    mapper.registerSubtypes(NamedType(CodeforcesCourse::class.java, CodeforcesNames.CODEFORCES_TYPE_YAML))
    mapper.registerSubtypes(NamedType(CourseraCourse::class.java, CourseraNames.COURSE_TYPE_YAML))
    mapper.registerSubtypes(NamedType(CheckiOCourse::class.java, CheckiONames.CHECKIO_TYPE_YAML))
    mapper.registerSubtypes(NamedType(HyperskillCourse::class.java, HYPERSKILL_TYPE_YAML))
    mapper.registerSubtypes(NamedType(StepikCourse::class.java, StepikNames.STEPIK_TYPE_YAML))
  }

  private fun addRemoteMixIns(mapper: ObjectMapper) {
    mapper.addMixIn(EduCourse::class.java, EduCourseRemoteInfoYamlMixin::class.java)
    mapper.addMixIn(CodeforcesCourse::class.java, CodeforcesCourseRemoteInfoYamlMixin::class.java)
    mapper.addMixIn(Lesson::class.java, RemoteStudyItemYamlMixin::class.java)
    mapper.addMixIn(StepikLesson::class.java, StepikLessonRemoteYamlMixin::class.java)
    mapper.addMixIn(Section::class.java, RemoteStudyItemYamlMixin::class.java)
    mapper.addMixIn(Task::class.java, RemoteStudyItemYamlMixin::class.java)
    mapper.addMixIn(DataTask::class.java, RemoteDataTaskYamlMixin::class.java)
    mapper.addMixIn(DataTaskAttempt::class.java, DataTaskAttemptYamlMixin::class.java)
    mapper.addHyperskillMixins()
  }

  private fun ObjectMapper.addHyperskillMixins() {
    addMixIn(HyperskillCourse::class.java, HyperskillCourseMixin::class.java)
    addMixIn(HyperskillProject::class.java, HyperskillProjectMixin::class.java)
    addMixIn(HyperskillStage::class.java, HyperskillStageMixin::class.java)
    addMixIn(HyperskillTopic::class.java, HyperskillTopicMixin::class.java)
  }

  private fun ObjectMapper.addStudentMixIns() {
    addMixIn(Course::class.java, StudentCourseYamlMixin::class.java)

    addMixIn(CheckiOStation::class.java, CheckiOStationYamlMixin::class.java)
    addMixIn(FrameworkLesson::class.java, StudentFrameworkLessonYamlMixin::class.java)

    addMixIn(Task::class.java, StudentTaskYamlMixin::class.java)
    addMixIn(RemoteEduTask::class.java, RemoteEduTaskYamlMixin::class.java)
    addMixIn(TheoryTask::class.java, TheoryTaskYamlUtil::class.java)
    addMixIn(ChoiceTask::class.java, StudentChoiceTaskYamlMixin::class.java)
    addMixIn(CheckiOMission::class.java, CheckiOMissionYamlMixin::class.java)
    addMixIn(CodeforcesTask::class.java, CodeforcesTaskYamlMixin::class.java)
    addMixIn(CodeforcesTaskWithFileIO::class.java, CodeforcesTaskWithFileIOYamlMixin::class.java)
    addMixIn(SortingTask::class.java, SortingTaskYamlMixin::class.java)
    addMixIn(MatchingTask::class.java, MatchingTaskYamlMixin::class.java)
    addMixIn(AnswerPlaceholder.MyInitialState::class.java, InitialStateMixin::class.java)
    addMixIn(CheckFeedback::class.java, FeedbackYamlMixin::class.java)
  }

  fun saveAll(project: Project) {
    @NonNls
    val errorMessageToLog = "Attempt to create config files for project without course"
    val course = StudyTaskManager.getInstance(project).course ?: error(errorMessageToLog)
    val mapper = course.mapper
    saveItem(course, mapper)
    course.visitSections { section -> saveItem(section, mapper) }
    course.visitLessons { lesson ->
      lesson.visitTasks { task ->
        saveItem(task, mapper)
      }
      saveItem(lesson, mapper)
    }

    saveRemoteInfo(course)
  }

  fun saveItem(item: StudyItem, mapper: ObjectMapper = item.course.mapper, configName: String = item.configFileName) {
    val course = item.course

    @NonNls
    val errorMessageToLog = "Failed to find project for course"
    val project = course.project ?: error(errorMessageToLog)
    if (!YamlFormatSettings.shouldCreateConfigFiles(project)) {
      return
    }
    item.saveConfig(project, configName, mapper)
  }

  fun saveRemoteInfo(item: StudyItem) {
    when (item) {
      is ItemContainer -> {
        saveItemRemoteInfo(item)
        item.items.forEach { saveRemoteInfo(it) }
      }
      is Task -> {
        saveItemRemoteInfo(item)
      }
    }
  }

  fun saveItemWithRemoteInfo(item: StudyItem) {
    saveItem(item)
    saveRemoteInfo(item)
  }

  private fun saveItemRemoteInfo(item: StudyItem) {
    // we don't want to create remote info files in local courses
    if (item.id > 0 || item is HyperskillCourse) {
      saveItem(item, REMOTE_MAPPER, item.remoteConfigFileName)
    }
  }

  fun startSynchronization(project: Project) {
    if (isUnitTestMode) {
      return
    }

    val disposable = StudyTaskManager.getInstance(project)
    EditorFactory.getInstance().eventMulticaster.addDocumentListener(YamlSynchronizationListener(project), disposable)
    project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (isLocalConfigFile(file)) {
          if (project.isStudentProject()) {
            @NonNls
            val errorMessageToLog = "Can't find editor for a file: ${file.name}"
            val editor = file.getEditor(project) ?: error(errorMessageToLog)
            showNoEditingNotification(editor)
            return
          }

          // load item to show editor notification if config file is invalid
          YamlLoader.loadItem(project, file, false)
        }
      }
    })
  }

  private fun showNoEditingNotification(editor: Editor) {
    val label = JLabel(EduCoreBundle.message("yaml.editor.notification.configuration.file"))
    label.border = JBUI.Borders.empty(5, 10, 5, 0)

    val panel = JPanel(BorderLayout())
    panel.add(label, BorderLayout.CENTER)
    panel.background = MessageType.WARNING.popupBackground

    editor.headerComponent = panel
  }

  private fun StudyItem.saveConfig(project: Project, configName: String, mapper: ObjectMapper) {
    val dir = getConfigDir(project)

    invokeLater {
      runWriteAction {
        val file = dir.findOrCreateChildData(javaClass, configName)
        try {
          file.putUserData(LOAD_FROM_CONFIG, false)
          if (FileTypeManager.getInstance().getFileTypeByFile(file) == UnknownFileType.INSTANCE) {
            @NonNls
            val errorMessageToLog = "Failed to get extension for file ${file.name}"
            FileTypeManager.getInstance().associateExtension(
              PlainTextFileType.INSTANCE,
              file.extension ?: error(errorMessageToLog)
            )
          }
          val yamlText = mapper.writeValueAsString(this)
          val formattedYamlText = reformatYaml(project, file.name, yamlText)

          VfsUtil.saveText(file, formattedYamlText)
          // make sure that there is no conflict between disk contents and ide in-memory document contents
          FileDocumentManager.getInstance().reloadFiles(file)
        }
        finally {
          file.putUserData(LOAD_FROM_CONFIG, true)
        }
      }
    }
  }

  private fun reformatYaml(project: Project, fileName: String, text: String): String {
    // We are able to reformat YAML only if the IDE supports the YAML language
    val yamlFileType = FileTypeManager.getInstance().findFileTypeByName("YAML") ?: return text

    val psiFile = PsiFileFactory.getInstance(project).createFileFromText(fileName, yamlFileType, text)
    CodeStyleManager.getInstance(project).reformat(psiFile)

    return psiFile.text ?: text
  }

  fun isConfigFile(file: VirtualFile): Boolean {
    return isLocalConfigFile(file) || isRemoteConfigFile(file)
  }

  fun isRemoteConfigFile(file: VirtualFile): Boolean {
    val name = file.name
    return REMOTE_COURSE_CONFIG == name || REMOTE_SECTION_CONFIG == name || REMOTE_LESSON_CONFIG == name || REMOTE_TASK_CONFIG == name
  }

  fun isLocalConfigFile(file: VirtualFile): Boolean {
    val name = file.name
    return COURSE_CONFIG == name || SECTION_CONFIG == name || LESSON_CONFIG == name || TASK_CONFIG == name
  }

  val Course.mapper: ObjectMapper
    get() = if (isStudy) {
      if (isMarketplace) STUDENT_MAPPER_WITH_ENCRYPTION else STUDENT_MAPPER
    }
    else {
      MAPPER
    }
}

val StudyItem.configFileName: String
  get() = when (this) {
    is Course -> COURSE_CONFIG
    is Section -> SECTION_CONFIG
    is Lesson -> LESSON_CONFIG
    is Task -> TASK_CONFIG
    else -> {
      @NonNls
      val errorMessageToLog = "Unknown StudyItem type: ${javaClass.simpleName}"
      error(errorMessageToLog)
    }
  }

val StudyItem.remoteConfigFileName: String
  get() = when (this) {
    is Course -> REMOTE_COURSE_CONFIG
    is Section -> REMOTE_SECTION_CONFIG
    is Lesson -> REMOTE_LESSON_CONFIG
    is Task -> REMOTE_TASK_CONFIG
    else -> {
      @NonNls
      val errorMessageToLog = "Unknown StudyItem type: ${javaClass.simpleName}"
      error(errorMessageToLog)
    }
  }

fun StudyItem.getConfigDir(project: Project): VirtualFile {
  return if (this is Task && lesson is FrameworkLesson) {
    @NonNls
    val errorMessageToLog = "Config for '$name' task dir in framework lesson not found"
    lesson.getDir(project.courseDir)?.findChild(name) ?: error(errorMessageToLog)
  }
  else {
    getDir(project.courseDir) ?: error("Config for '$this' not found")
  }
}
