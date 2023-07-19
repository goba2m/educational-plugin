package com.jetbrains.edu.coursecreator.courseignore

import com.intellij.openapi.vcs.changes.ignore.lang.IgnoreFileType
import com.intellij.openapi.vcs.changes.ignore.lang.IgnoreLanguage
import com.intellij.openapi.vcs.changes.ignore.lang.Syntax

class CourseIgnoreLanguage private constructor() : IgnoreLanguage("CourseIgnore", "courseignore") {

  companion object {
    val INSTANCE: CourseIgnoreLanguage = CourseIgnoreLanguage()
  }

  /**
   * Language file type.
   *
   * @return [CourseIgnoreFileType] instance
   */
  override fun getFileType(): IgnoreFileType {
    return CourseIgnoreFileType.INSTANCE
  }

  override fun isSyntaxSupported(): Boolean = true

  override fun getDefaultSyntax(): Syntax = Syntax.GLOB
}