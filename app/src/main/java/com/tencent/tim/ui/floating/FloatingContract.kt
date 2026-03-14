package com.tencent.tim.ui.floating

import com.tencent.tim.data.local.AccountEntity

data class FloatingState(
    val accounts: List<AccountEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isExpanded: Boolean = false,
    val isResponseMode: Boolean = false
)

sealed class FloatingIntent {
    object LoadAccounts : FloatingIntent()
    object SaveCurrentAccount : FloatingIntent()
    data class SwitchAccount(val openid: String) : FloatingIntent()
    data class RespondWithAccount(val openid: String) : FloatingIntent()
    object ClearCurrentAccount : FloatingIntent()
    object RestartApp : FloatingIntent()
    object ToggleExpand : FloatingIntent()
}

sealed class FloatingEffect {
    data class ShowToast(val message: String) : FloatingEffect()
}
