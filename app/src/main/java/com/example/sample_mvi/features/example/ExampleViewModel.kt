package com.example.sample_mvi.features.example

import com.example.sample_mvi.core.common.mvi.MviViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ExampleViewModel() : MviViewModel<ExampleAction, ExampleUiState, ExampleEvent>(ExampleUiState()) {
    override fun onAction(action: ExampleAction): Flow<ExampleEvent> {
        return when(action) {
            is ExampleAction.FetchData -> flow {
                emit(ExampleEvent.Loading(true))
                delay(2000L)
                emit(ExampleEvent.RequestData(action.url))
                emit(ExampleEvent.Loading(false))
                emit(ExampleEvent.ShowToast("Data fetched successfully!"))
            }
            is ExampleAction.ShowToast -> flow {
                emit(ExampleEvent.ShowToast(action.message))
            }
        }
    }

    override fun reduce(
        prevState: ExampleUiState,
        currentEvent: ExampleEvent
    ): ExampleUiState {
        return when(currentEvent) {
            is ExampleEvent.Loading -> prevState.copy(isLoading = currentEvent.isLoading)
            is ExampleEvent.RequestData -> prevState.copy(data = currentEvent.data)
            is ExampleEvent.Error -> prevState.copy(errorMessage = currentEvent.message)
            else -> prevState
        }
    }
}