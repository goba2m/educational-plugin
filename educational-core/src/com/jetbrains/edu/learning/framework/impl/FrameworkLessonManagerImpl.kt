package com.jetbrains.edu.learning.framework.impl

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.storage.AbstractStorage
import com.jetbrains.edu.learning.EduUtilsKt.isStudentProject
import com.jetbrains.edu.learning.courseFormat.FrameworkLesson
import com.jetbrains.edu.learning.courseFormat.tasks.Task
import com.jetbrains.edu.learning.framework.FrameworkLessonManager
import com.jetbrains.edu.learning.isToEncodeContent
import com.jetbrains.edu.learning.loadEncodedContent
import com.jetbrains.edu.learning.messages.EduCoreBundle
import com.jetbrains.edu.learning.stepik.hyperskill.courseFormat.HyperskillCourse
import com.jetbrains.edu.learning.ui.getUIName
import com.jetbrains.edu.learning.yaml.YamlFormatSynchronizer
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Keeps list of [Change]s for each task. Change list is difference between initial task state and latest one.
 *
 * Allows navigating between tasks in framework lessons in learner mode (where only current task is visible for a learner)
 * without rewriting whole task content.
 * It can be essential in large projects like Android applications where a lot of files are the same between two consecutive tasks
 */
class FrameworkLessonManagerImpl(private val project: Project) : FrameworkLessonManager, Disposable {

  @VisibleForTesting
  var storage: FrameworkStorage = createStorage(project)

  override fun prepareNextTask(lesson: FrameworkLesson, taskDir: VirtualFile, showDialogIfConflict: Boolean) {
    applyTargetTaskChanges(lesson, 1, taskDir, showDialogIfConflict)
  }

  override fun preparePrevTask(lesson: FrameworkLesson, taskDir: VirtualFile, showDialogIfConflict: Boolean) {
    applyTargetTaskChanges(lesson, -1, taskDir, showDialogIfConflict)
  }

  override fun saveExternalChanges(task: Task, externalState: Map<String, String>) {
    require(project.isStudentProject()) {
      "`saveExternalChanges` should be called only if course in study mode"
    }
    require(task.lesson is FrameworkLesson) {
      "Only solutions of framework tasks can be saved"
    }

    val visibleFiles = task.allFiles.split(task).first
    val externalVisibleFiles = externalState.split(task).first
    val changes = calculateChanges(visibleFiles, externalVisibleFiles)
    val currentRecord = task.record
    task.record = try {
      storage.updateUserChanges(currentRecord, changes)
    }
    catch (e: IOException) {
      LOG.error("Failed to save solution for task `${task.name}`", e)
      currentRecord
    }
    YamlFormatSynchronizer.saveItem(task)
  }

  override fun updateUserChanges(task: Task, newInitialState: Map<String, String>) {
    require(project.isStudentProject()) {
      "`updateUserChanges` should be called only if course in study mode"
    }
    require(task.lesson is FrameworkLesson) {
      "Only solutions of framework tasks can be saved"
    }

    val currentRecord = task.record
    if (currentRecord == -1) return

    val changes = try {
      storage.getUserChanges(currentRecord)
    }
    catch (e: IOException) {
      LOG.error("Failed to get user changes for task `${task.name}`", e)
      return
    }

    val newChanges = changes.changes.mapNotNull {
      when (it) {
        is Change.AddFile -> if (it.path in newInitialState) Change.ChangeFile(it.path, it.text) else it
        is Change.RemoveFile -> if (it.path !in newInitialState) null else it
        is Change.ChangeFile -> if (it.path !in newInitialState) Change.AddFile(it.path, it.text) else it
        is Change.PropagateLearnerCreatedTaskFile,
        is Change.RemoveTaskFile -> it
      }
    }

    try {
      storage.updateUserChanges(currentRecord, UserChanges(newChanges))
    }
    catch (e: IOException) {
      LOG.error("Failed to update user changes for task `${task.name}`", e)
    }
  }

