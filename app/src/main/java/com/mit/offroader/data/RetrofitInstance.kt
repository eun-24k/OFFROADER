package com.mit.offroader.data

import com.mit.offroader.data.api.AiApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {
    // 날씨 API:

    // 네이버 지도 API:

    // 챗GPT API:
    private const val AI_BASE_URL = "https://api.openai.com/v1/"
    val aiApi: AiApi by lazy { retrofit(AI_BASE_URL).create(AiApi::class.java) }

    // VWORLD API:

    private fun retrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder().baseUrl(baseUrl).client(
            OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }).build()
        ).addConverterFactory(GsonConverterFactory.create()).build()

    }
}