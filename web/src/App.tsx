import { useEffect, useState } from 'react'
import { change } from './domain/models'
import { market, placeOrder, trading, useAccount, useMarket } from './state/store'
import { Watchlist } from './components/Watchlist'
import { StockHeader } from './components/StockHeader'
import { PriceChart } from './components/PriceChart'
import { OrderBookPanel } from './components/OrderBookPanel'
import { OrderPanel } from './components/OrderPanel'
import { Toasts } from './components/Toasts'

export default function App() {
  const state = useMarket()
  const account = useAccount()
  const [selected, setSelected] = useState(market.stocks[0].code)
  const [query, setQuery] = useState('')
  const [picked, setPicked] = useState<{ price: number } | null>(null)

  useEffect(() => {
    market.start()
    return () => market.stop()
  }, [])

  const stock = market.stocks.find((s) => s.code === selected)!
  const quote = state.quotes[selected]

  return (
    <div className="app">
      <header className="topbar">
        <div className="logo">MTS Clone</div>
        <input className="search" placeholder="종목 이름이나 코드로 검색" value={query} onChange={(e) => setQuery(e.target.value)} />
        <span className="status-chip">
          <i className="dot" /> 모의 시세 · 실시간
        </span>
      </header>

      <main className="layout">
        <aside className="col-left">
          <Watchlist stocks={market.stocks} quotes={state.quotes} selected={selected} onSelect={setSelected} query={query} />
        </aside>

        <section className="col-center">
          <StockHeader stock={stock} quote={quote} holding={account.holdings[selected]} />
          <PriceChart candles={state.candles[selected] ?? []} label={market.candleLabel} up={change(quote) >= 0} />
        </section>

        <aside className="col-right">
          <OrderPanel
            stock={stock}
            quote={quote}
            picked={picked}
            maxBuyQuantity={(p) => trading.maxBuyQuantity(p)}
            sellableQuantity={trading.sellableQuantity(selected)}
            onSubmit={(side, type, price, qty) => placeOrder(stock, side, type, price, qty)}
          />
          <OrderBookPanel book={state.orderBooks[selected]} quote={quote} onPick={(price) => setPicked({ price })} />
        </aside>
      </main>
      <Toasts />
    </div>
  )
}
