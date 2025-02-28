package com.jetbrains.edu.learning.taskDescription.htmlTransformers

import com.jetbrains.edu.learning.taskDescription.htmlTransformers.steps.*

private val TaskDescriptionHtmlTransformer = HtmlTransformer.pipeline(
  CssHtmlTransformer,
  MediaThemesTransformer,
  ExternalLinkIconsTransformer,
  CodeHighlighter,
  HintsWrapper
)

val TaskDescriptionTransformer = StringHtmlTransformer.pipeline(
  TaskDescriptionHtmlTransformer.toStringTransformer(),
  ResourceWrapper
)