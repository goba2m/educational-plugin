package com.jetbrains.edu.learning.yaml

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.edu.learning.*
import com.jetbrains.edu.learning.courseFormat.*
import com.jetbrains.edu.learning.courseFormat.ext.getDir
import com.jetbrains.edu.learning.courseFormat.tasks.Task
import com.jetbrains.edu.learning.messages.EduCoreBundle
import com.jetbrains.edu.learning.yaml.YamlDeserializer.deserializeContent
import com.jetbrains.edu.learning.yaml.YamlFormatSynchronizer.MAPPER
import com.jetbrains.edu.learning.yaml.YamlFormatSynchronizer.mapper
import com.jetbrains.edu.learning.yaml.YamlFormatSynchronizer.saveItem
import com.jetbrains.edu.learning.yaml.YamlLoader.loadItem
import com.jetbrains.edu.learning.yaml.errorHandling.YamlLoadingException
import com.jetbrains.edu.learning.yaml.errorHandling.loadingError
import com.jetbrains.edu.learning.yaml.errorHandling.noDirForItemMessage
import com.jetbrains.edu.learning.yaml.errorHandling.unknownConfigMessage
import com.jetbrains.edu.learning.yaml.format.YamlMixinNames.TASK
import com.jetbrains.edu.learning.yaml.format.getChangeApplierForItem

/**
 *  Get fully-initialized [StudyItem] object from yaml config file.
 *  Uses [YamlDeserializer.deserializeItem] to deserialize object, than applies changes to existing object, see [loadItem].
 */
object YamlLoader {

  fun loadItem(project: Project, configFile: VirtualFile, loadFromVFile: Boolean) {
    project.messageBus.syncPublisher(YamlDeserializer.YAML_LOAD_TOPIC).beforeYamlLoad(configFile)
    try {
      doLoad(project, configFile, loadFromVFile)
    }
    catch (e: Exception) {
      when (e) {
        is YamlLoadingException -> YamlDeserializer.showError(project, e, configFile, e.message)
        else -> throw e
      }
    }
  }

  @VisibleForTesting
  fun doLoad(project: Project, configFile: VirtualFile, loadFromVFile: Boolean) {
    // for null course we load course again so no need to pass mode specific mapper here
    val mapper = StudyTaskManager.getInstance(project).course?.mapper ?: MAPPER

    val existingItem = getStudyItemForConfig(project, configFile)
    val deserializedItem = YamlDeserializer.deserializeItem(configFile, project, loadFromVFile, mapper) ?: return
    deserializedItem.ensureChildrenExist(configFile.parent)

    if (existingItem == null) {
      // this code is called if item wasn't loaded because of broken config
      // and now if config fixed, we'll add item to a parent
      if (deserializedItem is Course) {
        StudyTaskManager.getInstance(project).course = YamlDeepLoader.loadCourse(project)
        return
      }

      val itemDir = configFile.parent
      deserializedItem.name = itemDir.name
      val parentItem = deserializedItem.getParentItem(project, itemDir.parent)
      val parentConfig = parentItem.getDir(project.courseDir)?.findChild(parentItem.configFileName) ?: return
      val deserializedParent = YamlDeserializer.deserializeItem(parentConfig, project, mapper=mapper) as? ItemContainer ?: return
      if (deserializedParent.items.map { it.name }.contains(itemDir.name)) {
        parentItem.addItemAsNew(project, deserializedItem)
        reopenEditors(project)
        // new item is added at the end, so we should save parent item to update items order in config file
        saveItem(parentItem)
      }
      return
    }

    existingItem.applyChanges(project, deserializedItem)
  }

  /**
   * For items that are added as new we have to reopen editors, because `EduSplitEditor` wasn't created
   * for files that aren't task files.
   */
  private fun reopenEditors(project: Project) {
    val selectedEditor = FileEditorManager.getInstance(project).selectedEditor
    val files = FileEditorManager.getInstance(project).openFiles
      .filter { it.getTaskFile(project) != null }
    for (virtualFile in files) {
      FileEditorManager.getInstance(project).closeFile(virtualFile)
      FileEditorManager.getInstance(project).openFile(virtualFile, false)
    }

    // restore selection
    val file = selectedEditor?.file
    if (file != null) {
      FileEditorManager.getInstance(project).openFile(file, true)
    }
  }

  fun ItemContainer.addItemAsNew(project: Project, deserializedItem: StudyItem) {
    addItem(deserializedItem)
    sortItems()
    // we need parent to be set to obtain directories for children config files
    deserializedItem.parent = this
    deserializedItem.deserializeChildrenIfNeeded(project, course)
  }

  fun StudyItem.deserializeChildrenIfNeeded(project: Project, course: Course) {
    if (this !is ItemContainer) {
      return
    }

    val mapper = course.mapper
    items = deserializeContent(project, items, mapper)
    // set parent to deserialize content correctly
    items.forEach { it.init(this, false) }
    items.filterIsInstance(ItemContainer::class.java).forEach {
      it.items = it.deserializeContent(project, it.items, mapper)
    }
  }

  private fun StudyItem.getParentItem(project: Project, parentDir: VirtualFile): ItemContainer {
    val course = StudyTaskManager.getInstance(project).course
    val itemContainer = when (this) {
      is Section -> course
      is Lesson -> {
        val section = course?.let { parentDir.getSection(project) }
        section ?: course
      }
      is Task -> course?.let { parentDir.getLesson(project) }
      else -> loadingError(
        EduCoreBundle.message("yaml.editor.invalid.unexpected.item.type", itemType))
    }
    return itemContainer ?: loadingError(EduCoreBundle.message("yaml.editor.invalid.format.parent.not.found", name))
  }

  private fun <T : StudyItem> T.applyChanges(project: Project, deserializedItem: T) {
    getChangeApplierForItem(project, deserializedItem).applyChanges(this, deserializedItem)
  }

  private fun getStudyItemForConfig(project: Project, configFile: VirtualFile): StudyItem? {
    val name = configFile.name
    val itemDir = configFile.parent ?: error(EduCoreBundle.message("yaml.editor.invalid.format.containing.item.dir.not.found", name))
    val course = StudyTaskManager.getInstance(project).course ?: return null
    return when (name) {
      YamlConfigSettings.COURSE_CONFIG -> course
      YamlConfigSettings.SECTION_CONFIG -> itemDir.getSection(project)
      YamlConfigSettings.LESSON_CONFIG -> itemDir.getLesson(project)
      YamlConfigSettings.TASK_CONFIG -> itemDir.getTask(project)
      else -> loadingError(unknownConfigMessage(name))
    }
  }
}

private fun StudyItem.ensureChildrenExist(itemDir: VirtualFile) {
  when (this) {
    is ItemContainer -> {
      items.forEach {
        val itemTypeName = if (it is Task) TASK else EduNames.ITEM
        itemDir.findChild(it.name) ?: loadingError(noDirForItemMessage(it.name, itemTypeName))
      }
    }
    is Task -> {
      taskFiles.forEach { (name, _) ->
        itemDir.findFileByRelativePath(name) ?: loadingError(EduCoreBundle.message("yaml.editor.invalid.format.no.file", name))
      }
    }
  }
}
