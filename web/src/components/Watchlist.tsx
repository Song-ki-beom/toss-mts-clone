import { change, changeRate, type Quote, type Stock } from '../domain/models'
import { formatPrice, formatRate, tone } from '../domain/format'
import { Avatar, Card, useFlash } from './common'

interface Props {
  stocks: Stock[]
  quotes: Record<string, Quote>
  selected: string
  onSelect: (code: string) => void
  query: string
}

export function Watchlist({ stocks, quotes, selected, onSelect, query }: Props) {
  const q = query.trim().toLowerCase()
  const visible = stocks.filter((s) => !q || s.name.toLowerCase().includes(q) || s.code.includes(q))

  return (
    <Card title={`관심 종목 ${visible.length}`} className="watchlist">
      {visible.length === 0 && <p className="empty">"{query}"에 맞는 종목이 없어요.</p>}
      <ul>
        {visible.map((s) => {
          const quote = quotes[s.code]
          return quote ? (
            <WatchRow key={s.code} stock={s} quote={quote} active={s.code === selected} onClick={() => onSelect(s.code)} />
          ) : null
        })}
      </ul>
    </Card>
  )
}

function WatchRow({ stock, quote, active, onClick }: { stock: Stock; quote: Quote; active: boolean; onClick: () => void }) {
  const flash = useFlash(quote.price)
  const c = change(quote)
  return (
    <li>
      <button className={`watch-row ${active ? 'active' : ''}`} onClick={onClick}>
        <Avatar name={stock.name} />
        <span className="watch-name">
          <strong>{stock.name}</strong>
          <small>{stock.code}</small>
        </span>
        <span className={`watch-price ${flash}`}>
          <strong>{formatPrice(quote.price)}</strong>
          <small className={tone(c)}>{formatRate(changeRate(quote))}</small>
        </span>
      </button>
    </li>
  )
}
