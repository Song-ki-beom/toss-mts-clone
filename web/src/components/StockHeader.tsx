import { change, changeRate, type Holding, type Quote, type Stock } from '../domain/models'
import { formatChange, formatPrice, formatWon, tone } from '../domain/format'
import { Avatar } from './common'

export function StockHeader({ stock, quote, holding }: { stock: Stock; quote: Quote; holding?: Holding }) {
  const c = change(quote)
  const pnl = holding ? (quote.price - holding.avgPrice) * holding.quantity : 0
  return (
    <section className="card stock-header">
      <div className="stock-title">
        <Avatar name={stock.name} size={44} />
        <div>
          <h1>{stock.name}</h1>
          <span className="muted">
            {stock.code} · {stock.market}
          </span>
        </div>
      </div>
      <div className="stock-price">
        <strong>{formatWon(quote.price)}</strong>
        <span>
          <span className="muted">어제보다 </span>
          <span className={tone(c)}>{formatChange(c, changeRate(quote))}</span>
        </span>
      </div>
      <dl className="stock-stats">
        <div>
          <dt>시작가</dt>
          <dd>{formatPrice(quote.open)}</dd>
        </div>
        <div>
          <dt>최고가</dt>
          <dd className="up">{formatPrice(quote.high)}</dd>
        </div>
        <div>
          <dt>최저가</dt>
          <dd className="down">{formatPrice(quote.low)}</dd>
        </div>
        <div>
          <dt>거래량</dt>
          <dd>{formatPrice(quote.volume)}</dd>
        </div>
      </dl>
      {holding && (
        <div className="holding-pill">
          <span>내 주식 {holding.quantity}주 · 평균 {formatWon(holding.avgPrice)}</span>
          <span className={tone(pnl)}>{formatChange(pnl, ((quote.price - holding.avgPrice) * 100) / holding.avgPrice)}</span>
        </div>
      )}
    </section>
  )
}
