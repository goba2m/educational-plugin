package com.jetbrains.edu.learning

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.edu.learning.courseFormat.BinaryContents
import com.jetbrains.edu.learning.courseFormat.TextualContents

class BinaryContentsFromDisk(val file: VirtualFile) : BinaryContents {
  override val bytes: ByteArray
    get() {
      logger<BinaryContentsFromDisk>().info("OOO read binary file")
      return file.contentsToByteArray()
    }
}

class TextualContentsFromDisk(val file: VirtualFile) : TextualContents {
  override val text: String
    get() {
      logger<TextualContentsFromDisk>().info("OOO read textual file")
      return VfsUtilCore.loadText(file)
    }
}
