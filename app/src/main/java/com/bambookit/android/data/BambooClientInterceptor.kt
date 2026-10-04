package com.bambookit.android.data

import com.bambookit.android.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Requests to the BambooKit API carry `X-BK-Client: android/<version>`, and every API response's
 * X-BambooKit-API header (the server's API version) is remembered for the ⓘ diagnostics.
 * Other hosts (cloud storage, GitHub) are left untouched: signed storage URLs must get exactly the signed headers.
 */
class BambooClientInterceptor(private val apiHost: () -> String? = { Config.apiUrl.toHttpUrlOrNull()?.host }) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != apiHost()) return chain.proceed(request)
        val response = chain.proceed(request.newBuilder().header(CLIENT_HEADER, CLIENT).build())
        response.header(ApiClient.API_VERSION_HEADER)?.let { Diagnostics.apiVersion = it }
        return response
    }

    companion object {
        const val CLIENT_HEADER = "X-BK-Client"
        val CLIENT = "android/${BuildConfig.VERSION_NAME}"
    }
}
