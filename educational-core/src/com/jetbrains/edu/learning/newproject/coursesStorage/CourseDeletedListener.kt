package com.jetbrains.edu.learning.newproject.coursesStorage

import com.jetbrains.edu.learning.courseFormat.Course

interface CourseDeletedListener {
  fun courseDeleted(course: Course)
}