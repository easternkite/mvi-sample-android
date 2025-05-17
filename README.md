# mvi-sample-android
> 단방향, 양방향과 같은 추상적인 말보다는 납득할 수 있을만한 용어를 사용하였습니다.


## MVVM에서 있었던 일 
MVVM에서는 VM에서 View와 Model간의 중간 다리 역할을 하며, Model을 통해 불러온 데이터를 ViewState로 변환하여 View에 구독가능한 형태로 제공합니다. 

1. MVVM도 함수형태로 하나의 액션을 VM에게 요청합니다. 
2. VM은 Model에게 의도한 데이터를 받아오고 
3. Observable 형태로 ViewState로 변환하여 View에게 최종 전달합니다. 

사실 이부분만 봐도 MVVM이랑 MVI랑 크게 다르지않습니다. MVI는 Model에 원하는 데이터를 가져올 때, 사용자의 의도(Intent)를 Action형태로 넘긴다는 듯한 의미로 깨달았었지만, 이건 이미 MVVM에도 있었던 개념이죠. 

### 그럼 뭐가 다를까? 
MVI에는 사실 숨은 의미가 있습니다. 그중 하나는 사용자의 의도를 여러개의 부수효과(Side-Effect)단위로 쪼개어 처리하는 기법이 숨어있습니다. 

기존 MVVM에서 ViewState로 업데이트하는 과정을 살펴보면, 데이터에 상태를 맞춰서 개발하는 과정이 있었습니다. 
1. Data 요청 
2. 데이터가 들어왔으면? (Loading 끄고, error메세지 초기화시키고, 데이터를 할당합니다.) 
3. 데이터 요청 실패했으면? (Loading 끄고, error메세지 할당하고, 데이터 초기화시킵니다.) 

코드로 나타내면 다음과 같습니다.
```kotlin 
fun fetchData() { 
    uiState.update { it.copy(isLoading = true) } 
    val response = getDataUseCase() 
    if (response.isSuccess) { 
        uiState.update { it.copy(isLoading = false, isError = null, data = response.data) } 
    } else {
        uiState.update { it.copy(isLoading = false, isError = response.errorMessage, data = null) } 
    }
}
```


데이터 유무에 맞춰서 상태값을 한번에 갱신합니다. 이게 물론 시스템에 영향을 줄만큼 안티패턴이라는 말은 아닙니다. 하지만 하나의 데이터에 맞춰서 갱신해야하는 상태가 많아지면? 거의 새로운 데이터를 받아오고 State로 변환하려고 할 때마다 **빡센 청기백기 게임**을 해야합니다.

## MVI 는 어떨까?
MVI는 하나의 액션을 부수적인 이벤트, 즉 세부적인 작업 단위로 분리하여 처리합니다. 
이걸 통용적으로 Side-Effect(부수 효과)라고 합니다.

예를들어, 하나의 데이터를 불러오기 위해서 사용되는 부수적인 이벤트는 다음과 같습니다.
1. 로딩 켜기 
2. 데이터 불러오기
	1. 성공했으면? 데이터 매핑하기
	2. 실패했으면? 에러 메시지 띄우기
3. 로딩 끄기
4. 토스트 띄우기 

다음과 같이 크게 4가지의 부수효과를 만들어낼 수 있습니다.
코드로 나타내면 다음과 같습니다.

```kotlin
fun handleFetchAction() = flow<SideEffect> {
    emit(Loading(true))
    getDataUseCase()
        .onSuccess { emit(DataFetched(it.data)) }
        .onFailure { emit(Error(it.message)) }
    emit(Loading(false))
    emit(ShowToast("data fetching has been finished."))
}
```

이렇게 되면 하나의 Action에 대해서 처리해야하는 부수효과에 대해서, 명시적으로 지정하고, 개별적으로 처리할 수 있습니다.

이렇게 되면 뭐가좋을까? 
* 개발자 실수로 인한 휴먼에러 방지
* 가독성(기획서 등에 명세되어있는 예측가능한 동작을 명시적으로 확인 가능)
* 각각의 Effect별 테스트코드 작성에 유리하다.
* ~~청기백기의 간소화~~

## 세팅
그렇다면 어떻게 세팅하면 좋을지 생각해봅시다.

