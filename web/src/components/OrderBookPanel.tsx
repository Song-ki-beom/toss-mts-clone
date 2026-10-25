import type { OrderBook, OrderBookLevel, Quote } from '../domain/models'
import { formatPrice, formatRate, tone } from '../domain/format'
import { Card } from './common'

export function OrderBookPanel({ book, quote, onPick }: { book?: OrderBook; quote: Quote; onPick: (price: number) => void }) {
  if (!book) return null
  const maxQty = Math.max(1, ...[...book.asks, ...book.bids].map((l) => l.quantity))
  return (
    <Card title="호가" action={<span className="muted small">가격을 누르면 주문창에 입력돼요</span>} className="orderbook">
      <div className="ob-head">
        <span>판매 대기</span>
        <span>가격</span>
        <span>구매 대기</span>
      </div>
      <div className="ob-rows">
        {book.asks.map((l) => (
          <Row key={`a${l.price}`} level={l} side="ask" quote={quote} maxQty={maxQty} onPick={onPick} />
        ))}
        {book.bids.map((l) => (
          <Row key={`b${l.price}`} level={l} side="bid" quote={quote} maxQty={maxQty} onPick={onPick} />
        ))}
      </div>
    </Card>
  )
}

function Row({ level, side, quote, maxQty, onPick }: {
  level: OrderBookLevel
  side: 'ask' | 'bid'
  quote: Quote
  maxQty: number
  onPick: (p: number) => void
}) {
  const diff = level.price - quote.prevClose
  const pct = `${Math.max(4, (level.quantity / maxQty) * 100)}%`
  return (
    <button className={`ob-row ${side} ${level.price === quote.price ? 'current' : ''}`} onClick={() => onPick(level.price)}>
      <span className="ob-qty left">
        {side === 'ask' && (
          <>
            <i className="bar" style={{ width: pct }} />
            <em>{formatPrice(level.quantity)}</em>
          </>
        )}
      </span>
      <span className={`ob-price ${tone(diff)}`}>
        <strong>{formatPrice(level.price)}</strong>
        <small>{formatRate((diff * 100) / quote.prevClose)}</small>
      </span>
      <span className="ob-qty right">
        {side === 'bid' && (
          <>
            <i className="bar" style={{ width: pct }} />
            <em>{formatPrice(level.quantity)}</em>
          </>
        )}
      </span>
    </button>
  )
}
