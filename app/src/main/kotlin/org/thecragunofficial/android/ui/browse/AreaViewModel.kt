package org.thecragunofficial.android.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.thecragunofficial.android.data.repo.NodeRepository
import org.thecragunofficial.android.data.repo.TopoRepository
import org.thecragunofficial.android.domain.AreaDetail
import org.thecragunofficial.android.domain.NodeKind
import org.thecragunofficial.android.domain.Topo
import org.thecragunofficial.android.ui.common.UiState
import org.thecragunofficial.android.ui.common.describe

class AreaViewModel(
    private val repo: NodeRepository,
    private val topoRepo: TopoRepository,
    private val id: Long,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<AreaDetail>>(UiState.Loading)
    val state: StateFlow<UiState<AreaDetail>> = _state
    private val _topos = MutableStateFlow<List<Topo>>(emptyList())
    val topos: StateFlow<List<Topo>> = _topos

    init { load(force = false) }

    fun reload() = load(force = true)

    private fun load(force: Boolean) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            val area = try {
                repo.area(id, force)
            } catch (e: Exception) {
                _state.value = UiState.Error(e.describe()); return@launch
            }
            _state.value = UiState.Ready(area)
            if (area.numberTopos > 0) {
                _topos.value = try { topoRepo.toposFor(NodeKind.AREA, id, force) } catch (e: Exception) { emptyList() }
            }
        }
    }
}
