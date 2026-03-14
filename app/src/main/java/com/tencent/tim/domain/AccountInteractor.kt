package com.tencent.tim.domain

import com.tencent.tim.data.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import com.tencent.tim.data.local.AccountEntity

class AccountInteractor(
    private val repository: AccountRepository
) {
    val allAccounts: Flow<List<AccountEntity>> = repository.allAccounts

    suspend fun saveCurrentAccount() = repository.saveCurrentAccount()
    
    suspend fun switchAccount(openid: String) = repository.switchAccount(openid)
    
    suspend fun clearCurrentAccount() = repository.clearCurrentAccount()

    suspend fun refreshAccountsOnlineInfo() = repository.refreshAccountsOnlineInfo()
    
    suspend fun restartApp() = repository.restartApp()
    
    suspend fun deleteAccount(openid: String) = repository.deleteAccount(openid)
    
    suspend fun buildAgentResponse(openid: String) = repository.buildAgentResponse(openid)

    suspend fun setSelectedAccount(openid: String) = repository.setSelectedAccount(openid)

    suspend fun getSelectedAccount() = repository.getSelectedAccount()

    suspend fun manualImportAccount(
        accessToken: String,
        openid: String,
        payToken: String
    ) = repository.manualImportAccount(accessToken, openid, payToken)
}
