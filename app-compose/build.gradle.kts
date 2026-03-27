import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
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
        applicationId = "com.sunmi.taplink.demo"
        minSdk = Integer.parseInt(libs.versions.minSdk.get())
        targetSdk = Integer.parseInt(libs.versions.compileSdk.get())
        versionCode = 6
        versionName = "1.0.1.${versionCode}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Keep sensitive values out of source control.
        buildConfigField("String", "PROD_CLOUD_BASE_URL", "\"${secret("PROD_CLOUD_BASE_URL", "https://open.sunbay.us")}\"")
        buildConfigField("String", "PROD_CLOUD_API_KEY", "\"${secret("PROD_CLOUD_API_KEY")}\"")
        buildConfigField("String", "PROD_CLOUD_APP_ID", "\"${secret("PROD_CLOUD_APP_ID")}\"")
        buildConfigField("String", "PROD_WEBHOOK_KEY", "\"${secret("PROD_WEBHOOK_KEY")}\"")
        buildConfigField("String", "PROD_SDK_APP_ID", "\"${secret("PROD_SDK_APP_ID")}\"")
        buildConfigField("String", "PROD_SDK_MERCHANT_ID", "\"${secret("PROD_SDK_MERCHANT_ID")}\"")
        buildConfigField("String", "PROD_SDK_AUTH_KEY", "\"${secret("PROD_SDK_AUTH_KEY")}\"")

        buildConfigField("String", "UAT_CLOUD_BASE_URL", "\"${secret("UAT_CLOUD_BASE_URL", "https://open.sunbay-uat.us")}\"")
        buildConfigField("String", "UAT_CLOUD_API_KEY", "\"${secret("UAT_CLOUD_API_KEY")}\"")
        buildConfigField("String", "UAT_CLOUD_APP_ID", "\"${secret("UAT_CLOUD_APP_ID")}\"")
        buildConfigField("String", "UAT_SDK_APP_ID", "\"${secret("UAT_SDK_APP_ID")}\"")
        buildConfigField("String", "UAT_SDK_MERCHANT_ID", "\"${secret("UAT_SDK_MERCHANT_ID")}\"")
        buildConfigField("String", "UAT_SDK_AUTH_KEY", "\"${secret("UAT_SDK_AUTH_KEY")}\"")
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

    // Do NOT use org.apache.http.legacy - we use full Apache HttpClient
    // from service module to avoid AllowAllHostnameVerifier.INSTANCE error.

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        buildConfig = true
        aidl = true  // Enable AIDL support
        compose = true  // Enable Jetpack Compose
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    packaging {
        resources {
            excludes += "/META-INF/DEPENDENCIES"
        }
    }

    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val newApkName = "TaplinkPOS-${variant.name}-${variant.versionName}.apk"
            output.outputFileName = newApkName
        }
    }
}

dependencies {
    implementation(project(":lib_service"))
    // Payment service (includes Taplink SDK)
    //    implementation("com.sunmi:sunbay-taplink-sdk-android:1.0.7.14-SNAPSHOT")
    
    // JSON processing
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("org.java-websocket:Java-WebSocket:1.5.3")
    
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
    
    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material3:material3-window-size-class:1.2.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.activity.compose)
    
    // Compose Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")
    
    // Compose ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    
    // Compose debugging
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.v112)
    androidTestImplementation(libs.androidx.espresso.core.v330)
}

