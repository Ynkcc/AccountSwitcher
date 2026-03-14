package com.tencent.tim.ui.main

import com.tencent.tim.data.local.AccountEntity

data class MainState(
    val accounts: List<AccountEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class MainIntent {
    object LoadAccounts : MainIntent()
    object SaveCurrentAccount : MainIntent()
    data class SetSelectedAccount(val openid: String) : MainIntent()
    data class ShowAccountDetails(val account: AccountEntity) : MainIntent()
    data class SwitchAndPlay(val openid: String) : MainIntent()
    data class SwitchAccount(val openid: String) : MainIntent()
    data class DeleteAccount(val openid: String) : MainIntent()
    object ClearCurrentAccount : MainIntent()
    object RefreshAccountsOnlineInfo : MainIntent()
    object RestartApp : MainIntent()
}

sealed class MainEffect {
    data class ShowToast(val message: String) : MainEffect()
    data class ShowAccountDetails(val account: AccountEntity) : MainEffect()
}
