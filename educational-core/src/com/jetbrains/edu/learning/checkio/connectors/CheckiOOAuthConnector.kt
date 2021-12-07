package com.jetbrains.edu.learning.checkio.connectors

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.annotations.Transient
import com.jetbrains.edu.learning.EduLogInListener
import com.jetbrains.edu.learning.EduUtils
import com.jetbrains.edu.learning.api.ConnectorUtils
import com.jetbrains.edu.learning.api.EduOAuthConnector
import com.jetbrains.edu.learning.authUtils.CustomAuthorizationServer
import com.jetbrains.edu.learning.authUtils.OAuthUtils
import com.jetbrains.edu.learning.checkio.account.CheckiOAccount
import com.jetbrains.edu.learning.checkio.api.CheckiOEndpoints
import com.jetbrains.edu.learning.checkio.api.exceptions.ApiException
import com.jetbrains.edu.learning.checkio.api.exceptions.NetworkException
import com.jetbrains.edu.learning.checkio.api.executeHandlingCheckiOExceptions
import com.jetbrains.edu.learning.checkio.exceptions.CheckiOLoginRequiredException
import com.jetbrains.edu.learning.checkio.notifications.CheckiONotifications.error
import com.jetbrains.edu.learning.checkio.utils.CheckiONames
import com.jetbrains.edu.learning.checkio.utils.CheckiONames.CHECKIO_OAUTH_REDIRECT_HOST
import com.jetbrains.edu.learning.isUnitTestMode
import com.jetbrains.edu.learning.messages.EduCoreBundle.message
import org.apache.http.client.utils.URIBuilder
import org.jetbrains.ide.BuiltInServerManager
import java.net.URI

abstract class CheckiOOAuthConnector : EduOAuthConnector<CheckiOAccount>() {

  @get:Transient
  @set:Transient
  abstract override var account: CheckiOAccount?

  override val baseUrl: String = CheckiONames.CHECKIO_OAUTH_HOST

  override val baseOAuthTokenUrl: String = "oauth/token/"

  protected abstract val oAuthServicePath: String

  protected abstract val platformName: String

  override val objectMapper: ObjectMapper by lazy {
    ConnectorUtils.createRegisteredMapper(SimpleModule())
  }

  private var authorizationBusConnection = ApplicationManager.getApplication().messageBus.connect()

  private val currentPort: Int
    get() = BuiltInServerManager.getInstance().port

  private val customServer: CustomAuthorizationServer
    get() {
      val startedServer = CustomAuthorizationServer.getServerIfStarted(platformName)
      return startedServer ?: createCustomServer()
    }

  private val redirectUri: String
    get() = if (EduUtils.isAndroidStudio()) {
      customServer.handlingUri
    }
    else {
      """$CHECKIO_OAUTH_REDIRECT_HOST:${currentPort}$oAuthServicePath"""
    }

  private val checkiOEndpoints: CheckiOEndpoints
    get() = getEndpoints()

  open fun getAccessToken(): String {
    val currentAccount = account ?: throw CheckiOLoginRequiredException()
    if (!isUnitTestMode && !currentAccount.isUpToDate()) {
      refreshTokens()
    }
    return currentAccount.getAccessToken() ?: error("Cannot get access token")
  }

  fun doAuthorize(vararg postLoginActions: Runnable) {
    try {
      if (!OAuthUtils.checkBuiltinPortValid()) return

      val oauthLink = getOauthLink(redirectUri)
      createAuthorizationListener(*postLoginActions)
      BrowserUtil.browse(oauthLink)
    }
    catch (e: Exception) {
      // IOException is thrown when there're no available ports, in some cases restarting can fix this
      Notifications.Bus.notify(error(
        message("notification.title.failed.to.authorize"),
        null,
        message("notification.content.try.to.restart.ide.and.log.in.again")
      ))
    }
  }

  private fun getOauthLink(oauthRedirectUri: String): URI {
    return URIBuilder(CheckiONames.CHECKIO_OAUTH_URL + "/")
      .addParameter("redirect_uri", oauthRedirectUri)
      .addParameter("response_type", "code")
      .addParameter("client_id", clientId)
      .build()
  }

  private fun createAuthorizationListener(vararg postLoginActions: Runnable) {
    authorizationBusConnection.disconnect()
    authorizationBusConnection = ApplicationManager.getApplication().messageBus.connect()
    authorizationBusConnection.subscribe(AUTHORIZATION_TOPIC, object : EduLogInListener {
      override fun userLoggedIn() {
        for (action in postLoginActions) {
          action.run()
        }
      }

      override fun userLoggedOut() {}
    })
  }

  private fun createCustomServer(): CustomAuthorizationServer {
    return CustomAuthorizationServer.create(platformName, oAuthServicePath) { code: String, _: String ->
      login(code)
    }
  }

  @Synchronized
  fun login(code: String): String? {
    return try {
      if (account != null) {
        ApplicationManager.getApplication().messageBus.syncPublisher(AUTHORIZATION_TOPIC).userLoggedIn()
        return "You're logged in already"
      }
      val tokenInfo = retrieveLoginToken(code, redirectUri) ?: return null
      val checkiOAccount = CheckiOAccount(tokenInfo)
      val userInfo = checkiOEndpoints.getUserInfo(tokenInfo.accessToken).executeHandlingCheckiOExceptions()
      checkiOAccount.userInfo = userInfo
      checkiOAccount.saveTokens(tokenInfo)
      account = checkiOAccount
      ApplicationManager.getApplication().messageBus.syncPublisher(AUTHORIZATION_TOPIC).userLoggedIn()
      null
    }
    catch (e: NetworkException) {
      "Connection failed"
    }
    catch (e: ApiException) {
      "Couldn't get user info"
    }
  }

  companion object {
    @JvmStatic
    val AUTHORIZATION_TOPIC: Topic<EduLogInListener> = Topic.create("Edu.checkioUserLoggedIn", EduLogInListener::class.java)
  }
}