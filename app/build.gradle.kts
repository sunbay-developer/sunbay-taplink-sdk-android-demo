import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

fun secret(name: String, defaultValue: String = ""): String {
    val localSecretsFile = rootProject.file("local.secrets.properties")
    val localSecrets = Properties().apply {
        if (localSecretsFile.exists()) {
            localSecretsFile.inputStream().use { load(it) }
        }
    }

    val envValue = System.getenv(name)
    if (!envValue.isNullOrBlank()) return envValue

    val fileValue = localSecrets.getProperty(name)
    if (!fileValue.isNullOrBlank()) return fileValue

    val propValue = providers.gradleProperty(name).orNull
    return propValue?.takeIf { it.isNotBlank() } ?: defaultValue
}

android {
    namespace = "com.sunmi.tapro.taplink.demo"
    compileSdk = Integer.parseInt(libs.versions.compileSdk.get())

    defaultConfig {
        applicationId = "com.sunmi.tapro.taplink.demo"
        minSdk = Integer.parseInt(libs.versions.minSdk.get())
        targetSdk = Integer.parseInt(libs.versions.compileSdk.get())
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "APP_TAPLINK_APP_ID", "\"${secret("APP_TAPLINK_APP_ID")}\"")
        buildConfigField("String", "APP_TAPLINK_MERCHANT_ID", "\"${secret("APP_TAPLINK_MERCHANT_ID")}\"")
        buildConfigField("String", "APP_TAPLINK_SECRET_KEY", "\"${secret("APP_TAPLINK_SECRET_KEY")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        buildConfig = true
        aidl = true  // Enable AIDL support
    }

    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val newApkName = "TaplinkDemo-${variant.name}-${variant.versionName}.apk"
            output.outputFileName = newApkName
        }
    }
}

dependencies {
    implementation(project(":lib_service"))
    // Payment service (includes Taplink SDK)
    //    implementation("com.sunmi:sunbay-taplink-sdk-android:1.0.7.13-SNAPSHOT")
    
    // JSON processing
//    implementation("com.google.code.gson:gson:2.13.1")
//    implementation("org.java-websocket:Java-WebSocket:1.5.3")
    // Android core libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    
    // Kotlin coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    
    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.billing)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.v112)
    androidTestImplementation(libs.androidx.espresso.core.v330)
}

