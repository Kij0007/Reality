package com.reality.android.core.network

import com.reality.android.data.repository.SettingsRepository
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class BackendUrlInterceptor @Inject constructor(private val settings: SettingsRepository) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // OkHttp runs interceptors on its worker thread. Wait for disk settings once so
        // a restored URL cannot accidentally send the first request to the default server.
        runBlocking { settings.awaitLoaded() }
        val request = chain.request()
        val url = try {
            resolveBackendUrl(settings.currentSettings.value.backendUrl, request.url)
        } catch (error: IllegalArgumentException) {
            throw IOException("Invalid Reality backend configuration", error)
        }
        return chain.proceed(request.newBuilder().url(url).build())
    }
}
