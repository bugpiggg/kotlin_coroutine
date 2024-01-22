## Why Kotlin Coroutines?

아래는 안드로이드 예제코드이다.  
아래 코드의 경우 getNewsFromApi()는 block 될 것이고, 안드로이드의 특성상 (각 application은 하나의 main thread를 가짐) UI가 block 될 것이다.
```kotlin
fun onCreate() {
    val news = getNewsFromApi()
    val sortedNews = news.sortedByDescending { it.date }
    view.showNews(sortedNews)
}
```

이를 해결 하기 위해 아래와 같은 방법들이 있다.

### Thread switching
```kotlin
thread {
    val news = getNewsFromApi()
    val sortedNews = news
        .sortedByDescending { it.publishedAt }
    runOnUiThread {
        view.showNews(sortedNews)
    }
}
```
아래와 같은 단점들이 존재함
- 해당 스레드를 시작하면, 취소할 방법이 없음
- costly
- 빈번한 switching으로 인해 관리가 힘듬

### Callbacks
```kotlin
fun onCreate() {
    getNewsFromApi { news ->
        val sortedNews = news
            .sortedByDescending { it.publishedAt }
        view.showNews(sortedNews)
    }
}
```
위 코드는 취소동작을 지원하지 않지만, 구현 할 수 있다  
그렇지만 아래와 같은 단점들을 가짐
- callback hell
- 병렬적으로 수행할 수 있는 코드들도, 순차적으로 실행하게 됨(위와 같은 구조일때)

### RxJava and Other reactive streams
위 방법들이 가지는 단점들을 모두 해결할 수 있지만, 학습비용이 큼


### 그래서 코루틴을 소개한다
- 본질은 `특정시점에 코루틴을 멈추고, 나중에 다시 시작할 수 있음`

```kotlin
fun showNews() {
    viewModelScope.launch {
        val config = async { getConfigFromApi() }
        val news = async { getNewsFromApi(config.await()) }
        val user = async { getUserFromApi() }
        view.showNews(user.await(), news.await())
    }
}
```
- API가 병렬적으로 수행됨
- blocking 되지 않음
- 구조가 단순. 비동기코드와 거의 유사함
- 스레드와 비교했을때 컴퓨팅자원측면에서 유리함

--- 

## Sequence builder

코틀린에는 Sequence 라는 collection 과 유사한 기능이 있음  
List나 Set과 유사하지만 아래와 같은 특징들이 있음  
- lazily하게 연산하여, 필요한 최소한의 연산만 수행함
- 무한할 수 있음
- 메모리 효율적임


예를 들어 아래와 같은 함수는 출력이 다음과 같음
```kotlin
val seq = sequence {
    println("Generating first")
    yield(1)
    println("Generating second")
    yield(2)
    println("Generating third")
    yield(3)
    println("Done")
}
fun main() {
    for (num in seq) {
        println("The next number is $num")
    }
}

// Generating first
// The next number is 1
// Generating second
// The next number is 2
// Generating third
// The next number is 3
// Done
```

위 함수가 동작하는 순서를 보면, block 안의 코드들이 수행되다가 suspend 되고, 다시 resume되는 것을 볼 수 있음  
즉 코루틴을 활용한다는 것을 알 수 있음

추가적으로 Sequence 의 경우, block 내부에서 yield() 함수를 제외한 다른 suspend 함수는 사용하면 안됨.  
그럴 경우에는 Flow 를 활용할것을 권장

--- 

## How does suspension work?

Suspending a coroutine = 코드 수행중간에 멈춘다는 의미  
이는 게임을 하다가 중간에 저장하고 나가는 행위와 비슷함
추후 다시 게임을 하고 싶을때는 불러와서 중간부터 시작가능한것과 유사

스레드와 다르다는 것을 알아야 됨
- block 되지 않고
- 다른 컴퓨팅자원을 소모하지 않고
- 코루틴은 기존과 다른 스레드에서 재개가 가능함

### Resume

코루틴은 추후에 다룰 coroutine builder 들을 통해 시작할 수 있음.  
Suspending 함수란, 코루틴을 suspend 할 수 있는 함수를 의미함.  

아래 코드는 suspendCoroutine 함수를 이용해 중간에서 suspend 해본 코드임.  
이 경우 코드는 멈추지 않고 계속 실행됨.
```kotlin

suspend fun resume1() {
    println("before")
    suspendCoroutine<Unit> {  }
    println("after")
}
// before
```
아래 코드는 suspendCoroutine 함수 블록에 출력함수를 추가한 것.  
결과를 보면 알 수 있듯이 블록 안 코드는 suspension 전에 수행됨.  
인자로 Continuation 을 볼 수 있는데, 이 인자를 활용해 resume 할 수 있음. (e.g. continuation.resume(Unit))  
suspension 전에 continuation 을 조작할 수 있는 이유는, 추후 resume 할 함수나 코드조각에서 continuation을 인자로 받아 재개하기 위함임.
```kotlin

suspend fun resume1() {
    println("before")
    suspendCoroutine<Unit> { continuation ->
        println("before too")
    }
    println("after")
}
// before
// before too
```

그래서 아래와 같이 바로 재개하게 되면 after 문구까지 출력되는 것을 볼 수 있음
```kotlin

suspend fun resume1() {
    println("before")
    suspendCoroutine<Unit> { continuation ->
        continuation.resume(Unit)
    }
    println("after")
}
// before
// after
```
suspendCoroutine 안에서 다른 스레드를 호출 할 수도 있음  
아래 코드의 아쉬운 점은, sleep을 위해 스레드를 생성해야 한다는 점
```kotlin
suspend fun resume2() {
    println("before")
    suspendCoroutine<Unit> { continuation ->
        thread {
            println("suspended")
            Thread.sleep(1000)
            continuation.resume(Unit)
            println("resumed")
        }
    }
    println("after")
}
```

그래서 아래와 같이 개선할 수 있음  
매번 스레드를 새로 생성하는 것이 아닌 스레드 개수 1인 스레드 풀에서 스레드 가져와서 실행하는 방식  
책에는 언급하지 않지만, 아래 구현의 문제점은 한 번에 하나의 예약된 작업만 수행할 수 있다는 것...
```kotlin
private val executor = Executors.newSingleThreadScheduledExecutor {
    Thread(it, "myThread").apply { isDaemon = true }
}

suspend fun resume3() {
    println("before")

    suspendCoroutine<Unit> { continuation ->
        executor.schedule({
            continuation.resume(Unit)
        }, 1000, java.util.concurrent.TimeUnit.MILLISECONDS)
    }
    println("after")
}
```

### Resuming with a value

위에서는 suspendCoroutine 을 사용할 때, resume() 함수에 Unit 을 넘겼음.    
Type argument에 다른 타입을 지졍하여, 다른 타입의 값을 반환하게 할 수 있음.  

사실 앞서 들었던 게임 비유를 생각해보면, 이와 같이 어떤 input을 넣어 게임을 재개하는 경우는 없음  
그러나 코루틴에서는 당연한 개념. 왜냐하면 코루틴은 오래 걸리는 작업을 통해 무엇인가의 리턴값을 얻기 위함인 경우가 많기 때문  

### Resume with an exception

resumeWithException()을 활용하여 suspension 포인트에서 exception 이 발생하도록 할 수 있음  

### Suspending a coroutine, not a function

우리는 함수를 suspend 하는게 아니고, 코루틴을 suspend 하는 거임.
주목할점은, suspending function은 코루틴이 아니고 코루틴을 suspend 할 수 있는 함수임.

---

## Coroutines under the hood

## Coroutines: built-in support vs library
