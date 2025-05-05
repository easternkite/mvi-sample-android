package com.example.sample_mvi.features.example

import com.example.sample_mvi.core.common.mvi.SideEffect
import com.example.sample_mvi.core.common.mvi.UiAction
import com.example.sample_mvi.core.common.mvi.UiState

data class ExampleUiState(
    val isLoading: Boolean = false,
    val data: String = "",
    val errorMessage: String = "",
) : UiState

sealed interface ExampleEvent : SideEffect {
    data class Loading(val isLoading: Boolean) : ExampleEvent
    data class RequestData(val data: String) : ExampleEvent
    data class Error(val message: String) : ExampleEvent
    data class ShowToast(val message: String) : ExampleEvent
}

sealed interface ExampleAction : UiAction {
    data class FetchData(val url: String) : ExampleAction
    data class ShowToast(val message: String) : ExampleAction
}
