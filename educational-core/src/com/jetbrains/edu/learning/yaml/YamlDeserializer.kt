package com.jetbrains.edu.learning.yaml

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.util.messages.Topic
import com.jetbrains.edu.learning.codeforces.CodeforcesNames
import com.jetbrains.edu.learning.codeforces.courseFormat.CodeforcesCourse
import com.jetbrains.edu.learning.courseDir
import com.jetbrains.edu.learning.courseFormat.*
import com.jetbrains.edu.learning.courseFormat.ext.getDir
import com.jetbrains.edu.learning.courseFormat.tasks.data.DataTask
import com.jetbrains.edu.learning.courseFormat.tasks.data.DataTask.Companion.DATA_TASK_TYPE
import com.jetbrains.edu.learning.document
import com.jetbrains.edu.learning.getEditor
import com.jetbrains.edu.learning.isUnitTestMode
import com.jetbrains.edu.learning.messages.EduCoreBundle
import com.jetbrains.edu.learning.stepik.hyperskill.courseFormat.HyperskillCourse
import com.jetbrains.edu.learning.yaml.YamlConfigSettings.LESSON_CONFIG
import com.jetbrains.edu.learning.yaml.YamlConfigSettings.REMOTE_COURSE_CONFIG
import com.jetbrains.edu.learning.yaml.YamlConfigSettings.REMOTE_LESSON_CONFIG
import com.jetbrains.edu.learning.yaml.YamlConfigSettings.REMOTE_SECTION_CONFIG
import com.jetbrains.edu.learning.yaml.YamlConfigSettings.REMOTE_TASK_CONFIG
import com.jetbrains.edu.learning.yaml.YamlConfigSettings.SECTION_CONFIG
import com.jetbrains.edu.learning.yaml.YamlConfigSettings.TASK_CONFIG
import com.jetbrains.edu.learning.yaml.YamlFormatSynchronizer.MAPPER
import com.jetbrains.edu.learning.yaml.YamlFormatSynchronizer.REMOTE_MAPPER
import com.jetbrains.edu.learning.yaml.errorHandling.*
import com.jetbrains.edu.learning.yaml.format.RemoteStudyItem
import com.jetbrains.edu.learning.yaml.format.YamlMixinNames
import org.jetbrains.annotations.NonNls

/**
 * Deserialize [StudyItem] object from yaml config file without any additional modifications.
 * It means that deserialized object contains only values from corresponding config files which
 * should be applied to existing one that is done in [YamlLoader.loadItem].
 */
object YamlDeserializer {
  @NonNls
  private const val TOPIC = "Loaded YAML"
  val YAML_LOAD_TOPIC: Topic<YamlListener> = Topic.create(TOPIC, YamlListener::class.java)

  fun deserializeItemProcessingErrors(
    configFile: VirtualFile,
    project: Project,
    loadFromVFile: Boolean = true,
    mapper: ObjectMapper = MAPPER
  ): StudyItem? {
    val configFileText = if (loadFromVFile) VfsUtil.loadText(configFile) else configFile.document.text
    val configName = configFile.name
    return ProgressManager.getInstance().computeInNonCancelableSection<StudyItem, Exception> {
      try {
        YamlDeserializerBase.deserializeItem(configName, mapper, configFileText)
      }
      catch (e: Exception) {
        processErrors(project, configFile, e)
        null
      }
    }
  }

  inline fun <reified T : StudyItem> StudyItem.deserializeContent(
    project: Project,
    contentList: List<T>,
    mapper: ObjectMapper = MAPPER,
  ): List<T> {
    val content = mutableListOf<T>()
    for (titledItem in contentList) {
      val configFile: VirtualFile = getConfigFileForChild(project, titledItem.name) ?: continue
      val deserializeItem = deserializeItemProcessingErrors(configFile, project, mapper = mapper) as? T ?: continue
      deserializeItem.name = titledItem.name
      deserializeItem.index = titledItem.index
      content.add(deserializeItem)
    }

    return content
  }

  fun deserializeRemoteItem(configFile: VirtualFile): StudyItem {
    val configName = configFile.name
    val configFileText = VfsUtil.loadText(configFile)
    return when (configName) {
      REMOTE_COURSE_CONFIG -> deserializeCourseRemoteInfo(configFileText)
      REMOTE_LESSON_CONFIG -> deserializeLessonRemoteInfo(configFileText)
      REMOTE_SECTION_CONFIG -> REMOTE_MAPPER.readValue(configFileText, RemoteStudyItem::class.java)
      REMOTE_TASK_CONFIG -> deserializeTaskRemoteInfo(configFileText)
      else -> loadingError(unknownConfigMessage(configName))
    }
  }

  private fun deserializeCourseRemoteInfo(configFileText: String): Course {
    val treeNode = REMOTE_MAPPER.readTree(configFileText)
    val type = asText(treeNode.get(YamlMixinNames.TYPE))

    val clazz = when {
      type == CodeforcesNames.CODEFORCES_COURSE_TYPE -> CodeforcesCourse::class.java
      treeNode.get(YamlMixinNames.HYPERSKILL_PROJECT) != null -> HyperskillCourse::class.java
      else -> EduCourse::class.java
    }

    return REMOTE_MAPPER.treeToValue(treeNode, clazz)
  }

