package com.jetbrains.edu.learning.update.elements

import com.jetbrains.edu.learning.courseFormat.tasks.Task

class TaskUpdate(val localItem: Task?, val remoteItem: Task?) : EduStudyItemUpdate<Task>(localItem, remoteItem)