package com.jetbrains.edu.jvm.gradle.checker

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.jetbrains.edu.learning.Err
import com.jetbrains.edu.learning.Ok
import com.jetbrains.edu.learning.checker.CheckResult
import com.jetbrains.edu.learning.checker.TheoryTaskChecker
import com.jetbrains.edu.learning.checker.details.CheckDetailsView
import com.jetbrains.edu.learning.courseFormat.CheckStatus
import com.jetbrains.edu.learning.courseFormat.tasks.TheoryTask

class GradleTheoryTaskChecker(task: TheoryTask, project: Project) : TheoryTaskChecker(task, project) {
  override fun check(indicator: ProgressIndicator): CheckResult {
    val output = when (val result = runGradleRunTask(project, task, indicator)) {
      is Err -> return result.error
      is Ok -> result.value
    }

    CheckDetailsView.getInstance(project).showOutput(output)
    return CheckResult(CheckStatus.Solved, "")
  }
}
