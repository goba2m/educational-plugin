package com.jetbrains.edu.coursecreator.courseignore

import com.intellij.openapi.vcs.changes.ignore.cache.PatternCache
import com.intellij.openapi.vcs.changes.ignore.lang.Syntax
import java.util.regex.Pattern

private data class IgnorePattern(val pattern: Pattern, val isNegated: Boolean)

class CourseIgnoreFile private constructor(private val patterns: List<IgnorePattern>) {

  fun isIgnored(path: String): Boolean {
    var ignored = false

    for ((pattern, isNegated) in patterns) {
      if (pattern.matcher(path).find()) {
        ignored = !isNegated
      }
    }

    return ignored
  }

  companion object {

    private val GLOB_SYNTAX_PRESENTATION = Syntax.GLOB.presentation
    private val REGEXP_SYNTAX_PRESENTATION = Syntax.REGEXP.presentation

    fun parse(text: String, patternsCache: PatternCache): CourseIgnoreFile {
      var currentSyntax = Syntax.GLOB

      val patterns = mutableListOf<IgnorePattern>()

      for (line in text.lines()) {
        val trimmedLine = line.trimStart()

        if (trimmedLine.isEmpty()) continue

        // comments
        if (trimmedLine.startsWith('#')) continue

        val syntaxChange = checkSyntaxLine(trimmedLine)
        if (syntaxChange != null) {
          currentSyntax = syntaxChange
          continue
        }

        val isNegated = trimmedLine.startsWith("!")
        val globOrRegexp = if (isNegated) {
          trimmedLine.substring(1)
        }
        else {
          trimmedLine
        }

        val regexp = patternsCache.createPattern(globOrRegexp, currentSyntax) ?: continue

        patterns.add(IgnorePattern(regexp, isNegated))
      }

      return CourseIgnoreFile(patterns)
    }

    private fun checkSyntaxLine(line: String): Syntax? = when (line.trimEnd()) {
      GLOB_SYNTAX_PRESENTATION -> Syntax.GLOB
      REGEXP_SYNTAX_PRESENTATION -> Syntax.REGEXP
      else -> null
    }
  }
}