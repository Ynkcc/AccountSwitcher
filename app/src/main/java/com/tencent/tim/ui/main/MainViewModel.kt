package com.tencent.tim.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.tim.domain.AccountInteractor
import com.tencent.tim.manager.ModeManager
import com.tencent.tim.manager.OperationMode
import com.tencent.tim.manager.QQControlManager
import com.tencent.tim.ui.model.AccountUiModel
import com.tencent.tim.ui.model.toUiModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainViewModel(
    private val interactor: AccountInteractor,
    private val modeManager: ModeManager,
    private val qqControlManager: QQControlManager
) : ViewModel() {
    companion object {
        private val exportFileNameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.getDefault())
    }


    private val _state = MutableStateFlow(MainState())
    val state = _state.asStateFlow()

    private val _effect = MutableSharedFlow<MainEffect>()
    val effect = _effect.asSharedFlow()

    init {
        handleIntent(MainIntent.LoadAccounts)
        handleIntent(MainIntent.CheckModes)
        
        // 启动时请求 Shizuku 授权 (如果 Shizuku 已启动但未授权)
        viewModelScope.launch {
            if (modeManager.isShizukuAlive() && !modeManager.isShizukuAvailable()) {
                modeManager.requestShizukuPermission()
            }
        }

        // 监听模式变化
        viewModelScope.launch {
            modeManager.currentMode.collectLatest { mode ->
                _state.update { it.copy(operationMode = mode) }
            }
        }
    }

    fun handleIntent(intent: MainIntent) {
        when (intent) {
            is MainIntent.LoadAccounts -> loadAccounts()
            is MainIntent.SaveCurrentAccount -> saveAccount()
            is MainIntent.RequestExportAccountsToFile -> requestExportAccountsToFile()
            is MainIntent.RequestImportAccountsFromFile -> requestImportAccountsFromFile()
            is MainIntent.ExportAccountsToFile -> exportAccountsToFile(intent.uriString)
            is MainIntent.ImportAccountsFromFile -> importAccountsFromFile(intent.uriString)
            is MainIntent.SetSelectedAccount -> setSelectedAccount(intent.openid)
            is MainIntent.ShowAccountDetails -> showAccountDetails(intent.account)
            is MainIntent.SwitchAndPlay -> switchAndPlay(intent.openid)
            is MainIntent.SwitchAccount -> switchAndPlay(intent.openid)
            is MainIntent.DeleteAccount -> deleteAccount(intent.openid)
            is MainIntent.ClearCurrentAccount -> clearAccount()
            is MainIntent.RefreshAccountsOnlineInfo -> refreshAccountsOnlineInfo()
            is MainIntent.RestartApp -> restartApp()
            is MainIntent.HideQQ -> hideQQ()
            is MainIntent.RestoreQQ -> restoreQQ()
            is MainIntent.ManualImportAccount -> manualImportAccount(
                accessToken = intent.accessToken,
                openid = intent.openid,
                payToken = intent.payToken
            )
            is MainIntent.RequestShizukuPermission -> requestShizukuPermission()
            is MainIntent.CheckModes -> checkModes()
        }
    }

    private fun checkModes() {
        viewModelScope.launch {
            modeManager.checkAvailability()
            _state.update {
                it.copy(
                    isRootAvailable = modeManager.isRootAvailable(),
                    isShizukuAvailable = modeManager.isShizukuAvailable()
                )
            }
        }
    }

    private fun requestShizukuPermission() {
        viewModelScope.launch {
            modeManager.requestShizukuPermission()
            // 重新检查模式
            checkModes()
        }
    }

    private fun hideQQ() {
        viewModelScope.launch {
            if (qqControlManager.hideQQ()) {
                _effect.emit(MainEffect.ShowToast("QQ 已隐藏"))
            } else {
                _effect.emit(MainEffect.ShowToast("隐藏失败，请检查授权"))
            }
        }
    }

    private fun restoreQQ() {
        viewModelScope.launch {
            if (qqControlManager.unhideQQ()) {
                _effect.emit(MainEffect.ShowToast("QQ 已恢复"))
            } else {
                _effect.emit(MainEffect.ShowToast("恢复失败，请检查授权"))
            }
        }
    }

    private fun setSelectedAccount(openid: String) {
        viewModelScope.launch {
            interactor.setSelectedAccount(openid)
        }
    }

    private fun showAccountDetails(account: AccountUiModel) {
        viewModelScope.launch {
            _effect.emit(MainEffect.ShowAccountDetails(account))
        }
    }

    private fun switchAndPlay(openid: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            interactor.switchAccount(openid)
                .onSuccess {
                    _effect.emit(MainEffect.ShowToast("切换成功，正在重启游戏..."))
                    interactor.restartApp()
                }
                .onFailure { error ->
                    _effect.emit(MainEffect.ShowToast("切换失败: ${error.message}"))
                }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            interactor.allAccounts.collect { accounts ->
                _state.update {
                    it.copy(
                        accounts = accounts.map { account -> account.toUiModel() },
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun saveAccount() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            interactor.saveCurrentAccount()
                .onSuccess { roleName ->
                    _effect.emit(MainEffect.ShowToast("成功保存账号: $roleName"))
                }
                .onFailure { error ->
                    _effect.emit(MainEffect.ShowToast("保存失败: ${error.message}"))
                }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun requestExportAccountsToFile() {
        viewModelScope.launch {
            if (_state.value.accounts.isEmpty()) {
                _effect.emit(MainEffect.ShowToast("暂无账号可导出"))
                return@launch
            }

            val fileName = "accountswitcher_accounts_${LocalDateTime.now().format(exportFileNameFormatter)}.json"
            _effect.emit(MainEffect.PickExportAccountsFile(fileName))
        }
    }

    private fun requestImportAccountsFromFile() {
        viewModelScope.launch {
            _effect.emit(MainEffect.PickImportAccountsFile)
        }
    }

    private fun exportAccountsToFile(uriString: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            interactor.exportAccountsToFile(uriString)
                .onSuccess { count ->
                    _effect.emit(MainEffect.ShowToast("成功导出 $count 个账号"))
                }
                .onFailure { error ->
                    _effect.emit(MainEffect.ShowToast("导出失败: ${error.message}"))
                }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun importAccountsFromFile(uriString: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            interactor.importAccountsFromFile(uriString)
                .onSuccess { summary ->
                    val message = buildString {
                        append("导入完成：新增 ${summary.insertedCount}，更新 ${summary.updatedCount}，跳过 ${summary.skippedCount}")
                        if (summary.invalidCount > 0) {
                            append("，无效 ${summary.invalidCount}")
                        }
                    }
                    _effect.emit(MainEffect.ShowToast(message))
                }
                .onFailure { error ->
                    _effect.emit(MainEffect.ShowToast("导入失败: ${error.message}"))
                }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun deleteAccount(openid: String) {
        viewModelScope.launch {
            interactor.deleteAccount(openid)
            _effect.emit(MainEffect.ShowToast("账号已删除"))
        }
    }

    private fun clearAccount() {
        viewModelScope.launch {
            if (interactor.clearCurrentAccount()) {
                _effect.emit(MainEffect.ShowToast("当前登录已清除"))
                interactor.restartApp()
            } else {
                _effect.emit(MainEffect.ShowToast("清除失败"))
            }
        }
    }

    private fun restartApp() {
        viewModelScope.launch {
            interactor.restartApp()
        }
    }

    private fun refreshAccountsOnlineInfo() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            interactor.refreshAccountsOnlineInfo()
                .onSuccess { (refreshedCount, totalCount) ->
                    val message = if (totalCount == 0) {
                        "暂无可刷新的账号"
                    } else {
                        "在线信息刷新完成：$refreshedCount/$totalCount"
                    }
                    _effect.emit(MainEffect.ShowToast(message))
                }
                .onFailure { error ->
                    _effect.emit(MainEffect.ShowToast("在线信息刷新失败: ${error.message}"))
                }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun manualImportAccount(
        accessToken: String,
        openid: String,
        payToken: String
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            interactor.manualImportAccount(accessToken, openid, payToken)
                .onSuccess { roleName ->
                    _effect.emit(MainEffect.ShowToast("手动导入成功: $roleName"))
                }
                .onFailure { error ->
                    _effect.emit(MainEffect.ShowToast("手动导入失败: ${error.message}"))
                }
            _state.update { it.copy(isLoading = false) }
        }
    }
}
