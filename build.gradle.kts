plugins {
    id("com.android.application") version "9.4.1" apply false
    // AGP 9는 Kotlin을 내장하고 있다. 여기서는 Compose 컴파일러와 버전을 맞추기 위해 KGP 버전만 지정한다.
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
