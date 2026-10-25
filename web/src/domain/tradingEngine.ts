import type { Account, Order, OrderType, Quote, Side, Stock } from './models'

/** 매매 수수료. 세금은 단순화를 위해 생략했다 */
export const Fees = {
  RATE: 0.00015,
  of(amount: number): number {
    return Math.round(amount * this.RATE)
  },
  buyCost(price: number, quantity: number): number {
    const amount = price * quantity
    return amount + this.of(amount)
  },
}

export type OrderResult = { ok: true; order: Order } | { ok: false; message: string }

type Listener = () => void

/**
 * 모의 계좌와 주문 체결 (모바일 앱 TradingEngine.kt 이식).
 * 구매 주문은 접수 시점에 대금을 묶어두고, 체결가가 더 낮으면 차액을 돌려준다.
 */
export class TradingEngine {
  private account: Account
  private listeners = new Set<Listener>()
  private nextId = 1

  constructor(initialCash = 10_000_000, private readonly now: () => number = Date.now) {
    this.account = { cash: initialCash, holdings: {}, orders: [] }
  }

  getState = (): Account => this.account

  subscribe = (listener: Listener): (() => void) => {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  placeOrder(stock: Stock, side: Side, type: OrderType, limitPrice: number, quantity: number, marketPrice: number): OrderResult {
    if (!Number.isInteger(quantity) || quantity <= 0) return { ok: false, message: '수량을 입력해 주세요' }
    const orderPrice = type === 'MARKET' ? marketPrice : limitPrice
    if (orderPrice <= 0) return { ok: false, message: '가격을 확인해 주세요' }

    const acc = this.account
    if (side === 'BUY' && Fees.buyCost(orderPrice, quantity) > acc.cash) return { ok: false, message: '주문 가능 금액이 부족해요' }
    if (side === 'SELL' && quantity > this.sellableQuantityOf(acc, stock.code)) return { ok: false, message: '판매 가능 수량이 부족해요' }

    const order: Order = {
      id: this.nextId++,
      code: stock.code,
      name: stock.name,
      side,
      type,
      price: orderPrice,
      quantity,
      status: 'PENDING',
      filledPrice: null,
      time: this.now(),
    }
    const cash = side === 'BUY' ? acc.cash - Fees.buyCost(orderPrice, quantity) : acc.cash
    let next: Account = { ...acc, cash, orders: [order, ...acc.orders] }

    const marketable = type === 'MARKET' || (side === 'BUY' && marketPrice <= orderPrice) || (side === 'SELL' && marketPrice >= orderPrice)
    if (marketable) next = this.fill(next, order, marketPrice)

    this.setAccount(next)
    return { ok: true, order: next.orders.find((o) => o.id === order.id)! }
  }

  /** 새 시세마다 미체결 지정가 주문을 확인해 체결한다. 이번에 체결된 주문을 돌려준다 */
  onQuotes(quotes: Record<string, Quote>): Order[] {
    let acc = this.account
    const filled: Order[] = []
    for (const order of acc.orders.filter((o) => o.status === 'PENDING')) {
      const price = quotes[order.code]?.price
      if (price === undefined) continue
      const hit = order.side === 'BUY' ? price <= order.price : price >= order.price
      if (hit) {
        acc = this.fill(acc, order, order.price)
        filled.push(acc.orders.find((o) => o.id === order.id)!)
      }
    }
    if (filled.length) this.setAccount(acc)
    return filled
  }

  cancel(orderId: number): boolean {
    const acc = this.account
    const order = acc.orders.find((o) => o.id === orderId && o.status === 'PENDING')
    if (!order) return false
    const cash = order.side === 'BUY' ? acc.cash + Fees.buyCost(order.price, order.quantity) : acc.cash
    this.setAccount({ ...acc, cash, orders: acc.orders.map((o) => (o.id === orderId ? { ...o, status: 'CANCELED' } : o)) })
    return true
  }

  maxBuyQuantity(price: number): number {
    if (price <= 0) return 0
    let qty = Math.floor(this.account.cash / (price * (1 + Fees.RATE)))
    while (qty > 0 && Fees.buyCost(price, qty) > this.account.cash) qty--
    return qty
  }

  sellableQuantity(code: string): number {
    return this.sellableQuantityOf(this.account, code)
  }

  private sellableQuantityOf(acc: Account, code: string): number {
    const held = acc.holdings[code]?.quantity ?? 0
    const pendingSell = acc.orders
      .filter((o) => o.code === code && o.side === 'SELL' && o.status === 'PENDING')
      .reduce((s, o) => s + o.quantity, 0)
    return held - pendingSell
  }

  private fill(acc: Account, order: Order, price: number): Account {
    const holdings = { ...acc.holdings }
    const holding = holdings[order.code]
    let cash = acc.cash
    if (order.side === 'BUY') {
      cash += Fees.buyCost(order.price, order.quantity) - Fees.buyCost(price, order.quantity)
      const heldQty = holding?.quantity ?? 0
      const qty = heldQty + order.quantity
      const avg = Math.floor(((holding?.avgPrice ?? 0) * heldQty + price * order.quantity) / qty)
      holdings[order.code] = { code: order.code, name: order.name, quantity: qty, avgPrice: avg }
    } else {
      const amount = price * order.quantity
      cash += amount - Fees.of(amount)
      const qty = (holding?.quantity ?? 0) - order.quantity
      if (holding && qty > 0) holdings[order.code] = { ...holding, quantity: qty }
      else delete holdings[order.code]
    }
    const orders = acc.orders.map((o) => (o.id === order.id ? { ...o, status: 'FILLED' as const, filledPrice: price } : o))
    return { cash, holdings, orders }
  }

  private setAccount(next: Account): void {
    this.account = next
    this.listeners.forEach((l) => l())
  }
}
