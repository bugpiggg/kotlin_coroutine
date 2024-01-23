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

코루틴의 내부 동작에 대해 알아보자

### Contionuation-passing style

코틀린에서는 continuation-passing style 을 활용하여 코루틴을 구현함  


예를 들어 아래 3개 함수는
```kotlin
suspend fun getUser(): User?
suspend fun setUser(user: User)
suspend fun checkAvailability(flight: Flight): Boolean
```
내부적으로 아래와 같이 구현됨
```kotlin
fun getUser(continuation: Continuation<*>): Any?
fun setUser(user: User, continuation: Continuation<*>): Any
fun checkAvailability(
    flight: Flight,
    continuation: Continuation<*>
): Any
```
리턴 타입의 경우 Any 혹은 Any? 인데 이는 위 함수의 리턴 값으로 COROUTINE_SUSPENDED 라는 값이 추가적으로 반환될 수 있게 변환되기 때문임.

### A very simple function
예를 들어 아래 함수가 있다고 하자
```kotlin
suspend fun myFunction() {
    println("before")
    delay(100)
    println("after")
}
```
이 함수는 아래와 같이 내부적으로 변환됨.  
1. 우선 인자의 continuation 은 내부 상태를 저장하기 위해 함수만의 Continuation 으로 감싸줌
2. 함수는 맨 처음과, suspension 후 시점에서 호출될 수 있기에 이를 구분하기 위한 label 추가
3. 마지막으로, suspension 될 때 suspend 함수는 COROUTINE_SUSPENDED 를 반환함. 이는 상위 호출함수들로 전파되고 스레드 점유하던 것을 릴리즈함
> 여기서 조금 의문인 점은, suspension 된 후 다시 위 함수를 호출하는(resume 되는) 순간을 위해 다른 스레드에서 동작을 계속 수행하고 있는 것이 아닌가 의문이 들었음.... 추후 설명하는 부분이 있겠지?

```kotlin
fun myFunction(continuation: Continuation<Unit>): Any {
    val continuation = continuation as? MyFunctionContinuation
        ?: MyFunctionContinuation(continuation)
    if (continuation.label == 0) {
        println("Before")
        continuation.label = 1
        if (delay(1000, continuation) == COROUTINE_SUSPENDED){
            return COROUTINE_SUSPENDED
        }
    }
    if (continuation.label == 1) {
        println("After")
        return Unit
    }
    error("Impossible")
}

class MyFunctionContinuation(
    val completion: Continuation<Unit>
) : Continuation<Unit> {
    override val context: CoroutineContext
        get() = completion.context
    var label = 0
    var result: Result<Any>? = null
    override fun resumeWith(result: Result<Unit>) {
        this.result = result
        val res = try {
            val r = myFunction(this)
            if (r == COROUTINE_SUSPENDED) return
            Result.success(r as Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
        completion.resumeWith(res)
    }
}
```
만약 suspend 함수 내부적으로 지역변수가 존재한다면, 이는 continuation 에서 필드로 가지고 있음.  
만약 suspend 함수가 인자를 가지고 있다면, 이도 마찬가지로 continuation 에서 필드로 가지고 있음.


### The call stack

suspend 할 때, 스레드를 릴리즈 함. 이때 call stack 도 같이 비워 짐  
재개를 위해서는 call stack이 가지고 있는 정보들을 어딘가에 가지고 있어야 함. 이때 continuation 이 사용 됨.  
마치 huge onion 처럼 continuation 내부에 다른 상위호출함수의 continuation 을 가지기에 call stack과 유사한 기능을 할 수 있음.  

```kotlin
override fun resumeWith(result: Result<String>) {
    this.result = result
    val res = try {
        val r = printUser(token, this)
        if (r == COROUTINE_SUSPENDED) return
        Result.success(r as Unit)
    } catch (e: Throwable) {
        Result.failure(e)
    }
    completion.resumeWith(res)
}
```
실제 구현에서는 위와 같은 재귀 대신, while문을 이용한 최적화가 적용되어 있다

--- 

## Coroutines: built-in support vs library

코틀린은 언어에서 제공하는 built-in support와 kotlinx.cortouines library 2개로 나뉨
- built-in support의 경우 자유도가 높지만 편리하게 활용하기는 어려움
- 라이브러리의 경우 활용하기 쉽고, 개발자에게 구체적인 concurrence style 을 제공함