**결과적으로 기존의 ViewModel을 커스텀하여, Action을 여러개의 부수효과로서 방출하고, 이 각각의 부수효과들을 하나의 상태로 누적합하는 과정이 필요합니다.**

이 내용을 코드로 나타낸다면 다음과 같습니다. 하나의 액션에 대해서 1회성 invoke 를 보장하기 위해서 Channel을 사용합니다.
```kotlin
val state = actionChannel  
    .receiveAsFlow()  
    .flatMapConcat(::onAction) // Action -> 부수효과 분리
    .onEach(effectChannel::send) // 부수효과 방출
    .runningFold(initialState, ::reduce) // 부수효과들 -> 하나의 상태로 누적합
    .stateIn(viewModelScope, SharingStarted.Companion.Eagerly, initialState) // UI에서 사용할 HotFlow 변환
```

전체 코드를 보면 다음과 같습니다.
```kotlin
abstract class MviViewModel<ACTION : UiAction, STATE : UiState, EFFECT : SideEffect>(  
    initialState: STATE  
): ViewModel() {  
    private val actionChannel = Channel<ACTION>(Channel.Factory.BUFFERED)  
    private val effectChannel = Channel<EFFECT>(Channel.Factory.BUFFERED)  
  
    val effect = effectChannel  
        .receiveAsFlow()  
  
    val state = actionChannel  
        .receiveAsFlow()  
        .flatMapConcat(::onAction)  
        .onEach(effectChannel::send)  
        .runningFold(initialState, ::reduce)  
        .stateIn(viewModelScope, SharingStarted.Companion.Eagerly, initialState)  
  
    fun send(action: ACTION) {  
        viewModelScope.launch {  
            actionChannel.send(action)  
        }  
    }  
  
    abstract fun onAction(action: ACTION): Flow<EFFECT>  
    abstract fun reduce(prevState: STATE, currentEvent: EFFECT): STATE  
}
```

`UiAction`, `SideEffect`, `UiState` 타입은 각각 사용자의 액션, 부수효과, Ui 요소의 상태값을 의미하는 인터페이스입니다. 이는 사용자가 상속받아서 커스텀해주어야합니다.

```kotlin
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

```

이제 개발자는 `onAction()`, `reduce()`를 오버라이딩하여 각각의 동작을 유도해주면 됩니다. 

```kotlin
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
```

이렇게 되면, 실제 UI상태값은 `ExampleUiState`형태로 방출하게 되고, Toast 트리거와같은 부수효과는 `ExampleSideEffect`형태로 방출하게 됩니다.

이제 이를 사용하는 예시를 Compose코드로 작성하면 다음과 같습니다.
```kotlin
@Composable  
fun ExampleScreen(  
    modifier: Modifier = Modifier,  
    viewModel: ExampleViewModel = viewModel()  
) {  
    val state by viewModel.state.collectAsState()  
    val context = LocalContext.current  
    LaunchedEffect(Unit) {  
        viewModel.effect.collect {  
            when(it) {  
                is ExampleEvent.ShowToast -> {  
                    Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()  
                }  
                else -> {}  
            }  
        }  
    }  
    Box(modifier) {  
        Column {  
            Button(onClick = { viewModel.send(ExampleAction.FetchData("https://www.naver.com")) }) {  
                Text("Fetch Data")  
            }  
            Text(text = state.data)  
        }  
  
        if(state.isLoading) {  
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))  
        }  
    }  
}
```

위 코드대로 버튼을 누르면, `Action.FetchData` 를 Channel에 보내지게 되고, onAction으로 인해서 여러개의 부수효과로 나눠지며, 최종적으로 Loading상태, Toast상태 등의 UI변경을 명시적으로 제어할 수 있게됩니다.


## 마치며
정리해보자면, 앱개발에서 트랜디한 MVI패턴이라고 해서 만능도 아니고, 필수도 아닙니다.
상태처리가 적은 화면구조에서는 오히려 MVI가 귀찮고, 그저 귀찮은 반복작업처럼 느껴질 수도 있습니다.

하지만 화면에서 일어나는 복잡한 동작들을 한곳에 모아서, 명시적으로 처리하고싶을 때 고려해보면 만족도가 높은 패턴입니다. 

MVC에서 VC의 역할이 가중됨에 따라 MVVM을 선택했듯, MVVM의 상태관리가 가중됨에 따라 MVI를 도입해보는건 어떨까요?
