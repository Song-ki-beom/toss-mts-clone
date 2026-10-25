import { describe, expect, it } from 'vitest'
import { Fees, TradingEngine } from './tradingEngine'
import type { Quote, Stock } from './models'

const samsung: Stock = { code: '005930', name: '삼성전자', market: 'KOSPI' }
const quote = (price: number): Record<string, Quote> => ({
  [samsung.code]: { code: samsung.code, price, prevClose: 70_000, open: 70_000, high: price, low: price, volume: 0 },
})

describe('TradingEngine', () => {
  it('시장가 구매는 즉시 체결되고 대금이 빠진다', () => {
    const engine = new TradingEngine(1_000_000)
    const result = engine.placeOrder(samsung, 'BUY', 'MARKET', 0, 10, 70_000)

    expect(result.ok && result.order.status).toBe('FILLED')
    const acc = engine.getState()
    expect(acc.cash).toBe(1_000_000 - Fees.buyCost(70_000, 10))
    expect(acc.holdings[samsung.code]).toMatchObject({ quantity: 10, avgPrice: 70_000 })
  })

  it('지정가 구매는 가격이 내려올 때까지 대기한다', () => {
    const engine = new TradingEngine(1_000_000)
    const result = engine.placeOrder(samsung, 'BUY', 'LIMIT', 69_000, 5, 70_000)
    expect(result.ok && result.order.status).toBe('PENDING')

    expect(engine.onQuotes(quote(69_500))).toHaveLength(0)
    const filled = engine.onQuotes(quote(68_900))

    expect(filled).toHaveLength(1)
    expect(filled[0].filledPrice).toBe(69_000)
    expect(engine.getState().holdings[samsung.code].quantity).toBe(5)
  })

  it('대기 주문을 취소하면 묶인 대금을 돌려준다', () => {
    const engine = new TradingEngine(1_000_000)
    const result = engine.placeOrder(samsung, 'BUY', 'LIMIT', 60_000, 5, 70_000)
    if (!result.ok) throw new Error(result.message)

    expect(engine.cancel(result.order.id)).toBe(true)
    expect(engine.getState().cash).toBe(1_000_000)
    expect(engine.getState().orders[0].status).toBe('CANCELED')
  })

  it('보유보다 많은 판매와 잔고를 넘는 구매를 거절한다', () => {
    const engine = new TradingEngine(100_000)

    expect(engine.placeOrder(samsung, 'SELL', 'MARKET', 0, 1, 70_000).ok).toBe(false)
    expect(engine.placeOrder(samsung, 'BUY', 'MARKET', 0, 2, 70_000).ok).toBe(false)
    expect(engine.maxBuyQuantity(70_000)).toBe(1)
  })

  it('평균단가를 계산하고 전량 판매하면 보유에서 지운다', () => {
    const engine = new TradingEngine(1_000_000)
    engine.placeOrder(samsung, 'BUY', 'MARKET', 0, 2, 70_000)
    engine.placeOrder(samsung, 'BUY', 'MARKET', 0, 2, 72_000)
    expect(engine.getState().holdings[samsung.code].avgPrice).toBe(71_000)

    engine.placeOrder(samsung, 'SELL', 'MARKET', 0, 4, 75_000)
    expect(engine.getState().holdings).toEqual({})
  })

  it('구독자에게 계좌 변경을 알린다', () => {
    const engine = new TradingEngine(1_000_000)
    let calls = 0
    const unsubscribe = engine.subscribe(() => calls++)
    engine.placeOrder(samsung, 'BUY', 'MARKET', 0, 1, 70_000)
    unsubscribe()
    engine.placeOrder(samsung, 'BUY', 'MARKET', 0, 1, 70_000)
    expect(calls).toBe(1)
  })
})
