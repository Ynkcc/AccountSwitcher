package com.tencent.tim.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.tim.domain.AccountInteractor
import com.tencent.tim.ui.model.AccountUiModel
import com.tencent.tim.ui.model.toUiModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val interactor: AccountInteractor
) : ViewModel() {

    private val _state = MutableStateFlow(MainState())
    val state = _state.asStateFlow()

    private val _effect = MutableSharedFlow<MainEffect>()
    val effect = _effect.asSharedFlow()

    init {
        handleIntent(MainIntent.LoadAccounts)
    }

    fun handleIntent(intent: MainIntent) {
        when (intent) {
            is MainIntent.LoadAccounts -> loadAccounts()
            is MainIntent.SaveCurrentAccount -> saveAccount()
            is MainIntent.SetSelectedAccount -> setSelectedAccount(intent.openid)
            is MainIntent.ShowAccountDetails -> showAccountDetails(intent.account)
            is MainIntent.SwitchAndPlay -> switchAndPlay(intent.openid)
            is MainIntent.SwitchAccount -> switchAndPlay(intent.openid)
            is MainIntent.DeleteAccount -> deleteAccount(intent.openid)
            is MainIntent.ClearCurrentAccount -> clearAccount()
            is MainIntent.RefreshAccountsOnlineInfo -> refreshAccountsOnlineInfo()
            is MainIntent.RestartApp -> restartApp()
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
}
