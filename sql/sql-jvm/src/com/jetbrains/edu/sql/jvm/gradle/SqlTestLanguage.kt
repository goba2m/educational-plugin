package com.jetbrains.edu.sql.jvm.gradle

import com.intellij.lang.Language
import com.jetbrains.edu.EducationalCoreIcons
import com.jetbrains.edu.learning.EduNames
import com.jetbrains.edu.learning.courseFormat.Course
import javax.swing.Icon

const val SQL_TEST_LANGUAGE_KEY = "sql_test_language"

var Course.sqlTestLanguage: SqlTestLanguage
  get() {
    val languageId = environmentSettings[SQL_TEST_LANGUAGE_KEY]
    return SqlTestLanguage.values().find { it.languageId == languageId } ?: SqlTestLanguage.KOTLIN
  }
  set(value) {
    environmentSettings += SQL_TEST_LANGUAGE_KEY to value.languageId
  }

/**
 * Default programming language used for test files
 */
enum class SqlTestLanguage(val languageId: String, val logo: Icon) {
  KOTLIN(EduNames.KOTLIN, EducationalCoreIcons.KotlinLogo),
  JAVA(EduNames.JAVA, EducationalCoreIcons.JavaLogo);

  fun getLanguage(): Language? = Language.findLanguageByID(languageId)
}

