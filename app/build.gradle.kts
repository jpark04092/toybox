plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

fun String.toPositiveVersionCodeOrNull(): Int? =
    toIntOrNull()?.takeIf { it > 0 }

val alarmCardVersionCode: Int =
    (findProperty("ALARM_CARD_VERSION_CODE") as? String)?.toPositiveVersionCodeOrNull()
        ?: System.getenv("ALARM_CARD_VERSION_CODE")?.toPositiveVersionCodeOrNull()
        ?: System.getenv("GITHUB_RUN_NUMBER")?.toPositiveVersionCodeOrNull()
        ?: 1

val alarmCardVersionName: String =
    (findProperty("ALARM_CARD_VERSION_NAME") as? String)?.takeIf { it.isNotBlank() }
        ?: System.getenv("ALARM_CARD_VERSION_NAME")?.takeIf { it.isNotBlank() }
        ?: "0.1.0"

android {
    namespace = "com.jpark.alarmcard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jpark.alarmcard"
        minSdk = 26
        targetSdk = 34
        versionCode = alarmCardVersionCode
        versionName = alarmCardVersionName

        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        getByName("debug") {
            // CI runner마다 달라지는 기본 ~/.android/debug.keystore 대신
            // 저장소에 포함된 고정 개발용 keystore를 사용한다.
            // 이 APK는 개인 배포용 debug 빌드이며 Play Store 배포용 release 키가 아니다.
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xjvm-default=all")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        ignoreWarnings = true
        quiet = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.jsoup)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.timber)
    implementation(libs.work.runtime.ktx)
    implementation(libs.reorderable)

    testImplementation("junit:junit:4.13.2")
}
