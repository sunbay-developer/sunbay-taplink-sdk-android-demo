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
    // Taplink SDK - use the published Maven build that includes top-level tipConfig support.
//    api("com.sunmi:sunbay-taplink-sdk-android:1.0.6")
    api("com.sunmi:sunbay-taplink-sdk-android:1.0.7.13-SNAPSHOT")
//    api("com.sunmi:sunbay-taplink-sdk-android:1.0.6.15-SNAPSHOT")

    api(project(":lib_taplink_sdk"))

    // OkHttp - HTTP client (for Cloud mode API calls)
    implementation(libs.okhttp.v4120)

    // Gson removed — lib_service now uses Jackson for all JSON operations

    // Android core
    implementation(libs.androidx.core.ktx)

    // Kotlin coroutines (optional for future use)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.v112)
    androidTestImplementation(libs.androidx.espresso.core.v330)

    implementation("com.google.code.gson:gson:2.13.1")
    implementation("org.java-websocket:Java-WebSocket:1.5.3")
    implementation("com.github.mik3y:usb-serial-for-android:3.9.0")


    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")

    implementation("com.fasterxml.jackson.core:jackson-annotations:2.15.2")
}
