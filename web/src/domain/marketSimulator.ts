import {
  DEFAULT_WATCHLIST, roundToTick, tickSize,
  type Candle, type MarketState, type OrderBook, type Quote, type Stock,
} from './models'

const HISTORY_SIZE = 40
const MAX_CANDLES = 60
const ORDER_BOOK_DEPTH = 10
const VOLATILITY = 0.004

const BASE_PRICES: Record<string, number> = {
  '005930': 71_500,
  '000660': 198_000,
  '035420': 187_300,
  '035720': 41_250,
  '005380': 236_500,
  '373220': 352_000,
  '068270': 181_200,
  '247540': 168_900,
}

type Listener = () => void

/**
 * 랜덤워크로 시세, 분봉, 호가를 만드는 모의 시세 엔진 (모바일 앱 MarketSimulator.kt 이식).
 * React의 useSyncExternalStore 로 구독할 수 있게 subscribe / getState 를 제공한다.
 */
export class MarketSimulator {
  readonly stocks: Stock[]
  readonly candleLabel: string
  private state: MarketState
  private listeners = new Set<Listener>()
  private timer: ReturnType<typeof setInterval> | null = null
  private tickCount = 0

  constructor(
    private readonly random: () => number = Math.random,
    private readonly tickIntervalMs = 500,
    private readonly ticksPerCandle = 10,
    stocks: Stock[] = DEFAULT_WATCHLIST,
  ) {
    this.stocks = stocks
    this.candleLabel = `봉당 ${(tickIntervalMs * ticksPerCandle) / 1000}초`

    const quotes: Record<string, Quote> = {}
    const candles: Record<string, Candle[]> = {}
    for (const stock of stocks) {
      const prevClose = BASE_PRICES[stock.code] ?? 50_000
      const history = this.generateHistory(prevClose)
      candles[stock.code] = history
      quotes[stock.code] = {
        code: stock.code,
        price: history[history.length - 1].close,
        prevClose,
        open: history[0].open,
        high: Math.max(...history.map((c) => c.high)),
        low: Math.min(...history.map((c) => c.low)),
        volume: history.reduce((s, c) => s + c.volume, 0),
      }
    }
    this.state = { quotes, candles, orderBooks: this.buildOrderBooks(quotes) }
  }

  getState = (): MarketState => this.state

  subscribe = (listener: Listener): (() => void) => {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  start(): void {
    if (this.timer) return
    this.timer = setInterval(() => this.tick(), this.tickIntervalMs)
  }

  stop(): void {
    if (this.timer) clearInterval(this.timer)
    this.timer = null
  }

  /** 시세를 한 번 갱신한다. 테스트에서 직접 호출할 수 있다 */
  tick(): void {
    this.tickCount++
    const startNewCandle = this.tickCount % this.ticksPerCandle === 0
    const quotes: Record<string, Quote> = {}
    const candles: Record<string, Candle[]> = {}

    for (const [code, q] of Object.entries(this.state.quotes)) {
      const price = this.nextPrice(q.price, q.prevClose)
      const volume = this.randomVolume()
      quotes[code] = { ...q, price, high: Math.max(q.high, price), low: Math.min(q.low, price), volume: q.volume + volume }

      const list = this.state.candles[code] ?? []
      if (startNewCandle || list.length === 0) {
        candles[code] = [...list, { open: price, high: price, low: price, close: price, volume }].slice(-MAX_CANDLES)
      } else {
        const last = list[list.length - 1]
        candles[code] = [
          ...list.slice(0, -1),
          { ...last, high: Math.max(last.high, price), low: Math.min(last.low, price), close: price, volume: last.volume + volume },
        ]
      }
    }

    this.state = { quotes, candles, orderBooks: this.buildOrderBooks(quotes) }
    this.listeners.forEach((l) => l())
  }

  private generateHistory(prevClose: number): Candle[] {
    let price = prevClose
    return Array.from({ length: HISTORY_SIZE }, () => {
      const open = price
      let high = price
      let low = price
      let volume = 0
      for (let i = 0; i < this.ticksPerCandle; i++) {
        price = this.nextPrice(price, prevClose)
        high = Math.max(high, price)
        low = Math.min(low, price)
        volume += this.randomVolume()
      }
      return { open, high, low, close: price, volume }
    })
  }

  /** 현재가에서 호가단위로 움직이되 상·하한가(전일 종가 ±30%)를 넘지 않는다 */
  nextPrice(price: number, prevClose: number): number {
    const tick = tickSize(price)
    const move = (this.random() - 0.5) * price * VOLATILITY
    const next = roundToTick(price + Math.round(move / tick) * tick)
    const upper = roundToTick(Math.floor((prevClose * 13) / 10))
    const lower = roundToTick(Math.floor((prevClose * 7) / 10))
    return Math.min(Math.max(next, lower), upper)
  }

  private buildOrderBooks(quotes: Record<string, Quote>): Record<string, OrderBook> {
    const books: Record<string, OrderBook> = {}
    for (const [code, q] of Object.entries(quotes)) {
      const tick = tickSize(q.price)
      books[code] = {
        asks: Array.from({ length: ORDER_BOOK_DEPTH }, (_, i) => {
          const level = ORDER_BOOK_DEPTH - i
          return { price: q.price + level * tick, quantity: this.randomQuantity(level) }
        }),
        bids: Array.from({ length: ORDER_BOOK_DEPTH }, (_, i) => ({ price: q.price - i * tick, quantity: this.randomQuantity(i + 1) })),
      }
    }
    return books
  }

  private randomQuantity(level: number): number {
    return Math.floor(50 + this.random() * 750) * (1 + Math.floor(level / 3))
  }

  private randomVolume(): number {
    return Math.floor(10 + this.random() * 490)
  }
}
