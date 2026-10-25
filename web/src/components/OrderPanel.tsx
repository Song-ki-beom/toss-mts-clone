import { useEffect, useState } from 'react'
import { ORDER_TYPE_LABEL, SIDE_LABEL, tickSize, type OrderType, type Quote, type Side, type Stock } from '../domain/models'
import { formatPrice, formatWon } from '../domain/format'
import { Card, Segmented } from './common'

const SIDES = ['BUY', 'SELL'] as const
const TYPES = ['LIMIT', 'MARKET'] as const

interface Props {
  stock: Stock
  quote: Quote
  /** 호가창에서 고른 가격. 같은 가격을 다시 눌러도 반영되도록 객체로 받는다 */
  picked: { price: number } | null
  maxBuyQuantity: (price: number) => number
  sellableQuantity: number
  onSubmit: (side: Side, type: OrderType, price: number, quantity: number) => void
}

export function OrderPanel({ stock, quote, picked, maxBuyQuantity, sellableQuantity, onSubmit }: Props) {
  const [side, setSide] = useState<Side>('BUY')
  const [type, setType] = useState<OrderType>('LIMIT')
  const [price, setPrice] = useState(quote.price)
  const [qtyText, setQtyText] = useState('1')

  // 종목이 바뀌면 현재가로 초기화
  useEffect(() => {
    setPrice(quote.price)
    setQtyText('1')
  }, [stock.code])

  // 호가를 누르면 지정가로 그 가격을 넣는다
  useEffect(() => {
    if (picked) {
      setType('LIMIT')
      setPrice(picked.price)
    }
  }, [picked])

  const quantity = Number.parseInt(qtyText, 10) || 0
  const effectivePrice = type === 'MARKET' ? quote.price : price
  const available = side === 'BUY' ? maxBuyQuantity(effectivePrice) : sellableQuantity
  const sideTone = side === 'BUY' ? 'up' : 'down'

  return (
    <Card title={`${stock.name} 주문`} className="order-panel">
      <Segmented options={SIDES} value={side} onChange={setSide} labels={SIDE_LABEL} tone={(s) => (s === 'BUY' ? 'up' : 'down')} />

      <div className="field">
        <div className="field-head">
          <label>가격</label>
          <Segmented options={TYPES} value={type} onChange={setType} labels={ORDER_TYPE_LABEL} />
        </div>
        <div className={`stepper ${type === 'MARKET' ? 'disabled' : ''}`}>
          <button aria-label="가격 내리기" disabled={type === 'MARKET'} onClick={() => setPrice((p) => Math.max(1, p - tickSize(p - 1)))}>−</button>
          <span>{type === 'MARKET' ? '시장가로 바로' : formatWon(price)}</span>
          <button aria-label="가격 올리기" disabled={type === 'MARKET'} onClick={() => setPrice((p) => p + tickSize(p))}>+</button>
        </div>
      </div>

      <div className="field">
        <div className="field-head">
          <label htmlFor="qty">수량</label>
          <span className="muted small">{SIDE_LABEL[side]} 가능 {formatPrice(available)}주</span>
        </div>
        <div className="stepper">
          <button aria-label="수량 줄이기" onClick={() => setQtyText(String(Math.max(0, quantity - 1)))}>−</button>
          <input
            id="qty"
            inputMode="numeric"
            value={qtyText}
            onChange={(e) => setQtyText(e.target.value.replace(/\D/g, '').slice(0, 7))}
          />
          <button aria-label="수량 늘리기" onClick={() => setQtyText(String(quantity + 1))}>+</button>
        </div>
        <div className="quick">
          {[10, 25, 50, 100].map((pct) => (
            <button key={pct} onClick={() => setQtyText(String(Math.floor((available * pct) / 100)))}>
              {pct === 100 ? '최대' : `${pct}%`}
            </button>
          ))}
        </div>
      </div>

      <dl className="summary">
        <div>
          <dt>주문 방식</dt>
          <dd>{ORDER_TYPE_LABEL[type]}</dd>
        </div>
        <div>
          <dt>총 주문 금액</dt>
          <dd><strong>{formatWon(effectivePrice * quantity)}</strong></dd>
        </div>
      </dl>

      <button className={`submit ${sideTone}`} disabled={quantity <= 0} onClick={() => onSubmit(side, type, price, quantity)}>
        {formatPrice(quantity)}주 {SIDE_LABEL[side]}하기
      </button>
    </Card>
  )
}
