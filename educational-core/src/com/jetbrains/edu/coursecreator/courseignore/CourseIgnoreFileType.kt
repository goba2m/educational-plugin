package com.jetbrains.edu.coursecreator.courseignore

import com.intellij.openapi.vcs.changes.ignore.lang.IgnoreFileType

class CourseIgnoreFileType : IgnoreFileType(CourseIgnoreLanguage.INSTANCE) {
  companion object {
    val INSTANCE: CourseIgnoreFileType = CourseIgnoreFileType()
  }
}