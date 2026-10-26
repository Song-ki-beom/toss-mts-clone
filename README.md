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

## 중계 서버 (`server/`)
증권사 구조처럼 **앱·웹은 중계 서버에만 연결하고, KIS 키는 서버에만 둡니다.**
```
[Android 앱] ─┐                        ┌─ REST ──────▶ [한국투자증권 KIS]
              ├─ REST + WebSocket ─▶ [Ktor 서버] ─┤
[웹 (React)] ─┘      (키 없음)          (키 보관)  └─ WebSocket ─▶
```
Ktor 3로 만들었고, 시세·주문 엔진과 KIS 연동 코드는 앱과 같은 `core` 모듈을 씁니다. KIS 키가 없으면 모의 시세로 동작합니다.

| 경로 | 내용 |
|---|---|
| `GET /api/health` | 서버 상태와 시세 연결 상태 |
| `GET /api/market` | 종목 목록, 현재 시세·호가·5초봉 스냅샷 |
| `GET /api/stocks/{code}/history?period=DAY` | 일·주·월·년봉 (`DAY`, `WEEK`, `MONTH`, `YEAR`), 10분 캐시 |
| `WS /ws/market` | 연결 직후 `{"type":"snapshot",...}`, 이후 0.5초 간격 `{"type":"state",...}` |

환경 변수
| 이름 | 설명 |
|---|---|
| `PORT` | 기본 8080 |
| `KIS_APP_KEY`, `KIS_APP_SECRET` | 있으면 KIS 실시세, 없으면 모의 시세 |
| `KIS_MOCK` | `false`면 실전투자 서버 (기본 모의투자) |
| `ALLOWED_ORIGINS` | 브라우저 접근을 허용할 호스트, 쉼표 구분 (예: `toss-mts-clone.vercel.app,localhost:5173`). 비우면 모두 허용 |

실행
```
gradlew.bat :server:run
```
또는 배포본을 만들어 실행합니다.
```
gradlew.bat :server:installDist
server\build\install\server\bin\server.bat
```
Docker (저장소 루트에서):
```
docker build -f server/Dockerfile -t mts-relay .
docker run -p 8080:8080 -e KIS_APP_KEY=... -e KIS_APP_SECRET=... mts-relay
```
### Render 배포
배포 설정은 저장소 루트의 `render.yaml`(Blueprint)에 있습니다. Docker 웹 서비스, 싱가포르 지역, 무료 플랜, 상태 확인 `/api/health`.

1. https://render.com 에 GitHub 계정으로 가입·로그인합니다.
2. **New → Blueprint**를 누르고 GitHub 연동을 허용한 뒤 `toss-mts-clone` 저장소를 선택합니다.
   (Private 저장소는 Render GitHub 앱에 이 저장소 접근 권한을 줘야 목록에 보입니다)
3. `render.yaml`을 읽어 `mts-relay` 서비스가 표시됩니다. 환경 변수 입력 칸에서
   - `KIS_APP_KEY`, `KIS_APP_SECRET`: KIS 키를 넣으면 실시세, **비워 두면 모의 시세**
   - 나머지(`KIS_MOCK`, `ALLOWED_ORIGINS`, `JAVA_OPTS`)는 미리 채워져 있습니다.
4. **Apply**를 누르면 Docker 이미지를 빌드하고 배포합니다. 첫 빌드는 Gradle과 라이브러리를 받느라 5~10분 걸립니다.
5. 완료되면 `https://mts-relay-xxxx.onrender.com` 같은 주소가 생깁니다. `https://주소/api/health`가 `{"status":"ok",...}`를 돌려주면 성공입니다.

배포 후 연결
- **웹(Vercel)**: 프로젝트 Settings → Environment Variables에 `VITE_API_URL=https://Render주소`를 추가하고 다시 배포(Deployments → Redeploy)합니다.
  Vite 환경 변수는 빌드할 때 들어가므로 다시 배포해야 반영됩니다.
- **앱**: `local.properties`에 `mts.serverUrl=https://Render주소`
- Vercel 주소가 바뀌면 Render의 `ALLOWED_ORIGINS`도 바꿔야 브라우저가 서버를 호출할 수 있습니다.

