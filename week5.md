# The problem with shared state

아래 코드를 보자  
문제점이 있는데, 바로 동시성 문제이다

하나 이상의 스레드가 동시에 users 리스트에 접근하여 수정하게 된다면,  
users 는 shared state 이고 예상된 결과를 얻지 못할 수 있다

```kotlin
class UserDownloader(
    private val api: NetworkService
) {
    private val users = mutableListOf<User>()

    fun downloaded(): List<User> = users.toList()

    suspend fun fetchUser(id: Int) {
        val newUser = api.fetchUser(id)
        users.add(newUser)
    }
}
```

예를 들어 아래 코드는, 기대했던 1000000을 리턴하지 않는다

```kotlin
class FakeNetworkService : NetworkService {
    override suspend fun fetchUser(id: Int): User {
        delay(2)
        return User(id, "User$id")
    }
}
suspend fun main() {
    val downloader = UserDownloader(FakeNetworkService())
    coroutineScope {
        repeat(1_000_000) {
            downloader.fetchUser(it)
        }
    }
    println(downloader.downloaded().size)
}
```

아래와 같은 예제에서도 마찬가지로 기대 했던 결과를 볼 수 없다  
- 두 스레드가 동시에 counter 변수를 읽고 1을 더한 후 다시 변수에 값을 저장하면
- 2가 저장되어야 하지만, 실제로는 1이 저장되어 값의 오차가 생긴다

```kotlin
fun main() = runBlocking {
    massiveRun { counter++ }
    println("Counter = $counter")
}

suspend fun massiveRun(action: suspend () -> Unit) =
    withContext(Dispatchers.Default) {
        repeat(1000) {
            launch {
                repeat(1000) {
                    action()
                }
            }
        }
    }
```

## Blocking synchronization

위 문제는 Java 에서 전통적으로 사용하는 synchronized block 을 활용해서 해결 할 수 있음  

```kotlin
fun main() = runBlocking {
    val lock = Any()
    massiveRun {
        synchronized(lock) {
            counter++
        }
    }
    println("Counter = $counter")
}
```

- 그렇지만 synchronized 블록 안에서는 다른 suspend 함수를 호출 할 수 없고
- synchronized 의 경우 스레드를 블록 하기에
최선의 방법이라고 볼 수 없다

## Atomics

Java 에는 atomic value 가 있다  
이 값들의 연산은 thread-safe 한 특징이 있음
- 한마디로 값을 읽고 쓰는 것의 동시성 보장이 된다는 의미이다

이 들의 연산은 락을 사용하지 않고, low-level 에서 보장되기에 
- 효율적이고
- 코루틴의 접근방식에 적절함

```kotlin
private val atomicCounter = AtomicInteger()

fun main() = runBlocking {
    massiveRun {
        atomicCounter.incrementAndGet()
    }
    println("Counter = $atomicCounter") // 1000000
}
```

정확하게 동작하지만, 이 방법은 활용성이 떨어짐  
- 만약 다수의 operation 들의 동시성을 보장해야 한다면 활용이 어려울 수 있음

```kotlin
private var counter = AtomicInteger()

fun main() = runBlocking {
    massiveRun {
        counter.set(counter.get() + 1)
    }
    println("Counter = $atomicCounter") // ~430467
}
```

AtomicReference 를 활용해 컬렉션의 동시성을 보장할 수 있음

```kotlin
class UserDownloader(
    private val api: NetworkService
) {
    private val users = AtomicReference(listOf<User>())

    fun downloaded(): List<User> = users.get()

    suspend fun fetchUser(id: Int) {
        val newUser = api.fetchUser(id)
        users.getAndUpdate { it + newUser }
    }
}
```

## A dispatcher limited to a single thread

가장 편리한 방법은 싱글 스레드를 가지는 디스패처를 활용하는 것이다

```kotlin
val dispatcher = Dispatchers.IO
    .limitedParallelism(1)

fun main() = runBlocking {
    massiveRun {
        withContext(dispatcher) {
            counter++
        }
    }
    println(counter) // 1000000
}
```

위 방식은 2가지 접근으로 활용할 수 있음  

1. coarse-grained thread confinement
    - 이 방식은 전체 함수를 해당 디스패처를 가지는 코루틴스코프로 감싸는 것
    - 쉽고, 충돌을 방지하지만
    - 멀티스레딩의 장점을 포기함

