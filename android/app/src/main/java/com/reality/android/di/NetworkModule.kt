package com.reality.android.di

import com.reality.android.BuildConfig
import com.reality.android.core.network.BackendUrlInterceptor
import com.reality.android.core.network.AuthInterceptor
import com.reality.android.data.remote.api.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun okHttp(urlInterceptor: BackendUrlInterceptor, authInterceptor: AuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(urlInterceptor)
            .addInterceptor(authInterceptor)
            .apply {
                if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().apply {
                    redactHeader("Authorization")
                    level = HttpLoggingInterceptor.Level.BASIC
                })
            }
            .build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        // Never contacted: the interceptor replaces this root with validated app settings.
        .baseUrl("https://reality.invalid/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides fun activityApi(retrofit: Retrofit): ActivityApi = retrofit.create(ActivityApi::class.java)
    @Provides fun sessionApi(retrofit: Retrofit): SessionApi = retrofit.create(SessionApi::class.java)
    @Provides fun breakApi(retrofit: Retrofit): SessionBreakApi = retrofit.create(SessionBreakApi::class.java)
    @Provides fun dailyProgressApi(retrofit: Retrofit): DailyProgressApi = retrofit.create(DailyProgressApi::class.java)
    @Provides fun streakApi(retrofit: Retrofit): StreakApi = retrofit.create(StreakApi::class.java)
    @Provides fun reportApi(retrofit: Retrofit): ReportApi = retrofit.create(ReportApi::class.java)
    @Provides fun authApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)
}
