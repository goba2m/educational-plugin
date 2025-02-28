package com.jetbrains.edu.learning.compatibility

import com.intellij.util.PlatformUtils.*
import com.jetbrains.edu.EducationalCoreIcons
import com.jetbrains.edu.learning.courseFormat.PluginInfo
import javax.swing.Icon

class JsCourseCompatibilityProvider : CourseCompatibilityProvider {
  override fun requiredPlugins(): List<PluginInfo>? {
    @Suppress("DEPRECATION", "UnstableApiUsage")
    return if (isIdeaUltimate() || isWebStorm() || isPyCharmPro() || isGoIde()) {
      listOf(
        PluginInfo.JAVA_SCRIPT,
        PluginInfo.JAVA_SCRIPT_DEBUGGER,
        PluginInfo.NODE_JS
      )
    } else {
      null
    }
  }

  override val technologyName: String get() = "JavaScript"
  override val logo: Icon get() = EducationalCoreIcons.JsLogo
}