package coroutines

import java.util.concurrent.Executors
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

suspend fun main() {
    // resume1()
    // resume2()
    resume3()
}

suspend fun resume1() {
    println("before")
    suspendCoroutine<Unit> {  }
    println("after")
}

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




