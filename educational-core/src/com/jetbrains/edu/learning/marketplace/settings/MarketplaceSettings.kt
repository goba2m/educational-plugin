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

  private var shareMySolutions : Boolean? = null

  private val scope = CoroutineScope(Dispatchers.IO)

  init {
    scope.launch {
      shareMySolutions = try {
        MarketplaceSubmissionsConnector.getInstance().getSharingPreference()
      } catch (e: Exception) {
        false
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

  fun isShareMySolutions() = shareMySolutions ?: false

  fun setShareMySolutions(state: Boolean) {
    shareMySolutions = state
    scope.launch {
      try {
        if (state) {
          MarketplaceSubmissionsConnector.getInstance().enableSolutionSharing()
        } else {
          MarketplaceSubmissionsConnector.getInstance().disableSolutionSharing()
        }
      } catch (e: Exception) {
        // todo: add some logging and (or) prompting?
        shareMySolutions = !state
      }
    }
  }

  companion object {
    private val LOG = logger<MarketplaceSettings>()

    val INSTANCE: MarketplaceSettings
      get() = service()
  }
}