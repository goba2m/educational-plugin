package com.jetbrains.edu.coursecreator.actions

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.PrettyPrinter
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.BeanSerializerFactory
import com.google.common.annotations.VisibleForTesting
import com.intellij.externalDependencies.DependencyOnPlugin
import com.intellij.externalDependencies.ExternalDependenciesManager
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.io.ZipUtil
import com.jetbrains.edu.coursecreator.AdditionalFilesUtils
import com.jetbrains.edu.coursecreator.CCUtils.generateArchiveFolder
import com.jetbrains.edu.coursecreator.CCUtils.saveOpenedDocuments
import com.jetbrains.edu.learning.EduNames
import com.jetbrains.edu.learning.EduNames.COURSE_META_FILE
import com.jetbrains.edu.learning.StudyTaskManager
import com.jetbrains.edu.learning.courseDir
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholderDependency
import com.jetbrains.edu.learning.courseFormat.Course
import com.jetbrains.edu.learning.courseFormat.CourseMode
import com.jetbrains.edu.learning.courseFormat.DescriptionFormat
import com.jetbrains.edu.learning.courseFormat.EduCourse
import com.jetbrains.edu.learning.courseFormat.EduFile
import com.jetbrains.edu.learning.courseFormat.PluginInfo
import com.jetbrains.edu.learning.courseFormat.TaskFile
import com.jetbrains.edu.learning.courseFormat.copy
import com.jetbrains.edu.learning.courseFormat.ext.compatibilityProvider
import com.jetbrains.edu.learning.courseFormat.ext.findTaskFileInDir
import com.jetbrains.edu.learning.courseFormat.ext.getDescriptionFile
import com.jetbrains.edu.learning.courseFormat.ext.getDir
import com.jetbrains.edu.learning.courseFormat.ext.updateEnvironmentSettings
import com.jetbrains.edu.learning.courseFormat.tasks.Task
import com.jetbrains.edu.learning.coursera.CourseraCourse
import com.jetbrains.edu.learning.exceptions.BrokenPlaceholderException
import com.jetbrains.edu.learning.exceptions.HugeBinaryFileException
import com.jetbrains.edu.learning.isUnitTestMode
import com.jetbrains.edu.learning.json.addStudyItemMixins
import com.jetbrains.edu.learning.json.encrypt.EncryptionModule
import com.jetbrains.edu.learning.json.encrypt.getAesKey
import com.jetbrains.edu.learning.json.mixins.AnswerPlaceholderDependencyMixin
import com.jetbrains.edu.learning.json.mixins.AnswerPlaceholderMixin
import com.jetbrains.edu.learning.json.mixins.AnswerPlaceholderWithAnswerMixin
import com.jetbrains.edu.learning.json.mixins.EduFileMixin
import com.jetbrains.edu.learning.json.mixins.JsonMixinNames
import com.jetbrains.edu.learning.json.mixins.PluginInfoMixin
import com.jetbrains.edu.learning.json.mixins.TaskFileMixin
import com.jetbrains.edu.learning.json.setDateFormat
import com.jetbrains.edu.learning.marketplace.updateCourseItems
import com.jetbrains.edu.learning.messages.EduCoreBundle
import com.jetbrains.edu.learning.pluginVersion
import com.jetbrains.edu.learning.toStudentFile
import com.jetbrains.edu.learning.yaml.YamlFormatSettings.TASK_CONFIG
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.io.File
import java.io.IOException

