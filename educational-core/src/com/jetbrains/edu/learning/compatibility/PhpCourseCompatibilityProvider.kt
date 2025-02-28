package com.jetbrains.edu.learning.compatibility

import com.jetbrains.edu.EducationalCoreIcons
import com.jetbrains.edu.learning.courseFormat.PluginInfo
import javax.swing.Icon

class PhpCourseCompatibilityProvider : CourseCompatibilityProvider {

  override val logo: Icon get() = EducationalCoreIcons.PhpLogo

  override val technologyName: String get() = "PHP"

  override fun requiredPlugins(): List<PluginInfo> = listOf(PluginInfo.PHP)
}