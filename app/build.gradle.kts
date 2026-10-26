import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// KIS API 키는 Git에 올라가지 않는 local.properties에서 읽는다
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun localProp(key: String, default: String = "") = localProps.getProperty(key, default).trim()

// 릴리스 서명 정보: 내 PC에서는 keystore.properties(Git 제외), GitHub Actions에서는 환경 변수로 받는다
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun signingValue(propKey: String, envKey: String): String? =
    keystoreProps.getProperty(propKey)?.trim()?.takeIf { it.isNotEmpty() } ?: System.getenv(envKey)?.takeIf { it.isNotEmpty() }

val releaseStoreFile = signingValue("storeFile", "MTS_KEYSTORE_PATH")?.let { rootProject.file(it) }
val hasReleaseSigning = releaseStoreFile?.exists() == true

android {
    namespace = "com.example.mts"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.mts"
        minSdk = 26
        targetSdk = 37
        // CI에서는 -PversionCode=빌드번호 로 넘겨 Play 업로드마다 증가시킨다
        versionCode = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 3
        versionName = (findProperty("versionName") as String?) ?: "0.3.0"

        buildConfigField("String", "KIS_APP_KEY", "\"${localProp("kis.appKey")}\"")
        buildConfigField("String", "KIS_APP_SECRET", "\"${localProp("kis.appSecret")}\"")
        buildConfigField("boolean", "KIS_MOCK", localProp("kis.mock", "true"))
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = signingValue("storePassword", "MTS_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "MTS_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "MTS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8로 코드 축소·난독화, 쓰지 않는 리소스 제거
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 서명 정보가 없으면 서명되지 않은 릴리스 파일(app-release-unsigned.apk)이 만들어진다
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    // 모델, 시세·주문 엔진, KIS 연동 (OkHttp, kotlinx.serialization, 코루틴을 함께 가져온다)
    implementation(project(":core"))
}
