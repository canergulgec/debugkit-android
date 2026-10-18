// Release-build stand-in for :core. Mirrors the public API that host apps call from
// their main source set, with empty bodies and no third-party dependencies, so release
// APKs compile unchanged but carry none of the debugger.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "dev.debugkit"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    explicitApi()
}

dependencies {
    // Host apps that use DebugKitInterceptor or DebugKitTree already ship OkHttp or Timber;
    // compileOnly keeps :noop from adding or upgrading them in their release builds.
    compileOnly(libs.okhttp)
    compileOnly(libs.timber)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            artifactId = "noop"
            afterEvaluate { from(components["release"]) }
        }
    }
}
