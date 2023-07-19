package com.jetbrains.edu.learning.update.elements

/**
 * This base class is used to store updating information of the `StudyItem` object
 */
abstract class EduStudyItemUpdate<T>(private val localItem: T?, private val remoteItem: T?) {
  init {
    check(localItem != null || remoteItem != null) {
      "The definition for local or remote items is required"
    }
  }

  fun isCreate(): Boolean = localItem == null && remoteItem != null
  fun isUpdate(): Boolean = localItem != null && remoteItem != null
  fun isDelete(): Boolean = localItem != null && remoteItem == null
}