  override fun getChangesTimestamp(task: Task): Long {
    require(project.isStudentProject()) {
      "`getTimestamp` should be called only if course in study mode"
    }
    require(task.lesson is FrameworkLesson) {
      "Changes timestamp makes sense only for framework tasks"
    }

    return storage.getUserChanges(task.record).timestamp
  }

  /**
   * Convert the current state on local FS related to current task in framework lesson
   * to a new one, to get state of next/previous (target) task.
   */
  private fun applyTargetTaskChanges(
    lesson: FrameworkLesson,
    taskIndexDelta: Int,
    taskDir: VirtualFile,
    showDialogIfConflict: Boolean
  ) {
    require(project.isStudentProject()) {
      "`applyTargetTaskChanges` should be called only if course in study mode"
    }
    val currentTaskIndex = lesson.currentTaskIndex
    val targetTaskIndex = currentTaskIndex + taskIndexDelta

    val currentTask = lesson.taskList[currentTaskIndex]
    val targetTask = lesson.taskList[targetTaskIndex]

    lesson.currentTaskIndex = targetTaskIndex
    YamlFormatSynchronizer.saveItem(lesson)

    val currentRecord = currentTask.record
    val targetRecord = targetTask.record

    val initialCurrentFiles = currentTask.allFiles

    // 1. Get difference between initial state of current task and previous task state
    // and construct previous state of current task.
    // Previous state is needed to determine if a user made any new change
    val previousCurrentUserChanges = getUserChanges(currentTask)
    val previousCurrentState = HashMap(initialCurrentFiles).apply { previousCurrentUserChanges.apply(this) }

    // 2. Calculate difference between initial state of current task and current state on local FS.
    // Update change list for current task in [storage] to have ability to restore state of current task in future
    val (newCurrentRecord, currentUserChanges) = try {
      updateUserChanges(currentRecord, initialCurrentFiles, taskDir)
    }
    catch (e: IOException) {
      LOG.error("Failed to save user changes for task `${currentTask.name}`", e)
      UpdatedUserChanges(currentRecord, UserChanges.empty())
    }

    // 3. Update record index to a new one.
    currentTask.record = newCurrentRecord
    YamlFormatSynchronizer.saveItem(currentTask)

    // 4. Get difference (change list) between initial and latest states of target task
    val nextUserChanges = getUserChanges(targetTask)

    // 5. Apply change lists to initial state to get latest states of current and target tasks
    val currentState = HashMap(initialCurrentFiles).apply { currentUserChanges.apply(this) }
    val targetState = HashMap(targetTask.allFiles).apply { nextUserChanges.apply(this) }

    // 6. Calculate difference between latest states of current and target tasks
    // Note, there are special rules for hyperskill courses for now
    // All user changes from the current task should be propagated to next task as is

    // If a user navigated back to current task, didn't make any change and wants to navigate to next task again
    // we shouldn't try to propagate current changes to next task
    val currentTaskHasNewUserChanges = !(currentRecord != -1 && targetRecord != -1 && previousCurrentState == currentState)

    val course = lesson.course
    val isNonTemplateBased = !lesson.isTemplateBased || course is HyperskillCourse && !course.isTemplateBased

    val changes = if (currentTaskHasNewUserChanges && taskIndexDelta == 1 && isNonTemplateBased) {
      calculatePropagationChanges(targetTask, currentTask, currentState, targetState, showDialogIfConflict)
    }
    else {
      calculateChanges(currentState, targetState)
    }

    // 7. Apply difference between latest states of current and target tasks on local FS
    changes.apply(project, taskDir, targetTask)
    YamlFormatSynchronizer.saveItem(targetTask)
  }

  private fun getUserChanges(task: Task): UserChanges {
    return try {
      storage.getUserChanges(task.record)
    }
    catch (e: IOException) {
      LOG.error("Failed to get user changes for task `${task.name}`", e)
      UserChanges.empty()
    }
  }

