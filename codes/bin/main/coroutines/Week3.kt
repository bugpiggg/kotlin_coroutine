package coroutines

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope

suspend fun main() {
    // stopBreakingMyCoroutines()
    // supervisorJobExample()
    // supervisorJobExampleNotGood()
    // supervisorScopeExample()
    // asyncAwaitExample()
    // cancellationExample()
    // exceptionHandlerExample()
    additionalOperationsEx()
}

fun stopBreakingMyCoroutines() {
    runBlocking {
        // Don't wrap in a try-catch here. It will be ignored.
        try {
            launch {
                delay(1000)
                throw Error("Some error")
            }
        } catch (e: Throwable) { // nope, does not help here
            println("Will not be printed")
        }

        launch {
            delay(2000)
            println("Will not be printed")
        }
    }
}

fun supervisorJobExample() {
    runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        scope.launch {
            delay(1000)
            throw Error("Some error")
        }
        scope.launch {
            delay(2000)
            println("Will be printed")
        }
        delay(3000)
    }
}

fun supervisorJobExampleNotGood() {
    runBlocking {
        // Don't do that, SupervisorJob with one children
        // and no parent works similar to just Job
        launch(SupervisorJob()) { // 1
            launch {
                delay(1000)
                throw Error("Some error")
            }
            launch {
                delay(2000)
                println("Will not be printed")
            }
        }
        delay(3000)
    }
}

fun supervisorScopeExample() {
    runBlocking {
        supervisorScope {
            launch {
                delay(1000)
                throw Error("Some error")
            }
            launch {
                delay(2000)
                println("Will be printed")
            }
        }
        delay(1000)
        println("Done")
    }
    // Exception...
    // Will be printed
    // (1 sec)
    // Done
}

suspend fun asyncAwaitExample() {
    class MyException : Throwable()
    suspend fun main() = supervisorScope {
        val str1 = async<String> {
            delay(1000)
            throw MyException()
        }
        val str2 = async {
            delay(2000)
            "Text2"
        }
        try {
            println(str1.await())
        } catch (e: MyException) {
            println(e)
        }
        println(str2.await())
    }
    // MyException
    // Text2a
    main()
}

object MyNonPropagatingException : CancellationException()
suspend fun cancellationExample() {
    suspend fun main(): Unit = coroutineScope {
        launch { // 1
            launch { // 2
                delay(2000)
                println("Will not be printed")
            }
            throw MyNonPropagatingException // 3
        }
        launch { // 4
            delay(2000)
            println("Will be printed")
        }
    }
    // (2 sec)
    // Will be printed
    main()
}

fun exceptionHandlerExample() {
    fun main(): Unit = runBlocking {
        val handler =
            CoroutineExceptionHandler { ctx, exception ->
                println("Caught $exception")
            }
        val scope = CoroutineScope(SupervisorJob() + handler)
        scope.launch {
            delay(1000)
            throw Error("Some error")
        }
        scope.launch {
            delay(2000)
            println("Will be printed")
        }
        delay(3000)
    }
    // Caught java.lang.Error: Some error
    // Will be printed
    main()
}


fun additionalOperationsEx() {

    data class User(val name: String, val friendsCount: Int, val profile: String)

    suspend fun doSomething() = coroutineScope {
        val name = async {
            delay(1000)
            println("name done")
            "json"
        }
        val friendsCount = async {
            delay(2000)
            println("friendsCount done")
            100
        }
        val profile = async {
            delay(3000)
            println("profile done")
            "profile"
        }

        println("hi im here3")
        val user = User(name.await(), friendsCount.await(), profile.await())
        println("hi im here4")
    }

    fun onCreate() {
        runBlocking {
            println("hi im here1")
            doSomething()
            println("hi im here2")
        }
    }
    onCreate()
}

