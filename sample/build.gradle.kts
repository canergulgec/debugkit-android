plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.debugkit.sample"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.debugkit.sample"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // The setup every host app uses: the real debugger in debug, the no-op in release.
    debugImplementation(projects.core)
    debugImplementation(projects.network)
    debugImplementation(projects.logs)
    debugImplementation(projects.layers)
    releaseImplementation(projects.noop)

    implementation(libs.okhttp)
    implementation(libs.timber)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
