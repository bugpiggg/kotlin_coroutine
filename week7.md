## Hot and Cold data sources

우선 hot 과 cold 를 비교해보자

[hot]
- Collection(List, Set)
- Channel

[cold]
- Sequence, Stream
- Flow, RxJava streams

### Hot vs Cold

- hot 은 eager, 즉 요소들의 consumption 과 상관없이 생산하고 저장한다
  - 언제든지 사용될 준비가 되어 있음
  - 여러번 사용될 때, 재계산 할 필요가 없음 
- cold 는 lazy, 그들의 필요에 따라 생산하고 저장하지 않는다
  - 무한할수 있음
  - 최소한의 연산만 수행
  - 메모리를 적게 사용함
  
### Hot channels, cold flow
 
아래와 같이 channel 과 flow 를 builder 를 이용해 생성할 수 있음 

```kotlin
val channel = produce {
  while(true) {
    val x = computerNextValue()
    send(x)
  }
}

val flow = flow {
  while (true) {
    val x = computeNextValue()
    emit(x)
  }
}

```

채널은
- hot
- 바로 데이터를 계산 하기 시작함
- 다른 코루틴에서 계산이 시작됨
- 그렇기에 produce 는 코루틴 빌더인 거임

플로우는
- cold
- 데이터의 생성은 필요할 때 일어남
- 그렇기에 빌더가 아님
- termianl operation 이 사용되면 데이터 생성 시작 
- 같은 코루틴에서 시작함


## Flow Introduction

플로우는 아래의 Iterable 이나 Sequence 와 같이 결과를 모으는 collect 함수만 허용함 

```kotlin
interface Flow<out T> {
  suspend fun collect(collector: FlowCollector<T>)
}
interface Iterable<out T> {
  operator fun iterator(): Iterator<T>
}
interface Sequence<out T> {
  operator fun iterator(): Iterator<T>
}
```

### compare flow to other ways of representing values
- list나 set 같은 경우는, 모든 것들이 계산된 컬렉션이다
  - 만약 각 요소들이 계산되는 데 시간이 걸린다면, 모든 요소들이 계산되는 데 드는 시간 만큼 기다려야 한다 
  
- sequence 를 사용하면, 요소가 하나 계산되는 순간 바로 사용할 수 있다 
  -  cpu intensive 한 연산이나, 블로킹 되는 연산인 경우 유용함(효율적인 계산 방식 때문에)
  -  그러나 terminal operation이 suspending 하지 않다는 단점이 있음
     - 그래서 sequence 내에서의 suspending 함수는 블로킹 됨...

- 위 방법들은 모두 각자의 결점이 있기에, flow가 생겼다
  - flow는 코루틴을 사용하여 데이터를 생성하는 곳에 사용해야 한다

### The characteristics of Flow

플로우의 terminal operation 은 스레드를 블로킹 하는 대신, 코루틴을 서스펜드 시킴  
그 외에도, 코루틴의 다른 기능을 활용 할 수 있음(코루틴 컨택스트 반영이나 예외처리)

### Flow nomenclature

- flow는 시작점이 필요함. 대부분 flow builder 를 이용함 
- terminal operation 도 필요함. collect 가 있음
- 중간 단계인 intermediate operation 들도 있음

### Real-life use cases
- server-sent events. 예를 들어 websocket, rsocket, notification 등등
- user의 변화를 감지했을때
- 센서나 장치로 부터 어떤 정보를 업데이트 받았을 때
- DB 의 변화를 감지 했을때