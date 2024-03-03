package coroutines.week5

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

// suspend fun main() {
//     val downloader = UserDownloader(FakeNetworkService())
//     coroutineScope {
//         repeat(100_000) {
//             downloader.fetchUser(it)
//         }
//     }
//     println(downloader.downloaded().size)
// }

// fun main() = runBlocking {
//     massiveRun { counter++ }
//     println("Counter = $counter")
// }

// fun main() = runBlocking {
//     val lock = Any()
//     massiveRun {
//         synchronized(lock) {
//             counter++
//         }
//     }
//     println("Counter = $counter")
// }

// fun main() = runBlocking {
//     massiveRun {
//         atomicCounter.incrementAndGet()
//     }
//     println("Counter = $atomicCounter")
// }

val dispatcher = Dispatchers.IO
    .limitedParallelism(1)

fun main() = runBlocking {
    massiveRun {
        withContext(dispatcher) {
            counter++
        }
    }
    println(counter)
}

var counter = 0

private val atomicCounter = AtomicInteger()

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
data class User(
    val id: Int,
    val name: String,
)
interface NetworkService {
    suspend fun fetchUser(id: Int): User
}
class FakeNetworkService : NetworkService {
    override suspend fun fetchUser(id: Int): User {
        delay(2)
        return User(id, "User$id")
    }
}
