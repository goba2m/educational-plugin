package com.jetbrains.edu.coursecreator.courseignore

import com.jetbrains.edu.learning.courseFormat.COURSE_IGNORE_FORMAT_VERSION

/**
 * Course ignore versions are described in the Versions.md
 */
class CourseIgnoreMigrator {
  companion object {

    fun migrate(courseIgnore: String, version: Int): String {
      var result = courseIgnore

      for (newVersion in version+1..COURSE_IGNORE_FORMAT_VERSION) {
        result = when (newVersion) {
          1 -> upgradeToVersion1(result)
          else -> result
        }
      }
      return result
    }

    private fun upgradeToVersion1(courseIgnore: String): String {
      val lines = courseIgnore.lines()

      // add / to the beginning of each file, that makes each entry to be interpreted as a file path relative to the course root

      val rootedLines = lines.map { line ->
        val trimmedLine = line.trimStart()
        val indent = line.substring(0, line.length - trimmedLine.length)

        if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("/")){
          "$indent/$trimmedLine"
        }
        else {
          line
        }
      }

      return rootedLines.joinToString("\n")
    }
  }
}