  private fun deserializeLessonRemoteInfo(configFileText: String): StudyItem {
    val treeNode = REMOTE_MAPPER.readTree(configFileText)
    return REMOTE_MAPPER.treeToValue(treeNode, RemoteStudyItem::class.java)
  }

  private fun deserializeTaskRemoteInfo(configFileText: String): StudyItem {
    val treeNode = REMOTE_MAPPER.readTree(configFileText)

    val clazz = when (asText(treeNode.get(YamlMixinNames.TYPE))) {
      DATA_TASK_TYPE -> DataTask::class.java
      else -> RemoteStudyItem::class.java
    }

    return REMOTE_MAPPER.treeToValue(treeNode, clazz)
  }

  private fun asText(node: JsonNode?): String? {
    return if (node == null || node.isNull) null else node.asText()
  }

  private val StudyItem.childrenConfigFileNames: Array<String>
    get() = when (this) {
      is Course -> arrayOf(SECTION_CONFIG, LESSON_CONFIG)
      is Section -> arrayOf(LESSON_CONFIG)
      is Lesson -> arrayOf(TASK_CONFIG)
      else -> error("Unexpected StudyItem: ${javaClass.simpleName}")
    }

  fun StudyItem.getConfigFileForChild(project: Project, childName: String): VirtualFile? {
    val dir = getDir(project.courseDir) ?: error(noDirForItemMessage(name))
    val itemDir = dir.findChild(childName)
    val configFile = childrenConfigFileNames.map { itemDir?.findChild(it) }.firstOrNull { it != null }
    if (configFile != null) {
      return configFile
    }

    val message = if (itemDir == null) {
      EduCoreBundle.message("yaml.editor.notification.directory.not.found", childName)
    }
    else {
      EduCoreBundle.message("yaml.editor.notification.config.file.not.found", childName)
    }

    @NonNls
    val errorMessageToLog = "Config file for currently loading item ${name} not found"
    val parentConfig = dir.findChild(configFileName) ?: error(errorMessageToLog)
    showError(project, null, parentConfig, message)

    return null
  }

  private fun processErrors(project: Project, configFile: VirtualFile, e: Exception) {
    @Suppress("DEPRECATION")
    // suppress deprecation for MarkedYAMLException as it is actually thrown from com.fasterxml.jackson.dataformat.yaml.YAMLParser.nextToken
    when (e) {
      is MissingKotlinParameterException -> {
        val parameterName = e.parameter.name
        if (parameterName == null) {
          showError(project, e, configFile)
        }
        else {
          val cause = EduCoreBundle.message("yaml.editor.notification.parameter.is.empty",
                                            NameUtil.nameToWordsLowerCase(parameterName).joinToString("_"))
          showError(project, e, configFile, cause)
        }
      }
      is InvalidYamlFormatException -> showError(project, e, configFile, e.message)
      is MismatchedInputException -> {
        showError(project, e, configFile)
      }
      is com.fasterxml.jackson.dataformat.yaml.snakeyaml.error.MarkedYAMLException -> {
        val message = yamlParsingErrorNotificationMessage(e.problem, e.contextMark?.line)
        if (message != null) {
          showError(project, e, configFile, message)
        }
        else {
          showError(project, e, configFile)
        }
      }
      is JsonMappingException -> {
        val causeException = e.cause
        if (causeException?.message == null || causeException !is InvalidYamlFormatException) {
          showError(project, e, configFile)
        }
        else {
          showError(project, causeException, configFile, causeException.message)
        }
      }
      else -> throw e
    }
  }

  // it doesn't require localization as `problems` is snakeyaml error message on which we have no influence
  @Suppress("UnstableApiUsage")
  @NlsSafe
  private fun yamlParsingErrorNotificationMessage(problem: String?, line: Int?) =
    if (problem != null && line != null) "$problem at line ${line + 1}" else null

  fun showError(
    project: Project,
    originalException: Exception?,
    configFile: VirtualFile,
    cause: String = EduCoreBundle.message("yaml.editor.notification.invalid.config"),
  ) {
    // to make test failures more comprehensible
    if (isUnitTestMode && project.getUserData(YamlFormatSettings.YAML_TEST_THROW_EXCEPTION) == true) {
      if (originalException != null) {
        throw ProcessedException(cause, originalException)
      }
    }
    runInEdt {
      val editor = configFile.getEditor(project)
      project.messageBus.syncPublisher(YAML_LOAD_TOPIC).yamlFailedToLoad(configFile, cause)
      if (editor == null) {
        val notification = InvalidConfigNotification(project, configFile, cause)
        notification.notify(project)
      }
    }
  }

  @VisibleForTesting
  class ProcessedException(message: String, originalException: Exception?) : Exception(message, originalException)

}
