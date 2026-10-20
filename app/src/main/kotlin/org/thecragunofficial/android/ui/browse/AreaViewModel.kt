package org.thecragunofficial.android.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.thecragunofficial.android.data.repo.NodeRepository
import org.thecragunofficial.android.domain.AreaDetail
import org.thecragunofficial.android.ui.common.UiState
import org.thecragunofficial.android.ui.common.describe

class AreaViewModel(private val repo: NodeRepository, private val id: Long) : ViewModel() {
    private val _state = MutableStateFlow<UiState<AreaDetail>>(UiState.Loading)
    val state: StateFlow<UiState<AreaDetail>> = _state

    init { load(force = false) }

    fun reload() = load(force = true)

    private fun load(force: Boolean) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = try {
                UiState.Ready(repo.area(id, force))
            } catch (e: Exception) {
                UiState.Error(e.describe())
            }
        }
    }
}
