# 저장소 루트에서 빌드: docker build -f server/Dockerfile -t mts-relay .
# 1단계: 서버 빌드 (Android SDK가 없으므로 앱 모듈은 빼고 빌드)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY core ./core
COPY server ./server
ENV MTS_SERVER_ONLY=1
RUN chmod +x gradlew && ./gradlew :server:installDist --no-daemon

# 2단계: 실행 이미지
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/server/build/install/server /app
ENV PORT=8080
EXPOSE 8080
# KIS_APP_KEY, KIS_APP_SECRET, KIS_MOCK, ALLOWED_ORIGINS 는 실행할 때 환경 변수로 넣는다
CMD ["/app/bin/server"]
