import { roundToTick, type Candle, type Quote } from './models'

export type Period = 'LIVE' | 'DAY' | 'WEEK' | 'MONTH' | 'YEAR'
export type HistoryPeriod = Exclude<Period, 'LIVE'>

export const PERIODS = ['LIVE', 'DAY', 'WEEK', 'MONTH', 'YEAR'] as const
export const PERIOD_LABEL: Record<Period, string> = { LIVE: '실시간', DAY: '일', WEEK: '주', MONTH: '월', YEAR: '년' }

interface Spec {
  count: number
  /** 봉 하나의 평균 변동 폭 */
  vol: number
  caption: string
  /** today에서 n봉 전의 날짜 */
  back: (today: Date, n: number) => Date
}

export const PERIOD_SPECS: Record<HistoryPeriod, Spec> = {
  DAY: { count: 120, vol: 0.022, caption: '최근 120일', back: (d, n) => subBusinessDays(d, n) },
  WEEK: { count: 104, vol: 0.045, caption: '최근 2년', back: (d, n) => addDays(d, -7 * n) },
  MONTH: { count: 60, vol: 0.085, caption: '최근 5년', back: (d, n) => new Date(d.getFullYear(), d.getMonth() - n, 1) },
  YEAR: { count: 15, vol: 0.22, caption: '최근 15년', back: (d, n) => new Date(d.getFullYear() - n, 0, 1) },
}

/**
 * 모의 서비스라 과거 시세가 없으므로 종목·기간마다 항상 같은 결과가 나오는 과거 봉을 만든다.
 * 마지막 봉(오늘·이번 주·이번 달·올해)은 실시간 시세로 채운다.
 */
export function buildHistory(code: string, period: HistoryPeriod, quote: Quote, today: Date = new Date()): Candle[] {
  const spec = PERIOD_SPECS[period]
  const rand = mulberry32(hash(`${code}:${period}`))
  const pastCount = spec.count - 1

  // 일봉은 직전 봉 종가가 어제 종가다. 주·월·연봉은 이번 봉이 며칠~몇 달 전에 시작했으므로
  // 어제 종가에서 한 걸음 거슬러 올라간 값을 이번 봉의 시작(= 직전 봉 종가)으로 둔다
  const closes = new Array<number>(pastCount)
  let p = period === 'DAY' ? quote.prevClose : quote.prevClose / (1 + (rand() - 0.48) * spec.vol)
  for (let i = pastCount - 1; i >= 0; i--) {
    closes[i] = p
    p = p / (1 + (rand() - 0.48) * spec.vol)
  }

  const tick = (v: number) => Math.max(1, roundToTick(Math.round(v)))
  const timeOf = (i: number) => spec.back(today, spec.count - 1 - i).getTime()
  const candles: Candle[] = closes.map((close, i) => {
    const open = i === 0 ? close / (1 + (rand() - 0.5) * spec.vol) : closes[i - 1]
    const high = Math.max(open, close) * (1 + rand() * spec.vol * 0.4)
    const low = Math.min(open, close) * (1 - rand() * spec.vol * 0.4)
    return {
      open: tick(open),
      high: tick(high),
      low: tick(low),
      close: tick(close),
      volume: Math.round((0.5 + rand()) * 1_000_000 * (spec.vol / 0.022)),
      time: timeOf(i),
    }
  })

  candles.push(currentBar(period, candles[candles.length - 1], quote, timeOf(pastCount)))
  return candles
}

/** 지금 진행 중인 봉. 일봉은 오늘 시가로 시작하고, 나머지는 직전 봉 종가에서 시작한다 */
export function currentBar(period: HistoryPeriod, prev: Candle, quote: Quote, time: number): Candle {
  const open = period === 'DAY' ? quote.open : prev.close
  return {
    open,
    high: Math.max(open, quote.high, quote.price),
    low: Math.min(open, quote.low, quote.price),
    close: quote.price,
    volume: quote.volume,
    time,
  }
}

/** 진행 중인 마지막 봉에 실시간 가격을 반영한다 */
export function withLivePrice(candles: Candle[], price: number): Candle[] {
  if (candles.length === 0) return candles
  const last = candles[candles.length - 1]
  return [...candles.slice(0, -1), { ...last, high: Math.max(last.high, price), low: Math.min(last.low, price), close: price }]
}

/** 첫 봉 시가 대비 마지막 봉 종가의 등락률(%) */
export function periodReturn(candles: Candle[]): number {
  if (candles.length === 0) return 0
  const first = candles[0].open
  return ((candles[candles.length - 1].close - first) * 100) / first
}

function addDays(d: Date, n: number): Date {
  const r = new Date(d.getFullYear(), d.getMonth(), d.getDate())
  r.setDate(r.getDate() + n)
  return r
}

/** 주말을 건너뛰며 n영업일 전 날짜 */
function subBusinessDays(d: Date, n: number): Date {
  let r = new Date(d.getFullYear(), d.getMonth(), d.getDate())
  while (r.getDay() === 0 || r.getDay() === 6) r = addDays(r, -1)
  for (let left = n; left > 0; ) {
    r = addDays(r, -1)
    if (r.getDay() !== 0 && r.getDay() !== 6) left--
  }
  return r
}

function hash(text: string): number {
  let h = 2166136261
  for (let i = 0; i < text.length; i++) h = Math.imul(h ^ text.charCodeAt(i), 16777619)
  return h >>> 0
}

/** 시드가 같으면 항상 같은 수열을 내는 작은 난수 생성기 */
function mulberry32(seed: number): () => number {
  let a = seed
  return () => {
    a = (a + 0x6d2b79f5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}
