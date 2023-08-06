package com.jetbrains.edu.learning.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.jetbrains.edu.learning.EduSettings
import com.jetbrains.edu.learning.JavaUILibrary

/**
 * IMPORTANT: if you modify anything in this class, updated whitelist rules should be
 * provided to analytics platform team.
 */
@Suppress("UnstableApiUsage")
class EduStateUsagesCollector : ApplicationUsagesCollector() {

  private enum class EduRole {
    @Suppress("unused")
    STUDENT, EDUCATOR
  }

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> {
    val metrics = HashSet<MetricEvent>()

    val taskPanel = EduSettings.getInstance().javaUiLibrary
    metrics += TASK_PANEL_EVENT.metric(taskPanel)
    metrics += ROLE_EVENT.metric(EduRole.EDUCATOR)

    return metrics
  }

  companion object {
    private val GROUP = EventLogGroup("educational.state", 2)

    private val TASK_PANEL_EVENT = GROUP.registerEvent("task.panel", enumField<JavaUILibrary>())
    private val ROLE_EVENT = GROUP.registerEvent("role", enumField<EduRole>())
  }
}
