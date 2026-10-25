// 모바일 앱 data/Models.kt 를 옮긴 모델

export interface Stock {
  code: string
  name: string
  market: 'KOSPI' | 'KOSDAQ'
}

export interface Quote {
  code: string
  price: number
  prevClose: number
  open: number
  high: number
  low: number
  volume: number
}

export const change = (q: Quote) => q.price - q.prevClose
export const changeRate = (q: Quote) => ((q.price - q.prevClose) * 100) / q.prevClose

export interface Candle {
  open: number
  high: number
  low: number
  close: number
  volume: number
}

export interface OrderBookLevel {
  price: number
  quantity: number
}

/** asks, bids 모두 가격 내림차순. 위에서부터 그대로 그리면 된다 */
export interface OrderBook {
  asks: OrderBookLevel[]
  bids: OrderBookLevel[]
}

export type Side = 'BUY' | 'SELL'
export type OrderType = 'LIMIT' | 'MARKET'
export type OrderStatus = 'PENDING' | 'FILLED' | 'CANCELED'

export const SIDE_LABEL: Record<Side, string> = { BUY: '구매', SELL: '판매' }
export const ORDER_TYPE_LABEL: Record<OrderType, string> = { LIMIT: '지정가', MARKET: '시장가' }
export const STATUS_LABEL: Record<OrderStatus, string> = { PENDING: '대기', FILLED: '체결', CANCELED: '취소' }

export interface Order {
  id: number
  code: string
  name: string
  side: Side
  type: OrderType
  price: number
  quantity: number
  status: OrderStatus
  filledPrice: number | null
  time: number
}

export interface Holding {
  code: string
  name: string
  quantity: number
  avgPrice: number
}

export interface Account {
  cash: number
  holdings: Record<string, Holding>
  orders: Order[]
}

export interface MarketState {
  quotes: Record<string, Quote>
  candles: Record<string, Candle[]>
  orderBooks: Record<string, OrderBook>
}

export const DEFAULT_WATCHLIST: Stock[] = [
  { code: '005930', name: '삼성전자', market: 'KOSPI' },
  { code: '000660', name: 'SK하이닉스', market: 'KOSPI' },
  { code: '035420', name: 'NAVER', market: 'KOSPI' },
  { code: '035720', name: '카카오', market: 'KOSPI' },
  { code: '005380', name: '현대차', market: 'KOSPI' },
  { code: '373220', name: 'LG에너지솔루션', market: 'KOSPI' },
  { code: '068270', name: '셀트리온', market: 'KOSPI' },
  { code: '247540', name: '에코프로비엠', market: 'KOSDAQ' },
]

/** 한국거래소 호가단위 (2023년 개편 기준) */
export function tickSize(price: number): number {
  if (price < 2_000) return 1
  if (price < 5_000) return 5
  if (price < 20_000) return 10
  if (price < 50_000) return 50
  if (price < 200_000) return 100
  if (price < 500_000) return 500
  return 1_000
}

export function roundToTick(price: number): number {
  const tick = tickSize(price)
  return Math.floor(price / tick) * tick
}