```kotlin
class UserDownloader(
    private val api: NetworkService
) {
    private val users = mutableListOf<User>()
    private val dispatcher = Dispatcher.IO.limitedParallelism(1)

    fun downloaded(): List<User> = 
        withContext(dispatcher) {
            users.toList()
        }

    suspend fun fetchUser(id: Int) = wihtContext(dispatcher) {
        val newUser = api.fetchUser(id)
        users.add(newUser)
    }
}
```

위 코드에서, `api.fetchUser` 의 경우 멀티스레드 환경에서 동시에 실행될 수 있는 작업이다.  
하지만 현재는 싱글 스레드로 동작하여 성능 저하가 있다.
- 정리하면, blocking 되거나 cpu-intensive 한 작업이 코루틴 스코프안에 포함되면 성능저하가 있을 수 있다

2. fine-grained thread confinement
   - 이 방식은 state 를 변경하는 곳에만 코루틴 스코프를 활용한다
   - blocking 되거나 cpu-intensive 한 작업의 경우 제외할 수 있어, coarse-grained 방식보다 성능 좋음

```kotlin
class UserDownloader(
    private val api: NetworkService
) {
    private val users = mutableListOf<User>()
    private val dispatcher = Dispatcher.IO.limitedParallelism(1)

    fun downloaded(): List<User> = 
        withContext(dispatcher) {
            users.toList()
        }

    suspend fun fetchUser(id: Int) {
        val newUser = api.fetchUser(id)
        withContext(dispatcher) {
            users.add(newUser)
        }
    }
}
```

## Mutex

마지막 접근은 Mutex를 활용하는 것이다  
하나의 키를 가지는 화장실이나 방을 생각하면 된다  
- lock 을 가지는 첫 코루틴은 suspension 없이 코드 수행
- 두번째로 온 코루틴은 lock을 가질 수 없고 suspend 되었다가, 첫 코루틴이 unlock 하면 코드 수행

```kotlin
suspend fun main() = coroutineScope {
    repeat(5) {
        launch {
            delayAndPrint()
        }
    }
}

val mutex = Mutex()

suspend fun delayAndPrint() {
    mutex.lock()
    delay(1000)
    println("Done")
    mutex.unlock()
}
// (1 sec)
// Done
// (1 sec)
// Done
// (1 sec)
// Done
// (1 sec)
// Done
// (1 sec)
// Done
```

lock() 과 unlock() 을 직접적으로 사용하는 것은 위험하다  
만약 중간에 exception 이 발생한다면, unlock() 영영 호출되지 않을 수도 있기 때문이다  
그렇기에 withLock() 을 사용하는 것을 추천한다  

synchronized 블록과의 중요한 차이점이자 장점은  
- 스레드를 블로킹하지 않고, 코루틴을 서스펜드 시킨다는 점이다  
  - 이는 더 가벼운 방식임  

그렇지만 단점은  
- lock을 2번 사용할 수 없다는 점
- suspend 되었을때 unlock 하지 않는 다는 점


아래 코드는 데드락이 발생할 것이다 

```kotlin
suspend fun main() {
    val mutex = Mutex()
    println("Started")
    mutex.withLock {
        mutex.withLock{
            println("Will never be printed")
        }
    }
}
```

아래 코드는 5초정도 걸린다  
왜냐하면 delay() 동안 해당 코루틴이 lock 을 잡고 있기 때문임 

```kotlin
class MessagesRepository {
    private val messages = mutableListOf<String>()
    private val mutex = Mutex()
    suspend fun add(message: String) = mutex.withLock {
        delay(1000) // we simulate network call
        messages.add(message)
    }
}
suspend fun main() {
    val repo = MessagesRepository()
    val timeMillis = measureTimeMillis {
        coroutineScope {
            repeat(5) {
                launch {
                    repo.add("Message$it")
                }
            }
        }
    }
    println(timeMillis) // ~5120
}
```

만약 앞서 설명한 싱글스레드 디스패처 방식을 활용한다면, 이 문제는 해결 할 수 있음

## Semaphore

Mutex 와 동일하지만, 복수개의 permit 을 가질 수 있는 개념이다  
사실 동시성문제에서는 permit을 하나만 허용해야 되기에  
실제로 활용할 수 있는 예제는 rate limiting 정도 일 것이다 

---

# Testing Kotlin Coroutines



