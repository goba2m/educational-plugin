package com.jetbrains.edu.kotlin.checker

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.jetbrains.edu.jvm.gradle.checker.GradleCommandLine
import com.jetbrains.edu.jvm.gradle.checker.NewGradleEduTaskChecker
import com.jetbrains.edu.jvm.gradle.checker.hasSeparateModule
import com.jetbrains.edu.learning.courseFormat.CheckResult
import com.jetbrains.edu.learning.checker.EnvironmentChecker
import com.jetbrains.edu.learning.courseFormat.ext.findTestDirs
import com.jetbrains.edu.learning.courseFormat.tasks.EduTask
import org.jetbrains.kotlin.idea.extensions.KotlinTestFrameworkProvider
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration

class KtNewGradleTaskChecker(task: EduTask, envChecker: EnvironmentChecker, project: Project) :
  NewGradleEduTaskChecker(task, envChecker, project) {

  override fun computePossibleErrorResult(indicator: ProgressIndicator, stderr: String): CheckResult {
    return if (task.hasSeparateModule(project)) {
      super.computePossibleErrorResult(indicator, stderr)
    } else  {
      GradleCommandLine.create(project, "testClasses")?.launchAndCheck(indicator) ?: CheckResult.failedToCheck
    }
  }

  override fun createDefaultTestConfigurations(): List<RunnerAndConfigurationSettings> {
    val testConfigurations = super.createDefaultTestConfigurations()
    if (task.hasSeparateModule(project) || testConfigurations.size != 1) {
      return testConfigurations
    }

    val configuration = testConfigurations.single().configuration as? GradleRunConfiguration ?: return emptyList()

    val testDirs = task.findTestDirs(project)
    check(testDirs.isNotEmpty()) {
      error("Failed to find test dirs for task ${task.name}")
    }

    val testClasses: List<String> = testDirs.flatMap { testDir ->
      testDir.children.mapNotNull {
        val psiFile = PsiManager.getInstance(project).findFile(it) ?: return@mapNotNull null
        for (extension in KotlinTestFrameworkProvider.EP_NAME.extensionList) {
          val testClass = extension.getJavaTestEntity(psiFile, checkMethod = false)?.testClass ?: continue
          return@mapNotNull testClass.qualifiedName
        }

        null
      }
    }

    configuration.settings.scriptParameters = testClasses.joinToString(" ") { "--tests $it" }
    return testConfigurations
  }
}
