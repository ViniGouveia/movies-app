package dev.vinigouveia.moviesapp.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel : ViewModel() {
    private val _homeUiModel = MutableStateFlow(HomeUiModel())
    val homeUiModel: StateFlow<HomeUiModel> = _homeUiModel.asStateFlow()
}
