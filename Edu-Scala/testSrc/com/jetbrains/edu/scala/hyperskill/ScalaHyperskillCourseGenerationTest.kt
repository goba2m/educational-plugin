package com.jetbrains.edu.scala.hyperskill

import com.jetbrains.edu.jvm.gradle.GradleCourseBuilderBase.Companion.HYPERSKILL_SETTINGS_GRADLE_TEMPLATE_NAME
import com.jetbrains.edu.learning.EduTestCase
import com.jetbrains.edu.learning.courseGeneration.GeneratorUtils
import com.jetbrains.edu.learning.document
import com.jetbrains.edu.learning.stepik.hyperskill.courseFormat.HyperskillCourse
import com.jetbrains.edu.scala.hyperskill.ScalaHyperskillConfigurator.Companion.SCALA_HYPERSKILL_BUILD_GRADLE_TEMPLATE_NAME
import org.jetbrains.plugins.gradle.util.GradleConstants.DEFAULT_SCRIPT_NAME
import org.jetbrains.plugins.gradle.util.GradleConstants.SETTINGS_FILE_NAME
import org.jetbrains.plugins.scala.ScalaLanguage

class ScalaHyperskillCourseGenerationTest : EduTestCase() {
  fun `test build gradle file`() {
    courseWithFiles(
      courseProducer = ::HyperskillCourse,
      language = ScalaLanguage.INSTANCE
    ) {}
    val actualBuildGradleContent = findFile(DEFAULT_SCRIPT_NAME).document.text
    val expectedBuildGradleContent = GeneratorUtils.getInternalTemplateText(SCALA_HYPERSKILL_BUILD_GRADLE_TEMPLATE_NAME)

    assertEquals(expectedBuildGradleContent, actualBuildGradleContent)
  }

  fun `test settings gradle file`() {
    courseWithFiles(
      courseProducer = ::HyperskillCourse,
      language = ScalaLanguage.INSTANCE
    ) {}
    val actualSettingsGradleContent = findFile(SETTINGS_FILE_NAME).document.text
    val expectedSettingsGradleContent = GeneratorUtils.getInternalTemplateText(HYPERSKILL_SETTINGS_GRADLE_TEMPLATE_NAME)

    assertEquals(expectedSettingsGradleContent, actualSettingsGradleContent)
  }
}