package coroutines

import java.util.concurrent.Executors
import kotlin.concurrent.thread
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
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




private const val COROUTINE_SUSPENDED = "COROUTINE_SUSPENDED"

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

fun delay(i: Int, continuation: MyFunctionContinuation): Any {
    TODO("Not yet implemented")
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




