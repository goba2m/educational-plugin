package com.jetbrains.edu.github

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.edu.learning.github.PostToGithubActionProvider
import org.jetbrains.plugins.github.GithubShareAction

class PostToGithubProviderImpl : PostToGithubActionProvider {

  override fun postToGitHub(project: Project, file: VirtualFile) {
    GithubShareAction.shareProjectOnGithub(project, file)
  }
}
