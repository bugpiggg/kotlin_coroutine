package coroutines

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

class MyCustomContext : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> = Key
    companion object Key :
        CoroutineContext.Key<MyCustomContext>
}

class CounterContext(
    private val name: String
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> = Key
    companion object Key :CoroutineContext.Key<CounterContext>

    private var nextNumber = 0

    fun printNext() {
        println("$name: $nextNumber")
        nextNumber++
    }
}

suspend fun printNext() {
    coroutineContext[CounterContext]?.printNext()
}

suspend fun main(): Unit =
    withContext(CounterContext("Outer")) {
        printNext() // Outer: 0
        launch {
            printNext() // Outer: 1
            launch {
                printNext() // Outer: 2
            }
            launch(CounterContext("Inner")) {
                printNext() // Inner: 0
                printNext() // Inner: 1
                launch {
                    printNext() // Inner: 2
                }
            }
        }
        printNext() // Outer: 3
    }




abstract class UuidProviderContext : CoroutineContext.Element {
    abstract fun nextUuid(): String
    override val key: CoroutineContext.Key<*> = Key
    companion object Key : CoroutineContext.Key<UuidProviderContext>
}

class RealUuidProviderContext : UuidProviderContext() {
    override fun nextUuid(): String =
        UUID.randomUUID().toString()
}
class FakeUuidProviderContext(
    private val fakeUuid: String
) : UuidProviderContext() {
    override fun nextUuid(): String = fakeUuid
}


data class User(val id: String, val name: String)

suspend fun main2() {
    // production case
    withContext(RealUuidProviderContext()) {
        println(makeUser("Michał")) // e.g. User(id=d260482a-..., name=Michał)
    }

    // test case
    withContext(FakeUuidProviderContext("FAKE_UUID")) {
        val user = makeUser("Michał")
        println(user) // User(id=FAKE_UUID, name=Michał)
    }
}

// function under test
suspend fun makeUser(name: String) = User(
    id = nextUuid(),
    name = name
)

suspend fun nextUuid(): String =
    checkNotNull(coroutineContext[UuidProviderContext]) {
        "UuidProviderContext not present"
    }.nextUuid()

