package com.ezbook.mobile.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezbook.mobile.Graph
import com.ezbook.mobile.data.model.Account
import com.ezbook.mobile.util.describeError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val error: String? = null
)

class AccountsViewModel : ViewModel() {

    private val _state = MutableStateFlow(AccountsUiState())
    val state: StateFlow<AccountsUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var loadedOnce = false

    fun onEnter() {
        if (!loadedOnce) {
            reload(showLoading = true)
        }
    }

    fun refresh() = reload(showLoading = false)

    private fun reload(showLoading: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update {
                it.copy(loading = showLoading, refreshing = !showLoading, error = null)
            }
            try {
                val accounts = Graph.repo.accounts(force = true)
                loadedOnce = true
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        accounts = accounts,
                        error = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, refreshing = false, error = describeError(e))
                }
            }
        }
    }
}
