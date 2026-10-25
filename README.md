# Simple MTS

Kotlin + Jetpack Compose로 만든 간단한 모의투자 MTS 앱입니다. 증권사 API 없이 모의 시세로 동작합니다.

## 기능
- 관심종목: 8개 종목 실시간 시세 (0.5초마다 갱신, 가격 변동 시 깜빡임)
- 종목 상세: 현재가·시고저·거래량, 캔들 차트(봉당 5초), 10단 호가
- 주문: 매수/매도, 지정가/시장가, 호가 터치로 지정가 입력, 최대 수량
- 잔고: 총자산, 예수금, 평가손익, 보유종목, 주문내역(미체결 취소)

## 구조
```
data/
  Models.kt          시세·주문·계좌 모델, 한국거래소 호가단위
  MarketSimulator.kt MarketDataSource 인터페이스 + 랜덤워크 모의 시세
  TradingEngine.kt   모의 계좌, 주문 체결, 수수료(0.015%, 세금 생략)
ui/
  MtsViewModel.kt    시세·계좌 상태와 주문 처리
  MtsApp.kt          하단 탭 + 화면 전환
  WatchlistScreen / StockDetailScreen / OrderSheet / AccountScreen
```

실제 시세로 바꾸려면 `MarketDataSource`를 증권사 API(예: 한국투자증권 KIS의 REST + WebSocket)로 구현해
`MtsViewModel`에서 `MarketSimulator` 대신 넣으면 됩니다.

## 빌드
Android Studio에서 이 폴더를 열고 Run을 누르거나, 터미널에서 다음을 실행합니다.

```
gradlew.bat assembleDebug
gradlew.bat testDebugUnitTest
```

AGP 9.4.1 · Kotlin 2.4.21 · Gradle 9.8.2 · compileSdk 37 · minSdk 26
