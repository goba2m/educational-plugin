package com.jetbrains.edu.learning.checkio.connectors

import com.jetbrains.edu.learning.api.EduLoginConnector.Companion.STATE
import com.jetbrains.edu.learning.authUtils.OAuthRestService
import com.jetbrains.edu.learning.checkio.utils.CheckiONames
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.QueryStringDecoder
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.util.regex.Pattern

abstract class CheckiOOAuthRestService(platformName: String, private val oAuthConnector: CheckiOOAuthConnector) :
  OAuthRestService(platformName) {
  private val oAuthCodePattern: Pattern = oAuthConnector.getOAuthPattern()

  override fun getServiceName(): String = oAuthConnector.serviceName

  @Throws(InterruptedException::class, InvocationTargetException::class)
  override fun isHostTrusted(request: FullHttpRequest, urlDecoder: QueryStringDecoder): Boolean {
    val uri = request.uri()
    val codeMatcher = oAuthCodePattern.matcher(uri)
    return if (request.method() === HttpMethod.GET && codeMatcher.matches()) {
      true
    }
    else super.isHostTrusted(request, urlDecoder)
  }

  @Throws(IOException::class)
  override fun execute(
    urlDecoder: QueryStringDecoder,
    request: FullHttpRequest,
    context: ChannelHandlerContext
  ): String? {
    val uri = urlDecoder.uri()
    LOG.info("Request: $uri")
    if (oAuthCodePattern.matcher(uri).matches()) {
      // cannot be null because of pattern
      val code = getStringParameter(CODE_ARGUMENT, urlDecoder)!!

      val receivedState = getStringParameter(STATE, urlDecoder) ?: return sendErrorResponse(
        request,
        context,
        "State param was not received."
      )

      LOG.info("$platformName: OAuth code is handled")
      val success = oAuthConnector.login(code, receivedState)
      return if (success) {
        sendOkResponse(request, context)
      }
      else {
        val errorMessage = "Failed to login to " + CheckiONames.CHECKIO
        sendErrorResponse(request, context, errorMessage)
      }
    }
    sendStatus(HttpResponseStatus.BAD_REQUEST, false, context.channel())
    return "Unknown command: $uri"
  }
}
