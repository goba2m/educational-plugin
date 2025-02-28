package com.jetbrains.edu.learning.compatibility

import com.intellij.util.PlatformUtils.*
import com.jetbrains.edu.EducationalCoreIcons
import com.jetbrains.edu.learning.EduUtilsKt
import com.jetbrains.edu.learning.courseFormat.PluginInfo
import javax.swing.Icon

class PyCourseCompatibilityProvider : CourseCompatibilityProvider {

  override fun requiredPlugins(): List<PluginInfo>? {
    @Suppress("DEPRECATION", "UnstableApiUsage")
    return when {
      isPyCharmPro() -> listOf(PluginInfo.PYTHON_PRO)
      isPyCharm() -> listOf(PluginInfo.PYTHON_COMMUNITY)
      isIdeaUltimate() -> listOf(PluginInfo.PYTHON_PRO)
      isCLion() || isIntelliJ() || EduUtilsKt.isAndroidStudio() -> listOf(PluginInfo.PYTHON_COMMUNITY)
      else -> null
    }
  }

  override val technologyName: String get() = "Python"
  override val logo: Icon get() = EducationalCoreIcons.PythonLogo
}
