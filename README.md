# Toss MTS Clone

토스증권 MTS를 참고해 Kotlin + Jetpack Compose로 다시 만들어 보는 모의투자 MTS 클론 프로젝트입니다.

> 개인 학습용 클론이며 토스증권(비바리퍼블리카)과 관계가 없습니다. 토스의 로고나 상표 이미지는 사용하지 않습니다.

한국투자증권(KIS) Open API 키를 넣으면 실제 시세를, 없으면 내장 모의 시세를 사용합니다.
주문은 어느 경우든 앱 안의 모의 계좌(시작 자금 1,000만원)에서 체결됩니다.

## 기능
- 홈: 내 투자(총자산·수익), 내 주식, 오늘 많이 오른/내린 주식 랭킹
- 관심: 8개 종목 실시간 시세 (가격 변동 시 깜빡임)
- 종목 상세: 현재가·어제 대비, 라인/캔들 차트 전환, 시세 정보, 10단 호가
- 주문: 구매/판매, 지정가/시장가, 호가 터치로 지정가 입력, 수량 10%·25%·50%·최대
- 내 계좌: 총자산, 주문 가능 금액, 평가 손익, 보유 주식, 주문 내역(대기 주문 취소)

## UI
- 회색 배경 위 흰색 둥근 카드, 큰 숫자 중심의 타이포그래피(숫자 고정폭 tnum)
- 상승 빨강 / 하락 파랑, 라이트·다크 모드 지원
- 디자인 토큰은 `ui/theme/Theme.kt`, 공통 카드·목록·토글은 `ui/Components.kt`

## 웹 버전 (`web/`)
React 19 + TypeScript + Vite로 만든 PC 웹 트레이딩 화면입니다. 모바일 앱과 같은 모의 시세·주문 엔진을 TypeScript로 옮겨 씁니다.

- 3단 레이아웃: 내 투자·관심종목 / 종목 시세·차트·보유 주식·주문 내역 / 주문 패널·호가
- 화면 폭에 따라 3단 → 2단 → 1단으로 바뀌는 반응형, 라이트·다크 모드
- 실행: Node.js 20.19 이상 필요
  ```
  cd web
  npm install
  npm run dev      # http://localhost:5173
  npm test         # Vitest 단위 테스트
  npm run build    # dist/ 에 정적 파일 생성
  ```
- Windows PowerShell에서 `npm` 실행이 막히면 `npm.cmd run dev`처럼 실행합니다.

### Vercel 배포
배포 설정은 `web/vercel.json`에 있습니다(Vite, `npm ci` → `npm run build`, 결과물 `dist/`).

1. https://vercel.com 에 GitHub 계정으로 가입·로그인합니다.
2. **Add New → Project**에서 GitHub 연동을 허용하고 `toss-mts-clone` 저장소를 **Import**합니다.
   Private 저장소는 Vercel GitHub 앱에 해당 저장소 접근 권한을 줘야 목록에 보입니다.
3. **Root Directory**를 `web`으로 지정합니다. (이 저장소는 루트가 Android 프로젝트라 꼭 필요)
4. Framework Preset이 **Vite**로 잡혔는지 확인하고 **Deploy**를 누릅니다. 환경 변수는 필요 없습니다.
5. 이후 `main` 브랜치에 푸시할 때마다 자동으로 다시 배포되고, 다른 브랜치는 미리보기 URL이 만들어집니다.

CLI로 배포하려면:
```
cd web
npx vercel           # 처음: 로그인, 프로젝트 연결 (Root Directory 질문에 ./ 그대로)
npx vercel --prod    # 운영 배포
```

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
