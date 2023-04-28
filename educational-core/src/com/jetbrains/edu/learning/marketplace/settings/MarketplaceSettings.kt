package com.jetbrains.edu.learning.marketplace.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.jetbrains.edu.learning.marketplace.api.MarketplaceAccount
import com.jetbrains.edu.learning.marketplace.api.MarketplaceSubmissionsConnector
import com.jetbrains.edu.learning.marketplace.getJBAUserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MarketplaceSettings {

  private var account: MarketplaceAccount? = null

  private var solutionsSharing: Boolean = false

  private val scope = CoroutineScope(Dispatchers.IO)

  init {
    scope.launch {
      solutionsSharing = try {
        MarketplaceSubmissionsConnector.getInstance().getSharingPreference()
      } catch (e: Exception) {
        solutionsSharing
      }
    }
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

  fun isSolutionsSharingEnabled() = solutionsSharing

  fun setShareMySolutions(state: Boolean) {
    solutionsSharing = state
    scope.launch {
      try {
        MarketplaceSubmissionsConnector.getInstance().changeSharingPreference(state)
      } catch (e: Exception) {
        // todo: add some logging and (or) prompting?
        solutionsSharing = !state
      }
    }
  }

  companion object {
    private val LOG = logger<MarketplaceSettings>()

    val INSTANCE: MarketplaceSettings
      get() = service()
  }
}