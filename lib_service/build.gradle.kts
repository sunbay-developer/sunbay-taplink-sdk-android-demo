plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.sunmi.tapro.taplink.demo.service"
    compileSdk = Integer.parseInt(libs.versions.compileSdk.get())

    defaultConfig {
        minSdk = Integer.parseInt(libs.versions.minSdk.get())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    // Taplink SDK - shared AAR from root directory
//    api(files("../sunbay-taplink-sdk-android-1.0.7.19-release.aar"))
    api("com.sunmi:sunbay-taplink-sdk-android:1.0.5.1-SNAPSHOT")
//    api("com.sunmi:sunbay-taplink-sdk-android:1.0.5")
    // WebSocket client - required by Taplink SDK for LAN mode communication
    // AAR does not bundle transitive dependencies, so this must be declared explicitly
    api("org.java-websocket:Java-WebSocket:1.5.3")

    // USB serial (VSP cable path) — SDK uses UsbSerialProber; not bundled in AAR. Requires jitpack.io in settings.
    api("com.github.mik3y:usb-serial-for-android:3.9.0")
//    implementation("com.sunmi:sunbay-taplink-sdk-android:1.0.1")

    // OkHttp - HTTP client (for Cloud mode API calls)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Gson - JSON serialization (for Cloud mode API calls)
    implementation("com.google.code.gson:gson:2.13.1")

    // Android core
    implementation(libs.androidx.core.ktx)

    // Kotlin coroutines (optional for future use)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.v112)
    androidTestImplementation(libs.androidx.espresso.core.v330)
}
