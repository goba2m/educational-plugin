package com.jetbrains.edu.learning.compatibility

import com.intellij.util.PlatformUtils
import com.jetbrains.edu.EducationalCoreIcons
import com.jetbrains.edu.learning.EduUtilsKt
import com.jetbrains.edu.learning.courseFormat.PluginInfo
import javax.swing.Icon

class KtCourseCompatibilityProvider : CourseCompatibilityProvider {

  override fun requiredPlugins(): List<PluginInfo>? {
    if (!PlatformUtils.isIntelliJ() && !EduUtilsKt.isAndroidStudio()) return null
    return listOf(
      PluginInfo.KOTLIN,
      PluginInfo.JAVA,
      PluginInfo.GRADLE,
      PluginInfo.JUNIT
    )
  }

  override val technologyName: String get() = "Kotlin"
  override val logo: Icon get() = EducationalCoreIcons.KotlinLogo
}
