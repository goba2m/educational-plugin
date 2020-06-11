package com.jetbrains.edu.coursecreator.actions.placeholder

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapperPeer
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.HideableDecorator
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBUI
import com.jetbrains.edu.coursecreator.actions.placeholder.CCAddAnswerPlaceholderPanel.Companion.DEFAULT_PLACEHOLDER_TEXT
import com.jetbrains.edu.coursecreator.actions.placeholder.CCAddAnswerPlaceholderPanel.Companion.PLACEHOLDER_PANEL_WIDTH
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholderDependency
import com.jetbrains.edu.learning.courseFormat.tasks.Task
import com.jetbrains.edu.learning.messages.EduCoreBundle.message
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

open class CCCreateAnswerPlaceholderDialog(
  project: Project,
  isEdit: Boolean,
  private val placeholder: AnswerPlaceholder
) : DialogWrapper(project, true) {

  private val panel: CCAddAnswerPlaceholderPanel = CCAddAnswerPlaceholderPanel(placeholder.placeholderText ?: DEFAULT_PLACEHOLDER_TEXT)
  private val dependencyPathField: JBTextField = JBTextField(0)
  private val visibilityCheckBox: JBCheckBox = JBCheckBox(message("label.visible"), placeholder.placeholderDependency?.isVisible == true)
  private val pathLabel: JLabel = JLabel(message("ui.dialog.create.answer.placeholder.path.pattern"))
  private val isFirstTask: Boolean = placeholder.taskFile.task.isFirstInCourse
  private val currentText: String get() = dependencyPathField.text ?: ""

  init {
    title = if (isEdit) message("ui.dialog.create.answer.placeholder.edit") else message("ui.dialog.create.answer.placeholder.add")
    val buttonText = if (isEdit) message("label.ok") else message("label.add")
    setOKButtonText(buttonText)
    super.init()
    initValidation()
  }

  override fun createCenterPanel(): JComponent {
    dependencyPathField.putClientProperty(DialogWrapperPeer.HAVE_INITIAL_SELECTION, true)
    pathLabel.foreground = JBColor.GRAY

    if (!isFirstTask) {
      val dependencyPanel = JPanel(BorderLayout())
      val contentPanel = panel {
        row { dependencyPathField() }
        row { pathLabel() }
        row { visibilityCheckBox() }
      }
      contentPanel.border = JBUI.Borders.emptyBottom(5)
      val decorator = HideableDecorator(dependencyPanel, message("ui.dialog.create.answer.placeholder.dependency"), true)
      decorator.setContentComponent(contentPanel)
      if (placeholder.placeholderDependency != null) {
        decorator.setOn(true)
        dependencyPathField.text = placeholder.placeholderDependency?.toString()
        panel.preferredSize = JBUI.size(PLACEHOLDER_PANEL_WIDTH, 230)
      }

      dependencyPanel.alignmentX = Component.LEFT_ALIGNMENT
      contentPanel.maximumSize = JBUI.size(Int.MAX_VALUE, 0)
      dependencyPathField.minimumSize = JBUI.size(PLACEHOLDER_PANEL_WIDTH, 0)
      panel.add(dependencyPanel, BorderLayout.SOUTH)
      panel.minimumSize = JBUI.size(PLACEHOLDER_PANEL_WIDTH, 130)
    }
    return panel
  }

  override fun doValidate(): ValidationInfo? {
    if (currentText.isEmpty()) {
      return null
    }
    val errorText = try {
      val dependency = AnswerPlaceholderDependency.create(placeholder, currentText)
      if (dependency == null) message("error.invalid.dependency") else null
    }
    catch (e: AnswerPlaceholderDependency.InvalidDependencyException) {
      e.customMessage
    }
    return if (errorText != null) ValidationInfo(errorText) else null
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return panel.getPreferredFocusedComponent()
  }

  open fun getPlaceholderText(): String = panel.getAnswerPlaceholderText().trim()

  open fun getDependencyInfo(): DependencyInfo? =
    if (!(currentText.isBlank() || isFirstTask)) {
      DependencyInfo(currentText, visibilityCheckBox.isSelected)
    }
    else null

  data class DependencyInfo(val dependencyPath: String, val isVisible: Boolean)
}

private val Task.isFirstInCourse: Boolean
  get() {
    if (index > 1) {
      return false
    }
    val section = lesson.section ?: return lesson.index == 1
    return section.index == 1 && lesson.index == 1
  }