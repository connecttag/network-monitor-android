@file:Suppress("unused")

package org.connecttag.services.networkmonitor.android

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class InternetReachabilityChecker(
    private val probeUrl: String = DEFAULT_PROBE_URL,
    private val connectTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun hasInternet(): Boolean = withContext(dispatcher) {
        runCatching {
            val connection = (URL(probeUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                instanceFollowRedirects = false
                useCaches = false
            }
            connection.use {
                it.responseCode in SUCCESS_CODES
            }
        }.getOrDefault(false)
    }

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T {
        return try {
            block(this)
        } finally {
            disconnect()
        }
    }

    companion object {
        const val DEFAULT_PROBE_URL = "https://clients3.google.com/generate_204"
        const val DEFAULT_TIMEOUT_MILLIS = 1_500
        private val SUCCESS_CODES = setOf(HttpURLConnection.HTTP_NO_CONTENT, HttpURLConnection.HTTP_OK)
    }
}

