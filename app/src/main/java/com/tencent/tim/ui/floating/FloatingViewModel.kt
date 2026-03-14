package com.tencent.tim.ui.floating

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.open.agent.AgentActivity
import com.tencent.tim.domain.AccountInteractor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FloatingViewModel(
    private val interactor: AccountInteractor
) : ViewModel() {

    private val _state = MutableStateFlow(FloatingState())
    val state = _state.asStateFlow()

    private val _effect = MutableSharedFlow<FloatingEffect>()
    val effect = _effect.asSharedFlow()

    init {
        handleIntent(FloatingIntent.LoadAccounts)
    }

    fun handleIntent(intent: FloatingIntent) {
        when (intent) {
            is FloatingIntent.LoadAccounts -> loadAccounts()
            is FloatingIntent.SaveCurrentAccount -> saveAccount()
            is FloatingIntent.SwitchAccount -> switchAccount(intent.openid)
            is FloatingIntent.RespondWithAccount -> respondWithAccount(intent.openid)
            is FloatingIntent.ClearCurrentAccount -> clearAccount()
            is FloatingIntent.RestartApp -> restartApp()
            is FloatingIntent.ToggleExpand -> toggleExpand()
        }
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            interactor.allAccounts.collect { accounts ->
                _state.update {
                    it.copy(
                        accounts = accounts,
                        isResponseMode = AgentActivity.isActive()
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
                    _effect.emit(FloatingEffect.ShowToast("成功保存账号: $roleName"))
                }
                .onFailure { error ->
                    _effect.emit(FloatingEffect.ShowToast("保存失败: ${error.message}"))
                }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun switchAccount(openid: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            interactor.switchAccount(openid)
                .onSuccess {
                    _effect.emit(FloatingEffect.ShowToast("切换成功，正在重启游戏..."))
                    interactor.restartApp()
                }
                .onFailure { error ->
                    _effect.emit(FloatingEffect.ShowToast("切换失败: ${error.message}"))
                }
            _state.update { it.copy(isLoading = false, isExpanded = false) }
        }
    }

    private fun clearAccount() {
        viewModelScope.launch {
            if (interactor.clearCurrentAccount()) {
                _effect.emit(FloatingEffect.ShowToast("当前登录已清除"))
                interactor.restartApp()
            } else {
                _effect.emit(FloatingEffect.ShowToast("清除失败"))
            }
        }
    }

    private fun restartApp() {
        viewModelScope.launch {
            interactor.restartApp()
        }
    }

    private fun toggleExpand() {
        _state.update {
            it.copy(
                isExpanded = !it.isExpanded,
                isResponseMode = AgentActivity.isActive()
            )
        }
    }

    private fun respondWithAccount(openid: String) {
        viewModelScope.launch {
            if (!AgentActivity.isActive()) {
                _effect.emit(FloatingEffect.ShowToast("当前无活跃的登录请求"))
                _state.update { it.copy(isResponseMode = false) }
                return@launch
            }

            interactor.buildAgentResponse(openid)
                .onSuccess { response ->
                    AgentActivity.sendResponse(response)
                    _effect.emit(FloatingEffect.ShowToast("响应成功"))
                    _state.update { it.copy(isExpanded = false, isResponseMode = false) }
                }
                .onFailure { error ->
                    _effect.emit(FloatingEffect.ShowToast("响应失败: ${error.message}"))
                }
        }
    }
}
