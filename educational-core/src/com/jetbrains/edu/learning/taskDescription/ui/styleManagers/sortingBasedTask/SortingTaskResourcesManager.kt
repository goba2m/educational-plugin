package com.jetbrains.edu.learning.taskDescription.ui.styleManagers.sortingBasedTask

import com.google.gson.Gson
import com.jetbrains.edu.learning.courseFormat.tasks.matching.SortingTask
import kotlinx.css.*

class SortingTaskResourcesManager : SortingBasedTaskResourcesManager<SortingTask>() {
  override fun getCaptions(task: SortingTask): String = Gson().toJson(emptyList<String>())

  override val resources: Map<String, String>
    get() = mapOf(wrapIntoStyleName(SortingTask.SORTING_TASK_TYPE) to stylesheet)

  override val stylesheet: String
    get() {
      return super.stylesheet + CSSBuilder().apply {
        "#options" {
          paddingLeft = 8.px
        }
        "#keyValueGrid" {
          gridTemplateColumns = GridTemplateColumns(GridAutoRows.auto)
        }
        ".value" {
          gridColumn = GridColumn("1")
        }
      }.toString()
    }
}