  /**
   * Returns [Change]s to propagate user changes from [currentState] to [targetTask].
   *
   * In case, when it's impossible due to simultaneous incompatible user changes in [currentState] and [targetState],
   * it asks user to choose what change he wants to apply.
   */
  private fun calculatePropagationChanges(
    targetTask: Task,
    currentTask: Task,
    currentState: Map<String, String>,
    targetState: Map<String, String>,
    showDialogIfConflict: Boolean
  ): UserChanges {
    val (currentVisibleFilesState, currentInvisibleFilesState) = currentState.split(currentTask)
    val (targetVisibleFilesState, targetInvisibleFilesState) = targetState.split(targetTask)

    // A lesson may have files that are invisible in the previous step, but become visible in the new one.
    // We allow files to change visibility from invisible to visible.
    // Why is this necessary? Course creators often have such use-case:
    // They want to make some files invisible on some prefix of the task list, and then have them visible in the rest of the tasks
    // So that they will be shown to students and will participate in solving the problem
    // For more detailed explanation, see the documentation:
    // https://jetbrains.team/p/edu/repositories/internal-documentation/files/subsystems/Framework%20Lessons/internal-part-ru.md

    // Calculate files that change visibility
    val fromInvisibleToVisibleFilesState = targetVisibleFilesState.filter { it.key in currentInvisibleFilesState }
    val fromVisibleToInvisibleFilesState = targetInvisibleFilesState.filter { it.key in currentVisibleFilesState }

    // We assume that files could not change visibility from visible to invisible
    // This behaviour is not intended
    if (fromVisibleToInvisibleFilesState.isNotEmpty()) {
      LOG.error("Visibility change from visible to invisible during navigation in non-template-based lessons is not supported")
    }

    // Only visible files that do not change visibility can participate in propagating user changes.
    val currentVisibleFilesStateWithoutVisibilityChange = currentVisibleFilesState.filter { it.key !in targetInvisibleFilesState }
    val targetVisibleFilesStateWithoutVisibilityChange = targetVisibleFilesState.filter { it.key !in currentInvisibleFilesState }

    // Files that change visibility are processed separately:
    // (Invisible -> Visible) - Changes for them are not propagated
    // (Visible -> Invisible) - We assume that there are no such files


    // Creates [Change]s to propagates all current changes of task files to target task.
    // During propagation, we assume that in the not-template-based framework lessons all the initial files are the same for each task.
    // Therefore, we will only add user-created files and remove user-deleted files.
    // During propagation, we do not change the text of the files.
    fun calculateCurrentTaskChanges(): UserChanges {
      val toRemove = HashMap(targetVisibleFilesStateWithoutVisibilityChange)
      val visibleFileChanges = mutableListOf<Change>()

      for ((path, text) in currentVisibleFilesStateWithoutVisibilityChange) {
        val targetText = toRemove.remove(path)
        // Propagate user-created files
        if (targetText == null) {
          visibleFileChanges += Change.PropagateLearnerCreatedTaskFile(path, text)
        }
      }

      // Remove user-deleted files
      for ((path, _) in toRemove) {
        visibleFileChanges += Change.RemoveTaskFile(path)
      }

      // Calculate diff for invisible files and files that become visible and change them without propagation
      val invisibleFileChanges = calculateChanges(
        currentInvisibleFilesState,
        targetInvisibleFilesState + fromInvisibleToVisibleFilesState
      )
      return invisibleFileChanges + visibleFileChanges
    }

    // target task initialization
    if (targetTask.record == -1) {
      return calculateCurrentTaskChanges()
    }

    // if current and target states of visible files are the same
    // it needs to calculate only diff for invisible files and for files that change visibility from invisible to visible
    if (currentVisibleFilesStateWithoutVisibilityChange == targetVisibleFilesStateWithoutVisibilityChange) {
      return calculateChanges(
        currentInvisibleFilesState,
        targetInvisibleFilesState + fromInvisibleToVisibleFilesState
      )
    }

    val keepConflictingChanges = if (showDialogIfConflict) {
      val currentTaskName = "${currentTask.getUIName()} ${currentTask.index}"
      val targetTaskName = "${targetTask.getUIName()} ${targetTask.index}"
      val message = EduCoreBundle.message("framework.lesson.changes.conflict.message", currentTaskName, targetTaskName, targetTaskName,
                                          currentTaskName)
      Messages.showYesNoDialog(project,
                               message,
                               EduCoreBundle.message("framework.lesson.changes.conflicting.changes.title"),
                               EduCoreBundle.message("framework.lesson.changes.conflicting.changes.keep"),
                               EduCoreBundle.message("framework.lesson.changes.conflicting.changes.replace"),
                               null)
    }
    else {
      Messages.YES
    }

    return if (keepConflictingChanges == Messages.YES) {
      calculateChanges(currentState, targetState)
    }
    else {
      calculateCurrentTaskChanges()
    }
  }

