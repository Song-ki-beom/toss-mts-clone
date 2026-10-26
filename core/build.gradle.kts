import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// 앱(Android)과 서버(Ktor)가 함께 쓰는 순수 Kotlin 모듈. Android API를 쓰면 안 된다.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    api("com.squareup.okhttp3:okhttp:5.5.1")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")

    testImplementation("junit:junit:4.13.2")
}
