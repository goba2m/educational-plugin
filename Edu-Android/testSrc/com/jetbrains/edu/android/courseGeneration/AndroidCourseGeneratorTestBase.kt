package com.jetbrains.edu.android.courseGeneration

import com.android.tools.idea.startup.GradleSpecificInitializer
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.jetbrains.edu.jvm.courseGeneration.JvmCourseGenerationTestBase
import com.jetbrains.edu.learning.*
import org.hamcrest.CoreMatchers
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.junit.Assert.assertThat

abstract class AndroidCourseGeneratorTestBase : JvmCourseGenerationTestBase() {

  override fun setUp() {
    super.setUp()
    disableUnnecessaryExtensions()
    disableProjectSyncNotifications()
  }

  // Disables some extensions provided by AS.
  // They try to set up JAVA and Android JDK in test that we don't need in these tests.
  // So let's unregister them. Otherwise, tests fail
  protected open fun disableUnnecessaryExtensions() {
    val extensionArea = ApplicationManager.getApplication().extensionArea

    @Suppress("UnstableApiUsage")
    extensionArea
      .getExtensionPoint<ApplicationInitializedListener>("com.intellij.applicationInitializedListener")
      .unregisterExtensionInTest(GradleSpecificInitializer::class.java)
  }

  @Suppress("UnstableApiUsage")
  protected fun <T : Any, K : T> ExtensionPoint<T>.unregisterExtensionInTest(extensionClass: Class<K>) {
    require(this is ExtensionPointImpl)
    val filteredExtensions = extensionList.filter { !extensionClass.isInstance(it) }
    maskAll(filteredExtensions, testRootDisposable, false)
  }

  fun `test new course structure`() {
    val course = newCourse(KotlinLanguage.INSTANCE, environment = EduNames.ANDROID)
    createCourseStructure(course)

    val expectedFileTree = fileTree {
      dir("lesson1/task1") {
        dir("src") {
          dir("main") {
            dir("java/com/example/android/course") {
              file("MainActivity.kt")
            }
            dir("res") {
              dir("layout") {
                file("activity_main.xml")
              }
              dir("values") {
                file("colors.xml")
                file("strings.xml")
                file("themes.xml")
              }
              dir("values-night") {
                file("themes.xml")
              }
            }
            file("AndroidManifest.xml")
          }
          dir("test/java/com/example/android/course") {
            file("ExampleUnitTest.kt")
          }
          dir("androidTest/java/com/example/android/course") {
            file("AndroidEduTestRunner.kt")
            file("ExampleInstrumentedTest.kt")
          }
        }
        file("task.md")
        file("build.gradle")
      }
      gradleWrapperFiles()
      file("local.properties")
      file("gradle.properties")
      file("build.gradle")
      file("settings.gradle")
    }

    expectedFileTree.assertEquals(rootDir)

    val gradleProperties = findFile("gradle.properties")
    val text = VfsUtil.loadText(gradleProperties)
    listOf(
      "android.enableJetifier=true",
      "android.useAndroidX=true",
      "org.gradle.jvmargs=-Xmx1536m"
    ).forEach { line ->
      assertThat(text, CoreMatchers.containsString(line))
    }
  }

  fun `test do not rewrite already created additional files`() {
    val course = course(language = KotlinLanguage.INSTANCE, environment = EduNames.ANDROID) {
      additionalFile("gradle.properties", "some.awesome.property=true")
    }
    createCourseStructure(course)

    fileTree {
      gradleWrapperFiles()
      file("local.properties")
      file("gradle.properties", "some.awesome.property=true")
      file("build.gradle")
      file("settings.gradle")
    }.assertEquals(rootDir)
  }

  private fun FileTreeBuilder.gradleWrapperFiles() {
    dir("gradle/wrapper") {
      file("gradle-wrapper.jar")
      file("gradle-wrapper.properties")
    }
    file("gradlew")
    file("gradlew.bat")
  }

  // Doesn't allow `ProjectSyncStatusNotificationProvider` to show `ProjectSyncStatusNotificationProvider.ProjectStructureNotificationPanel`.
  // These tests are not intended to check Gradle sync, only file structure
  private fun disableProjectSyncNotifications() {
    val component = PropertiesComponent.getInstance()
    val oldValue = component.getValue(PROJECT_STRUCTURE_NOTIFICATION_LAST_HIDDEN_TIMESTAMP)
    component.setValue(PROJECT_STRUCTURE_NOTIFICATION_LAST_HIDDEN_TIMESTAMP, System.currentTimeMillis().toString())
    Disposer.register(testRootDisposable) {
      PropertiesComponent.getInstance().setValue(PROJECT_STRUCTURE_NOTIFICATION_LAST_HIDDEN_TIMESTAMP, oldValue)
    }
  }

  companion object {
    // See com.android.tools.idea.gradle.notification.ProjectSyncStatusNotificationProvider.ProjectStructureNotificationPanel.userAllowsShow
    private const val PROJECT_STRUCTURE_NOTIFICATION_LAST_HIDDEN_TIMESTAMP = "PROJECT_STRUCTURE_NOTIFICATION_LAST_HIDDEN_TIMESTAMP"
  }
}
