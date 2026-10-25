# Simple MTS

Kotlin + Jetpack Compose로 만든 간단한 모의투자 MTS 앱입니다.
한국투자증권(KIS) Open API 키를 넣으면 실제 시세를, 없으면 내장 모의 시세를 사용합니다.
주문은 어느 경우든 앱 안의 모의 계좌(시작 자금 1,000만원)에서 체결됩니다.

## 기능
- 관심종목: 8개 종목 실시간 시세 (0.5초마다 갱신, 가격 변동 시 깜빡임)
- 종목 상세: 현재가·시고저·거래량, 캔들 차트(봉당 5초), 10단 호가
- 주문: 매수/매도, 지정가/시장가, 호가 터치로 지정가 입력, 최대 수량
- 잔고: 총자산, 예수금, 평가손익, 보유종목, 주문내역(미체결 취소)

## KIS 실시세 연동
1. 한국투자증권 계좌를 만들고 [KIS Developers](https://apiportal.koreainvestment.com)에서 모의투자 신청 후 App Key / App Secret을 발급받습니다.
2. 프로젝트 루트의 `local.properties`(Git에 올라가지 않음)에 추가합니다.
   ```
   kis.appKey=발급받은_APP_KEY
   kis.appSecret=발급받은_APP_SECRET
   kis.mock=true
   ```
   실전투자 키라면 `kis.mock=false`로 바꿉니다.
3. 다시 빌드하면 상단 칩에 `KIS 모의 · 실시간`처럼 연결 상태가 표시됩니다.

동작 방식
- 시작 시 REST로 현재가(FHKST01010100), 호가(FHKST01010200), 당일 1분봉(FHKST03010200)을 조회합니다.
- 이후 WebSocket으로 실시간 체결가(H0STCNT0)와 호가(H0STASP0)를 받습니다. 끊기면 자동 재연결합니다.
- 접근토큰은 하루 유효하고 1분에 한 번만 발급되므로 앱 내부에 저장해 재사용합니다.
- 장 운영시간(평일 09:00~15:30)이 아니면 실시간 데이터가 오지 않으며, 마지막 시세가 표시됩니다.

주의: 이 방식은 App Secret이 APK 안에 들어갑니다. 개인 학습용으로만 쓰고,
배포하는 앱이라면 키는 중계 서버에 두고 앱은 서버에서 토큰만 받아야 합니다.

## 구조
```
data/
  Models.kt          시세·주문·계좌 모델, 한국거래소 호가단위, 기본 관심종목
  MarketSimulator.kt MarketDataSource 인터페이스 + 랜덤워크 모의 시세
  TradingEngine.kt   모의 계좌, 주문 체결, 수수료(0.015%, 세금 생략)
  kis/
    KisConfig.kt           접속 정보(모의/실전 URL), 토큰 저장소
    KisApi.kt              REST 호출(토큰, 실시간 접속키, 현재가, 호가, 분봉), 호출 간격 제한
    KisParser.kt           REST·WebSocket 응답 파싱
    KisMarketDataSource.kt 스냅샷 조회 + WebSocket 실시간 갱신, 재연결
ui/
  MtsViewModel.kt    시세·계좌 상태와 주문 처리
  MtsApp.kt          하단 탭 + 화면 전환
  WatchlistScreen / StockDetailScreen / OrderSheet / AccountScreen
```

## 빌드
Android Studio에서 이 폴더를 열고 Run을 누르거나, 터미널에서 다음을 실행합니다.

```
gradlew.bat assembleDebug
gradlew.bat testDebugUnitTest
```

AGP 9.4.1 · Kotlin 2.4.21 · Gradle 9.8.2 · compileSdk 37 · minSdk 26
