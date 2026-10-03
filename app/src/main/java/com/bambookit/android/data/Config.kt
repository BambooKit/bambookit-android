package com.bambookit.android.data

import com.bambookit.android.BuildConfig

/** Build-time configuration (see app/build.gradle.kts). Nothing here is a secret. */
object Config {
    val apiUrl: String = BuildConfig.API_URL.trimEnd('/')
    val supabaseUrl: String = BuildConfig.SUPABASE_URL.trimEnd('/')
    val supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY
    val authConfigured: Boolean get() = supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()
}
