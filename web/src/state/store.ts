import { useSyncExternalStore } from 'react'
import { createFeed } from '../domain/feed'
import { TradingEngine } from '../domain/tradingEngine'
import { SIDE_LABEL, type Order, type OrderType, type Side, type Stock } from '../domain/models'
import { formatWon } from '../domain/format'

/** VITE_API_URL 이 있으면 중계 서버 시세, 없으면 브라우저 안 모의 시세 */
export const market = createFeed()
export const trading = new TradingEngine()

// ── 토스트 알림
export interface Toast {
  id: number
  text: string
}

let toasts: Toast[] = []
let toastId = 0
const toastListeners = new Set<() => void>()
const emitToasts = () => toastListeners.forEach((l) => l())

export function pushToast(text: string) {
  const id = ++toastId
  toasts = [...toasts, { id, text }].slice(-3)
  emitToasts()
  setTimeout(() => {
    toasts = toasts.filter((t) => t.id !== id)
    emitToasts()
  }, 2600)
}

const subscribeToasts = (l: () => void) => {
  toastListeners.add(l)
  return () => toastListeners.delete(l)
}

// ── 시세가 바뀔 때마다 대기 중인 지정가 주문을 확인한다
const fillMessage = (o: Order) => `${o.name} ${o.quantity}주 ${SIDE_LABEL[o.side]} 완료 (${formatWon(o.filledPrice ?? o.price)})`

market.subscribe(() => {
  trading.onQuotes(market.getState().quotes).forEach((o) => pushToast(fillMessage(o)))
})

// ── React 훅
export const useMarket = () => useSyncExternalStore(market.subscribe, market.getState)
export const useMarketStatus = () => useSyncExternalStore(market.subscribe, market.getStatus)
export const useAccount = () => useSyncExternalStore(trading.subscribe, trading.getState)
export const useToasts = () => useSyncExternalStore(subscribeToasts, () => toasts)

// ── 화면에서 부르는 동작
export function placeOrder(stock: Stock, side: Side, type: OrderType, price: number, quantity: number) {
  const marketPrice = market.getState().quotes[stock.code]?.price
  if (marketPrice === undefined) return
  const result = trading.placeOrder(stock, side, type, price, quantity, marketPrice)
  if (!result.ok) {
    pushToast(result.message)
    return
  }
  const o = result.order
  pushToast(o.status === 'FILLED' ? fillMessage(o) : `${o.name} ${o.quantity}주 ${SIDE_LABEL[side]} 주문을 접수했어요 (${formatWon(o.price)})`)
}

export function cancelOrder(id: number) {
  if (trading.cancel(id)) pushToast('주문을 취소했어요')
}