알아둘 점
- 무료 플랜은 15분 동안 요청이 없으면 잠들고, 다음 요청 때 깨어나는 데 1분 정도 걸립니다. 그동안 웹은 "서버 연결 중"으로 보이다가 자동으로 연결됩니다.
- `main`에 푸시할 때 `server/`, `core/`, Gradle 설정이 바뀌었을 때만 자동으로 다시 배포합니다.
- KIS 서버가 해외 IP 접속을 제한하는 경우 실시세 연결이 실패할 수 있습니다. 서버 상태 칩이나 `/api/health`의 `market` 값으로 확인하세요.

Fly.io, Railway 같은 다른 서비스도 Dockerfile 경로를 `server/Dockerfile`, 빌드 위치를 저장소 루트로 지정하면 같은 방식으로 배포할 수 있습니다.

### 앱·웹을 서버에 연결
- **웹**: `web/.env.local`(또는 Vercel 환경 변수)에 `VITE_API_URL=https://서버주소`. 없으면 브라우저 모의 시세.
- **앱**: `local.properties`에 `mts.serverUrl=https://서버주소`. 없으면 KIS 직접 연결 또는 모의 시세.
  - 시세 공급원 우선순위: `mts.serverUrl` → `kis.appKey/appSecret` → 모의 시세
- 에뮬레이터에서 내 PC의 서버에 붙을 때는 `adb reverse`로 포트를 연결하고 `localhost`를 씁니다.
  (`10.0.2.2`는 Windows 방화벽 때문에 TCP 연결이 막힐 수 있습니다)
  ```
  adb reverse tcp:8080 tcp:8080
  ```
  `local.properties`: `mts.serverUrl=http://localhost:8080`

## Android 앱 배포
릴리스 빌드는 R8 난독화·리소스 축소가 켜져 있습니다(APK 약 1.5MB). 결과물은 두 가지입니다.

| 파일 | 용도 |
|---|---|
| `app-release.apk` | 휴대폰에 직접 설치, GitHub Release로 배포 |
| `app-release.aab` | Google Play Console 업로드용 |

### 1. 서명 키 만들기 (처음 한 번)
서명 키를 잃어버리면 같은 앱으로 업데이트할 수 없으니 안전한 곳에 백업하세요. 키와 비밀번호는 Git에 올라가지 않습니다.
```
"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore release-key.jks -alias mts-release -keyalg RSA -keysize 2048 -validity 10000
```
`keystore.properties.example`을 `keystore.properties`로 복사해 비밀번호를 채웁니다.

### 2. 내 PC에서 릴리스 빌드
```
gradlew.bat assembleRelease bundleRelease
```
결과물: `app/build/outputs/apk/release/app-release.apk`, `app/build/outputs/bundle/release/app-release.aab`
(`keystore.properties`가 없으면 서명되지 않은 `app-release-unsigned.apk`가 만들어집니다.)

### 3. GitHub Actions로 자동 배포
저장소 **Settings → Secrets and variables → Actions**에 시크릿 4개를 등록합니다.

| 시크릿 | 값 |
|---|---|
| `MTS_KEYSTORE_BASE64` | 키 파일을 Base64로 바꾼 문자열 (아래 명령) |
| `MTS_KEYSTORE_PASSWORD` | 키스토어 비밀번호 |
| `MTS_KEY_ALIAS` | `mts-release` |
| `MTS_KEY_PASSWORD` | 키 비밀번호 |

```
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release-key.jks")) | Set-Clipboard
```
그다음 버전 태그를 푸시하면 `.github/workflows/android-release.yml`이 테스트 → 서명된 APK·AAB 빌드 → GitHub Release 생성까지 합니다.
워크플로에는 중계 서버 주소(`MTS_SERVER_URL=https://mts-relay.onrender.com`)가 들어 있어, 릴리스 APK는 설치하자마자 Render 중계 서버의 KIS 모의투자 시세를 받습니다.
내 PC에서 빌드할 때는 `local.properties`의 `mts.serverUrl`이 우선입니다.
```
git tag v0.3.0
git push origin v0.3.0
```
`main`에 푸시하거나 PR을 열면 `.github/workflows/ci.yml`이 앱·웹 빌드와 테스트를 돌립니다.

### 4. Google Play (선택)
Play Console(등록비 1회 25달러)에서 앱을 만들고 **내부 테스트** 트랙에 AAB를 올리는 것부터 시작하세요.
실제 금융 서비스로 오해될 수 있는 앱은 심사에서 제한될 수 있으니, 설명에 학습용 모의투자 앱임을 분명히 적어야 합니다.
릴리스 빌드에는 KIS 키를 넣지 마세요. 배포된 APK에서 키를 꺼낼 수 있습니다.

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
