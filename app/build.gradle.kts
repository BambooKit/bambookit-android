import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// BambooKit configuration comes from local.properties (gitignored) or -P gradle properties.
// The Supabase anon key is a public client key; never put a service-role key here.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun bkProp(name: String, default: String = ""): String =
    (project.findProperty(name) as String?) ?: localProps.getProperty(name) ?: default

android {
    namespace = "com.bambookit.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bambookit.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // GitHub repository whose Releases provide app updates (release builds only).
        buildConfigField("String", "UPDATE_REPO", "\"${bkProp("bambookit.updateRepo", "BambooKit/bambookit-android")}\"")
        buildConfigField("String", "SUPABASE_URL", "\"${bkProp("bambookit.supabaseUrl")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${bkProp("bambookit.supabaseAnonKey")}\"")
        manifestPlaceholders["authScheme"] = "bambookit"
    }

    // Release APKs are signed with the BambooKit release key so every update installs over the previous
    // version. The key lives outside the repo; its location and passwords come from ~/.gradle/gradle.properties
    // (BAMBOOKIT_RELEASE_STORE_FILE, BAMBOOKIT_RELEASE_STORE_PASSWORD, BAMBOOKIT_RELEASE_KEY_ALIAS, BAMBOOKIT_RELEASE_KEY_PASSWORD).
    val releaseStore = providers.gradleProperty("BAMBOOKIT_RELEASE_STORE_FILE").orNull
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = providers.gradleProperty("BAMBOOKIT_RELEASE_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("BAMBOOKIT_RELEASE_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("BAMBOOKIT_RELEASE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            if (releaseStore != null) signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "API_URL", "\"${bkProp("bambookit.apiUrl.release", "https://bambookit-api.onrender.com")}\"")
            manifestPlaceholders["cleartext"] = "false"
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Hosted API by default. For a local API over USB set bambookit.apiUrl.debug=http://127.0.0.1:8080
            // in local.properties (start-bambookit.ps1 keeps `adb reverse tcp:8080 tcp:8080` applied).
            buildConfigField("String", "API_URL", "\"${bkProp("bambookit.apiUrl.debug", "https://bambookit-api.onrender.com")}\"")
            manifestPlaceholders["cleartext"] = "true"
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// BambooRepositoryTest.kt tests a demo repository that was removed in 1.0.1 and no longer compiles.
// It is left in place (not deleted) and skipped here until someone removes it.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    if (name.contains("UnitTest")) exclude("**/BambooRepositoryTest.kt")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.sse)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.browser)
    implementation(libs.zxing.embedded)
    implementation(libs.zxing.core)
    // App lock: BiometricPrompt (fingerprint/face or the phone's PIN/pattern). Needs a FragmentActivity and an
    // AppCompat theme (the pre-Android 9 fingerprint dialog is an AppCompat dialog).
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.appcompat)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

