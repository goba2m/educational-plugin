package com.jetbrains.edu.coursecreator.actions.stepik

import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.edu.learning.messages.EduCoreBundle
import com.jetbrains.edu.learning.stepik.StepikNames
import com.jetbrains.edu.learning.toTitleCase
import javax.swing.Icon

// TODO i18n Class is needed to be refactored
abstract class CCPushAction(itemName: String, icon: Icon? = null) : DumbAwareAction(getActionText(itemName), getActionText(itemName),
                                                                                    icon) {
  protected val itemName = itemName.toTitleCase()

  companion object {
    @JvmStatic
    protected fun getActionText(itemName: String): String = "${getUploadText(itemName)}/${getUpdateText(itemName)}"

    @JvmStatic
    protected fun getUpdateText(itemName: String): String = EduCoreBundle.message("stepik.update", StepikNames.STEPIK, itemName)

    @JvmStatic
    protected fun getUploadText(itemName: String): String = EduCoreBundle.message("stepik.upload", StepikNames.STEPIK, itemName)
  }
}