class CourseArchiveCreator(
  private val project: Project,
  @NonNls private val location: String,
  private val aesKey: String = getAesKey()
) {

  @Nls(capitalization = Nls.Capitalization.Sentence)
  fun doCreateArchive(indicator: ProgressIndicator): String? {
    val course = StudyTaskManager.getInstance(project).course
    if (course != null && course.isMarketplace && !isUnitTestMode) {
      course.updateCourseItems()
    }
    course?.updateEnvironmentSettings(project)
    val courseCopy = course?.copy() ?: return EduCoreBundle.message("error.unable.to.obtain.course.for.project")
    val jsonFolder = runWriteActionAndWait { generateArchiveFolder(project) }
                     ?: return EduCoreBundle.message("error.failed.to.generate.course.archive")

    val error = AdditionalFilesUtils.checkIgnoredFiles(project)
    if (error != null) {
      if (!isUnitTestMode) {
        LOG.error("Failed to create course archive: $error")
      }
      return error
    }

    try {
      prepareCourse(courseCopy)
    }
    catch (e: BrokenPlaceholderException) {
      if (!isUnitTestMode) {
        LOG.error("Failed to create course archive: ${e.message}")
      }
      val yamlFile = e.placeholder.taskFile.task.getDir(project.courseDir)?.findChild(TASK_CONFIG) ?: return e.message
      FileEditorManager.getInstance(project).openFile(yamlFile, true)
      return "${e.message}\n\n${e.placeholderInfo}"
    }
    catch (e: HugeBinaryFileException) {
      return e.message
    }

    val filesCount = countFiles(courseCopy)
    indicator.text2 = "processing $filesCount file(s)"
    LOG.info("Creating archive with $filesCount files and thread ${Thread.currentThread()}")

    return try {
      val a1 = System.currentTimeMillis()
      val json = generateJson(jsonFolder, courseCopy)
      val a2 = System.currentTimeMillis()
      LOG.info("OOO starting refresh ${a2 - a1}")
      runWriteActionAndWait {
        VirtualFileManager.getInstance().refreshWithoutFileWatcher(false)
      }
      val a22 = System.currentTimeMillis()
      LOG.info("OOO starting zip ${a22 - a2}")
      val a3 = System.currentTimeMillis()
      ZipUtil.compressFile(json, File(location))
      val a4 = System.currentTimeMillis()
      LOG.info("OOO zip zip ${a4 - a3}")
      synchronize(project)
      val a5 = System.currentTimeMillis()
      LOG.info("OOO synced ${a5 - a4}")
      null
    }
    catch (e: IOException) {
      LOG.error("Failed to create course archive", e)
      EduCoreBundle.message("error.failed.to.write")
    }
  }

  /**
   * @return null when course archive was created successfully, non-empty error message otherwise
   */
  fun createArchive(): String? {
    saveOpenedDocuments(project)

    var resultString: String? = null
    ProgressManager.getInstance().runProcessWithProgressSynchronously({
      resultString = doCreateArchive(ProgressManager.getInstance().progressIndicator)
    }, "creating course archive", true, project)

    return resultString
  }

  private fun countFiles(courseCopy: Course): Int {
    var filesCount = 0
    courseCopy.visitTasks { filesCount += it.taskFiles.size }
    filesCount += courseCopy.additionalFiles.size
    return filesCount
  }

  @VisibleForTesting
  fun prepareCourse(course: Course) {
    loadActualTexts(project, course)
    course.sortItems()
    course.additionalFiles = AdditionalFilesUtils.collectAdditionalFiles(course, project)
    course.pluginDependencies = collectCourseDependencies(project, course)
    course.courseMode = CourseMode.STUDENT
  }

  private fun synchronize(project: Project) {
    VirtualFileManager.getInstance().refreshWithoutFileWatcher(true)
    ProjectView.getInstance(project).refresh()
  }

  private fun generateJson(parentDir: VirtualFile, course: Course): File {
    val mapper = getMapper(course)

    val jsonFile = File(File(parentDir.path), COURSE_META_FILE)
    mapper.writer(printer).writeValue(jsonFile, course)
    return jsonFile
  }

  @VisibleForTesting
  fun getMapper(course: Course): ObjectMapper {
    val module = SimpleModule()
      .addSerializer(EduCourse::class.java, EduCoursePluginVersionSerializer())

    val mapper = JsonMapper.builder()
      .addModule(module)
      .addModule(EncryptionModule(aesKey))
      .commonMapperSetup(course)
      .setDateFormat()
      .build()
    mapper.addStudyItemMixins()
    return mapper
  }

  class EduCoursePluginVersionSerializer : JsonSerializer<EduCourse>() {
    override fun serialize(value: EduCourse, jgen: JsonGenerator, provider: SerializerProvider) {
      jgen.writeStartObject()
      val javaType = provider.constructType(value::class.java)
      val beanDesc: BeanDescription = provider.config.introspect(javaType)
      val serializer: JsonSerializer<Any> =
        BeanSerializerFactory.instance.findBeanOrAddOnSerializer(provider, javaType, beanDesc,
                                                                 provider.isEnabled(MapperFeature.USE_STATIC_TYPING))
      serializer.unwrappingSerializer(null).serialize(value, jgen, provider)
      jgen.addPluginVersion()
      jgen.writeEndObject()
    }

    private fun JsonGenerator.addPluginVersion() {
      val fieldName = JsonMixinNames.PLUGIN_VERSION
      val pluginVersion = if (isUnitTestMode) TEST_PLUGIN_VERSION else pluginVersion(EduNames.PLUGIN_ID) ?: "unknown"
      writeObjectField(fieldName, pluginVersion)
    }
  }

  companion object {
    private val LOG = Logger.getInstance(CourseArchiveCreator::class.java.name)
    private const val TEST_PLUGIN_VERSION = "yyyy.2-yyyy.1-TEST"

    private val printer: PrettyPrinter
      get() {
        val prettyPrinter = DefaultPrettyPrinter()
        prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE)
        return prettyPrinter
      }

    fun JsonMapper.Builder.commonMapperSetup(course: Course): JsonMapper.Builder {
      if (course is CourseraCourse) {
        addMixIn(AnswerPlaceholder::class.java, AnswerPlaceholderMixin::class.java)
      }
      else {
        addMixIn(AnswerPlaceholder::class.java, AnswerPlaceholderWithAnswerMixin::class.java)
      }
      addMixIn(PluginInfo::class.java, PluginInfoMixin::class.java)
      addMixIn(EduFile::class.java, EduFileMixin::class.java)
      addMixIn(TaskFile::class.java, TaskFileMixin::class.java)
      addMixIn(AnswerPlaceholderDependency::class.java, AnswerPlaceholderDependencyMixin::class.java)
      disable(MapperFeature.AUTO_DETECT_FIELDS)
      disable(MapperFeature.AUTO_DETECT_GETTERS)
      disable(MapperFeature.AUTO_DETECT_IS_GETTERS)
      return this
    }

    @VisibleForTesting
    fun loadActualTexts(project: Project, course: Course) {
      course.visitLessons { lesson ->
        val lessonDir = lesson.getDir(project.courseDir)
        if (lessonDir == null) return@visitLessons
        for (task in lesson.taskList) {
          loadActualTexts(project, task)
        }
      }
    }

    private fun loadActualTexts(project: Project, task: Task) {
      val taskDir = task.getDir(project.courseDir) ?: return
      convertToStudentTaskFiles(project, task, taskDir)
      addDescriptions(project, task)
    }

    private fun convertToStudentTaskFiles(project: Project, task: Task, taskDir: VirtualFile) {
      val studentTaskFiles = LinkedHashMap<String, TaskFile>()
      for ((key, value) in task.taskFiles) {
        val answerFile = value.findTaskFileInDir(taskDir) ?: continue
        val studentFile = answerFile.toStudentFile(project, task)
        if (studentFile != null) {
          studentTaskFiles[key] = studentFile
        }
      }
      task.taskFiles = studentTaskFiles
    }

    fun addDescriptions(project: Project, task: Task) {
      val descriptionFile = task.getDescriptionFile(project)

      if (descriptionFile != null) {
        try {
          task.descriptionText = VfsUtilCore.loadText(descriptionFile)
          val extension = descriptionFile.extension
          val descriptionFormat = DescriptionFormat.values().firstOrNull { format -> format.fileExtension == extension }
          if (descriptionFormat != null) {
            task.descriptionFormat = descriptionFormat
          }
        }
        catch (e: IOException) {
          LOG.warn("Failed to load text " + descriptionFile.name)
        }

      }
      else {
        LOG.warn("Can't find description file for task ${task.name}")
      }
    }

    private fun collectCourseDependencies(project: Project, course: Course): List<PluginInfo> {
      val requiredPluginIds = course.compatibilityProvider?.requiredPlugins().orEmpty().mapTo(HashSet()) { it.stringId }
      return ExternalDependenciesManager.getInstance(project).getDependencies(DependencyOnPlugin::class.java)
        // Compatibility provider may produce different required plugins in different IDEs,
        // for example, `PythonCore` in IDEA Community and `Pythonid` in PyCharm Pro.
        // So exclude them from the plugin archive to keep course compatible with all desired IDEs.
        // Note, it doesn't allow user to start course without required plugins because
        // we still check required plugins from compatibility providers on course creation.
        // See https://youtrack.jetbrains.com/issue/EDU-4514
        .filter { it.pluginId !in requiredPluginIds }
        .map {
          PluginInfo(it.pluginId,
                     PluginManager.getInstance().findEnabledPlugin(PluginId.getId(it.pluginId))?.name ?: it.pluginId,
                     it.minVersion,
                     it.maxVersion)
        }
    }
  }
}