  private fun updateUserChanges(record: Int, initialFiles: Map<String, String>, taskDir: VirtualFile): UpdatedUserChanges {
    val documentManager = FileDocumentManager.getInstance()
    val currentState = HashMap<String, String>()
    for ((path, _) in initialFiles) {
      val file = taskDir.findFileByRelativePath(path) ?: continue

      val text = if (file.isToEncodeContent) {
        file.loadEncodedContent(isToEncodeContent = true)
      }
      else runReadAction { documentManager.getDocument(file)?.text }

      if (text == null) {
        continue
      }

      currentState[path] = text
    }
    val userChanges = calculateChanges(initialFiles, currentState)
    return updateUserChanges(record, userChanges)
  }

  @Synchronized
  private fun updateUserChanges(record: Int, changes: UserChanges): UpdatedUserChanges {
    return try {
      val newRecord = storage.updateUserChanges(record, changes)
      storage.force()
      UpdatedUserChanges(newRecord, changes)
    }
    catch (e: IOException) {
      LOG.error("Failed to update user changes", e)
      UpdatedUserChanges(record, UserChanges.empty())
    }
  }

  /**
   * Returns [Change]s to convert [currentState] to [targetState]
   */
  private fun calculateChanges(
    currentState: Map<String, String>,
    targetState: Map<String, String>
  ): UserChanges {
    val changes = mutableListOf<Change>()
    val current = HashMap(currentState)
    loop@ for ((path, nextText) in targetState) {
      val currentText = current.remove(path)
      changes += when {
        currentText == null -> Change.AddFile(path, nextText)
        currentText != nextText -> Change.ChangeFile(path, nextText)
        else -> continue@loop
      }
    }

    current.mapTo(changes) { Change.RemoveFile(it.key) }
    return UserChanges(changes)
  }

  private val Task.allFiles: Map<String, String> get() = taskFiles.mapValues { it.value.text }

  private fun Map<String, String>.split(task: Task): Pair<Map<String, String>, Map<String, String>> {
    val visibleFiles = HashMap<String, String>()
    val invisibleFiles = HashMap<String, String>()

    for ((path, text) in this) {
      // TaskFiles and state may not be consistent due to external changes in hyperskill lessons.
      // if there is a task in state that is not in taskFiles, then we know that it is a visible file.
      val isVisibleFile = task.taskFiles[path]?.isVisible ?: true
      val state = if (isVisibleFile) visibleFiles else invisibleFiles
      state[path] = text
    }

    return visibleFiles to invisibleFiles
  }

  override fun dispose() {
    Disposer.dispose(storage)
  }

  companion object {
    private val LOG: Logger = Logger.getInstance(FrameworkLessonManagerImpl::class.java)

    const val VERSION: Int = 1

    @VisibleForTesting
    fun constructStoragePath(project: Project): Path =
      Paths.get(FileUtil.join(project.basePath!!, Project.DIRECTORY_STORE_FOLDER, "frameworkLessonHistory", "storage"))

    @VisibleForTesting
    fun createStorage(project: Project): FrameworkStorage {
      val storageFilePath = constructStoragePath(project)
      val storage = FrameworkStorage(storageFilePath)
      return try {
        storage.migrate(VERSION)
        storage
      }
      catch (e: IOException) {
        LOG.error(e)
        AbstractStorage.deleteFiles(storageFilePath.toString())
        FrameworkStorage(storageFilePath, VERSION)
      }
    }
  }
}

private data class UpdatedUserChanges(
  val record: Int,
  val changes: UserChanges
)
