import { describe, expect, it } from 'vitest'
import { buildHistory, PERIOD_SPECS, periodReturn, type HistoryPeriod } from './history'
import { tickSize, type Quote } from './models'

const quote: Quote = { code: '005930', price: 72_300, prevClose: 71_500, open: 71_800, high: 72_600, low: 71_400, volume: 120_000 }
const today = new Date(2026, 9, 26) // 2026-10-26 월요일
const periods: HistoryPeriod[] = ['DAY', 'WEEK', 'MONTH', 'YEAR']

describe('buildHistory', () => {
  it.each(periods)('%s: 정해진 개수의 봉을 만들고 마지막 봉은 실시간 시세로 끝난다', (period) => {
    const candles = buildHistory('005930', period, quote, today)
    const last = candles[candles.length - 1]

    expect(candles).toHaveLength(PERIOD_SPECS[period].count)
    expect(last.close).toBe(quote.price)
    expect(last.high).toBeGreaterThanOrEqual(quote.high)
    expect(last.low).toBeLessThanOrEqual(quote.low)
  })

  it('일봉: 직전 봉 종가는 어제 종가이고 마지막 봉 시가는 오늘 시가다', () => {
    const days = buildHistory('005930', 'DAY', quote, today)
    expect(days.at(-2)!.close).toBe(quote.prevClose)
    expect(days.at(-1)!.open).toBe(quote.open)
  })

  it.each(['WEEK', 'MONTH', 'YEAR'] as const)('%s: 이번 봉은 직전 봉 종가에서 시작한다', (period) => {
    const candles = buildHistory('005930', period, quote, today)
    expect(candles.at(-1)!.open).toBe(candles.at(-2)!.close)
  })

  it.each(periods)('%s: 가격은 양수이고 호가단위에 맞으며 고가·저가가 시가·종가를 감싼다', (period) => {
    for (const c of buildHistory('000660', period, { ...quote, code: '000660' }, today).slice(0, -1)) {
      for (const v of [c.open, c.high, c.low, c.close]) {
        expect(v).toBeGreaterThan(0)
        expect(v % tickSize(v)).toBe(0)
      }
      expect(c.high).toBeGreaterThanOrEqual(Math.max(c.open, c.close))
      expect(c.low).toBeLessThanOrEqual(Math.min(c.open, c.close))
    }
  })

  it('같은 종목·기간이면 항상 같은 결과, 다른 종목이면 다른 결과', () => {
    const a = buildHistory('005930', 'MONTH', quote, today)
    expect(buildHistory('005930', 'MONTH', quote, today)).toEqual(a)
    expect(buildHistory('035720', 'MONTH', quote, today)).not.toEqual(a)
  })

  it('봉 시각은 과거에서 현재로 증가하고, 일봉은 주말을 건너뛴다', () => {
    const days = buildHistory('005930', 'DAY', quote, today)
    const times = days.map((c) => c.time!)
    expect(times).toEqual([...times].sort((x, y) => x - y))
    expect(days.every((c) => ![0, 6].includes(new Date(c.time!).getDay()))).toBe(true)
    expect(new Date(times.at(-1)!).getDate()).toBe(26)

    const years = buildHistory('005930', 'YEAR', quote, today)
    expect(new Date(years[0].time!).getFullYear()).toBe(2012)
    expect(new Date(years.at(-1)!.time!).getFullYear()).toBe(2026)
  })
})

describe('periodReturn', () => {
  it('첫 봉 시가 대비 마지막 종가의 등락률', () => {
    expect(periodReturn([
      { open: 100, high: 110, low: 90, close: 105, volume: 0 },
      { open: 105, high: 130, low: 100, close: 120, volume: 0 },
    ])).toBe(20)
    expect(periodReturn([])).toBe(0)
  })
})
