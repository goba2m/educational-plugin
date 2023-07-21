package com.jetbrains.edu.coursecreator.courseignore

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ignore.cache.PatternCache
import com.jetbrains.edu.learning.EduNames.COURSE_IGNORE
import com.jetbrains.edu.learning.courseDir

/**
 * This service determines whether the files are ignored by the .courseignore file or not.
 * It reads the .courseignore PSI tree to collect a set of patterns from that file.
 */
class CourseIgnoreChecker(private val project: Project) : Disposable {

  private val patternCache: PatternCache = PatternCache.getInstance(project)

  @Volatile
  private var courseIgnoreFile: CourseIgnoreFile? = null

  init {
    Disposer.register(patternCache, this)
  }

  fun refresh(courseIgnoreText: String) {
    courseIgnoreFile = CourseIgnoreFile.parse(courseIgnoreText, patternCache)
  }

  fun refresh() {
    val text = runReadAction {
      val file = project.courseDir.findChild(COURSE_IGNORE)
      val bytes = file?.contentsToByteArray() ?: return@runReadAction null
      String(bytes, file.charset)
    } ?: return

    refresh(text)
  }

  fun isIgnored(path: String): Boolean = courseIgnoreFile?.isIgnored(path) ?: false

  override fun dispose() {
  }

  companion object {
    fun getInstance(project: Project): CourseIgnoreChecker {
      return project.getService(CourseIgnoreChecker::class.java)
    }
  }
}