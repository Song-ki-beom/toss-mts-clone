pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TossMtsClone"

// core: 앱과 서버가 함께 쓰는 순수 Kotlin 코드 (모델, 시세·주문 엔진, KIS 연동)
include(":core")

// server: KIS 키를 보관하고 앱·웹에 시세를 중계하는 Ktor 서버
include(":server")

// 서버만 빌드하는 환경(Docker 등)에는 Android SDK가 없으므로 앱 모듈을 뺄 수 있게 한다
if (System.getenv("MTS_SERVER_ONLY") == null) {
    include(":app")
}
