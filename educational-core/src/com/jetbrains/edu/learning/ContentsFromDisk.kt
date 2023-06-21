package com.jetbrains.edu.learning

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.edu.learning.courseFormat.BinaryContents
import com.jetbrains.edu.learning.courseFormat.TextualContents

class BinaryContentsFromDisk(val file: VirtualFile) : BinaryContents {
  override val bytes: ByteArray
    get() = file.contentsToByteArray()
}

class TextualContentsFromDisk(val file: VirtualFile) : TextualContents {
  override val text: String
    get() = VfsUtilCore.loadText(file)
}
