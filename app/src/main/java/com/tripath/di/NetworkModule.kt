package com.tripath.di

import com.tripath.BuildConfig
import com.tripath.data.remote.openfoodfacts.OpenFoodFactsApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** Networking for the barcode-scan food lookup — the app's only network dependency. */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val OPEN_FOOD_FACTS_BASE_URL = "https://world.openfoodfacts.org/"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Identifies the app to Open Food Facts.
     *
     * They ask API consumers to say who they are, and throttle or block generic agents — an
     * anonymous OkHttp default is the kind of thing that works in testing and then quietly stops
     * working. Deliberately carries no contact details: nothing about the person using the app needs
     * to leave the device to look up a barcode.
     */
    private const val USER_AGENT = "TriPath/${BuildConfig.VERSION_NAME} (Android)"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build()
                )
            }
            .build()

    @Provides
    @Singleton
    fun provideOpenFoodFactsApi(client: OkHttpClient): OpenFoodFactsApi =
        Retrofit.Builder()
            .baseUrl(OPEN_FOOD_FACTS_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenFoodFactsApi::class.java)
}
