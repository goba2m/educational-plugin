package com.jetbrains.edu.learning.marketplace.settings

import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.ProjectManager
import com.jetbrains.edu.learning.marketplace.MarketplaceNotificationUtils
import com.jetbrains.edu.learning.marketplace.api.MarketplaceAccount
import com.jetbrains.edu.learning.marketplace.api.MarketplaceSubmissionsConnector
import com.jetbrains.edu.learning.marketplace.getJBAUserInfo
import com.jetbrains.edu.learning.marketplace.isMarketplaceCourse
import java.util.concurrent.CompletableFuture

class MarketplaceSettings {

  private var account: MarketplaceAccount? = null

  var solutionsSharing: Boolean = false
    private set

  init {
    CompletableFuture.runAsync({
      solutionsSharing = try {
        MarketplaceSubmissionsConnector.getInstance().getSharingPreference()
      } catch (e: Exception) {
        solutionsSharing
      }
    }, ProcessIOExecutorService.INSTANCE)
  }

  fun getMarketplaceAccount(): MarketplaceAccount? {
    if (!MarketplaceAccount.isJBALoggedIn()) {
      account = null
      return null
    }
    val currentAccount = account
    val jbaUserInfo = getJBAUserInfo()
    if (jbaUserInfo == null) {
      LOG.error("User info is null for account ${account?.userInfo?.name}")
      account = null
    }
    else if (currentAccount == null || !currentAccount.checkTheSameUserAndUpdate(jbaUserInfo)) {
      account = MarketplaceAccount(jbaUserInfo)
    }

    return account
  }

  fun setAccount(value: MarketplaceAccount?) {
    account = value
  }

  fun setShareMySolutions(state: Boolean) {
    solutionsSharing = state
    CompletableFuture.runAsync({
      try {
        MarketplaceSubmissionsConnector.getInstance().changeSharingPreference(state)
      } catch (e: Exception) {
        ProjectManager.getInstance().openProjects.forEach {
          if (!it.isDisposed && it.isMarketplaceCourse()) {
            MarketplaceNotificationUtils.showFailedToChangeSolutionSharing(it)
          }
        }
        solutionsSharing = !state
      }
    }, ProcessIOExecutorService.INSTANCE)
  }

  companion object {
    private val LOG = logger<MarketplaceSettings>()

    val INSTANCE: MarketplaceSettings
      get() = service()
  }
}