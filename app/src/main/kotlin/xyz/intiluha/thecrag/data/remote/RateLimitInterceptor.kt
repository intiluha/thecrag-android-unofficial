package xyz.intiluha.thecrag.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Token bucket: at most [burst] requests at once, refilled every [intervalMs].
 * Keeps one install far below theCrag's "<10 calls per 10 s" partner guideline.
 */
class RateLimitInterceptor(
    private val burst: Int = 3,
    private val intervalMs: Long = 700,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = Thread::sleep,
) : Interceptor {
    private val lock = Object()
    private var tokens = burst.toDouble()
    private var last = clock()

    override fun intercept(chain: Interceptor.Chain): Response {
        acquire()
        return chain.proceed(chain.request())
    }

    private fun acquire() {
        synchronized(lock) {
            while (true) {
                val now = clock()
                tokens = minOf(burst.toDouble(), tokens + (now - last).toDouble() / intervalMs)
                last = now
                if (tokens >= 1.0) { tokens -= 1.0; return }
                val wait = ((1.0 - tokens) * intervalMs).toLong().coerceAtLeast(1)
                sleep(wait)
            }
        }
    }
}
