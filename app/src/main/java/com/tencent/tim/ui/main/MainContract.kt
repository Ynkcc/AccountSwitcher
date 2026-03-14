package com.tencent.tim.ui.main

import com.tencent.tim.manager.OperationMode
import com.tencent.tim.ui.model.AccountUiModel

data class MainState(
    val accounts: List<AccountUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val operationMode: OperationMode = OperationMode.NONE,
    val isShizukuAvailable: Boolean = false,
    val isRootAvailable: Boolean = false
)

sealed class MainIntent {
    object LoadAccounts : MainIntent()
    object SaveCurrentAccount : MainIntent()
    object RequestExportAccountsToFile : MainIntent()
    object RequestImportAccountsFromFile : MainIntent()
    data class ExportAccountsToFile(val uriString: String) : MainIntent()
    data class ImportAccountsFromFile(val uriString: String) : MainIntent()
    data class SetSelectedAccount(val openid: String) : MainIntent()
    data class ShowAccountDetails(val account: AccountUiModel) : MainIntent()
    data class SwitchAndPlay(val openid: String) : MainIntent()
    data class SwitchAccount(val openid: String) : MainIntent()
    data class DeleteAccount(val openid: String) : MainIntent()
    object ClearCurrentAccount : MainIntent()
    object RefreshAccountsOnlineInfo : MainIntent()
    object RestartApp : MainIntent()
    object HideQQ : MainIntent()
    object RestoreQQ : MainIntent()
    data class ManualImportAccount(
        val accessToken: String,
        val openid: String,
        val payToken: String
    ) : MainIntent()
    object RequestShizukuPermission : MainIntent()
    object CheckModes : MainIntent()
}

sealed class MainEffect {
    data class ShowToast(val message: String) : MainEffect()
    data class ShowAccountDetails(val account: AccountUiModel) : MainEffect()
    data class PickExportAccountsFile(val suggestedFileName: String) : MainEffect()
    object PickImportAccountsFile : MainEffect()
}
