import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*

rootProject.name = "educational-plugin"
include(
  "educational-core",
  "edu-format",
  "code-insight",
  "code-insight:html",
  "code-insight:markdown",
  "code-insight:yaml",
  "jvm-core",
  "remote-env",
  "Edu-Java",
  "Edu-Kotlin",
  "Edu-Scala",
  "Edu-Python",
  "Edu-Python:Idea", // python support for IDEA and Android Studio
  "Edu-Python:PyCharm", // python support for PyCharm and CLion
  "Edu-Android",
  "Edu-JavaScript",
  "Edu-Rust",
  "Edu-Cpp",
  "Edu-Go",
  "Edu-Php",
  "Edu-Shell",
  "sql",
  "sql:sql-jvm",
  "github"
)

apply(from = "common.gradle.kts")

val secretProperties: String by extra
val inJetBrainsNetwork: () -> Boolean by extra

val isTeamCity: Boolean get() = System.getenv("TEAMCITY_VERSION") != null

configureSecretProperties()

downloadHyperskillCss()

fun configureSecretProperties() {
  if (inJetBrainsNetwork() || isTeamCity) {
    download(URL("https://repo.labs.intellij.net/edu-tools/secret.properties"), secretProperties)
  }
  else {
    val secretProperties = file(secretProperties)
    if (!secretProperties.exists()) {
      secretProperties.createNewFile()
    }
  }

  val secretProperties = loadProperties(secretProperties)

  secretProperties.extractAndStore(
    "educational-core/resources/stepik/stepik.properties",
    "stepikClientId",
    "stepikClientSecret",
    "stepikNonProductionClientId",
    "stepikNonProductionClientSecret",
    "cogniterraClientId",
    "cogniterraClientSecret"
  )
  secretProperties.extractAndStore(
    "educational-core/resources/hyperskill/hyperskill-oauth.properties",
    "hyperskillClientId",
    "hyperskillClientSecret"
  )
  secretProperties.extractAndStore(
    "educational-core/resources/twitter/oauth_twitter.properties",
    "twitterConsumerKey",
    "twitterConsumerSecret"
  )
  secretProperties.extractAndStore(
    "Edu-Python/resources/checkio/py-checkio-oauth.properties",
    "pyCheckioClientId",
    "pyCheckioClientSecret"
  )
  secretProperties.extractAndStore(
    "Edu-JavaScript/resources/checkio/js-checkio-oauth.properties",
    "jsCheckioClientId",
    "jsCheckioClientSecret"
  )
  secretProperties.extractAndStore(
    "edu-format/resources/aes/aes.properties",
    "aesKey"
  )
  secretProperties.extractAndStore(
    "educational-core/resources/marketplace/marketplace-oauth.properties",
    "eduHubClientId",
    "eduHubClientSecret",
    "marketplaceHubClientId"
  )
}

fun downloadHyperskillCss() {
  try {
    download(URL("https://hyperskill.org/static/shared.css"), "educational-core/resources/style/hyperskill_task.css")
  }
  catch (e: IOException) {
    System.err.println("Error downloading: ${e.message}. Using local copy")
    Files.copy(
      Paths.get("hyperskill_default.css"),
      Paths.get("educational-core/resources/style/hyperskill_task.css"),
      StandardCopyOption.REPLACE_EXISTING
    )
  }
}

fun download(url: URL, dstPath: String) {
  println("Download $url")

  url.openStream().use {
    val path = file(dstPath).toPath().toAbsolutePath()
    println("Copying file to $path")
    Files.copy(it, path, StandardCopyOption.REPLACE_EXISTING)
  }
}

fun loadProperties(path: String): Properties {
  val properties = Properties()
  file(path).bufferedReader().use { properties.load(it) }
  return properties
}

fun Properties.extractAndStore(path: String, vararg keys: String) {
  val properties = Properties()
  for (key in keys) {
    properties[key] = getProperty(key) ?: ""
  }
  val file = file(path)
  file.parentFile?.mkdirs()
  file.bufferedWriter().use { properties.store(it, "") }
}

buildCache {
  local {
    isEnabled = !isTeamCity
    // By default, build cache is stored in gradle home directory
    directory = File(rootDir, "build/build-cache")
    removeUnusedEntriesAfterDays = 30
  }
}

pluginManagement {
  repositories {
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    gradlePluginPortal()
  }
}
