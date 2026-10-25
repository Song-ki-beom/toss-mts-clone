import { describe, expect, it } from 'vitest'
import { MarketSimulator } from './marketSimulator'
import { tickSize } from './models'

// 테스트마다 같은 결과가 나오도록 고정된 난수열을 쓴다
function seeded(seed = 42) {
  let s = seed
  return () => {
    s = (s * 1664525 + 1013904223) % 4294967296
    return s / 4294967296
  }
}

describe('tickSize', () => {
  it('한국거래소 호가단위를 따른다', () => {
    expect(tickSize(1_999)).toBe(1)
    expect(tickSize(4_999)).toBe(5)
    expect(tickSize(41_250)).toBe(50)
    expect(tickSize(71_500)).toBe(100)
    expect(tickSize(352_000)).toBe(500)
    expect(tickSize(800_000)).toBe(1_000)
  })
})

describe('MarketSimulator', () => {
  it('가격은 항상 호가단위에 맞고 상·하한가를 넘지 않는다', () => {
    const sim = new MarketSimulator(seeded())
    for (let i = 0; i < 300; i++) sim.tick()

    for (const q of Object.values(sim.getState().quotes)) {
      expect(q.price % tickSize(q.price)).toBe(0)
      expect(q.price).toBeLessThanOrEqual(q.prevClose * 1.3)
      expect(q.price).toBeGreaterThanOrEqual(q.prevClose * 0.7)
      expect(q.high).toBeGreaterThanOrEqual(q.price)
      expect(q.low).toBeLessThanOrEqual(q.price)
    }
  })

  it('정해진 틱마다 새 캔들을 만들고 최대 60개만 유지한다', () => {
    const sim = new MarketSimulator(seeded(), 500, 10)
    const before = sim.getState().candles['005930'].length
    for (let i = 0; i < 10; i++) sim.tick()
    expect(sim.getState().candles['005930'].length).toBe(before + 1)

    for (let i = 0; i < 1_000; i++) sim.tick()
    expect(sim.getState().candles['005930'].length).toBe(60)
  })

  it('호가는 매도 10단, 매수 10단이고 가격 내림차순이다', () => {
    const sim = new MarketSimulator(seeded())
    const book = sim.getState().orderBooks['005930']
    const prices = [...book.asks, ...book.bids].map((l) => l.price)

    expect(book.asks).toHaveLength(10)
    expect(book.bids).toHaveLength(10)
    expect(prices).toEqual([...prices].sort((a, b) => b - a))
  })

  it('tick 때마다 구독자에게 알린다', () => {
    const sim = new MarketSimulator(seeded())
    let calls = 0
    sim.subscribe(() => calls++)
    sim.tick()
    sim.tick()
    expect(calls).toBe(2)
  })
})
