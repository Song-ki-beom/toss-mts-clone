import { useEffect, useState } from 'react'
import { cancelOrder, market, placeOrder, trading, useAccount, useMarket, useMarketStatus } from './state/store'
import { AccountPanel, AssetSummary } from './components/AccountPanel'
import { Watchlist } from './components/Watchlist'
import { StockHeader } from './components/StockHeader'
import { PriceChart } from './components/PriceChart'
import { OrderBookPanel } from './components/OrderBookPanel'
import { OrderPanel } from './components/OrderPanel'
import { Toasts } from './components/Toasts'

export default function App() {
  const state = useMarket()
  const status = useMarketStatus()
  const account = useAccount()
  const [selected, setSelected] = useState(market.stocks[0].code)
  const [query, setQuery] = useState('')
  const [picked, setPicked] = useState<{ price: number } | null>(null)

  useEffect(() => {
    market.start()
    return () => market.stop()
  }, [])

  const stock = market.stocks.find((s) => s.code === selected) ?? market.stocks[0]
  const quote = state.quotes[stock.code]

  const header = (
    <header className="topbar">
      <div className="logo">MTS Clone</div>
      <input className="search" placeholder="종목 이름이나 코드로 검색" value={query} onChange={(e) => setQuery(e.target.value)} />
      <span className="status-chip">
        <i className="dot" /> {status}
      </span>
    </header>
  )

  // 서버에서 첫 시세가 오기 전
  if (!quote) {
    return (
      <div className="app">
        {header}
        <main className="loading">
          <strong>시세를 불러오고 있어요</strong>
          <span className="muted">{status}</span>
        </main>
      </div>
    )
  }

  return (
    <div className="app">
      {header}

      <main className="layout">
        <aside className="col-left">
          <AssetSummary account={account} quotes={state.quotes} />
          <Watchlist stocks={market.stocks} quotes={state.quotes} selected={stock.code} onSelect={setSelected} query={query} />
        </aside>

        <section className="col-center">
          <StockHeader stock={stock} quote={quote} holding={account.holdings[stock.code]} />
          <PriceChart
            code={stock.code}
            quote={quote}
            liveCandles={state.candles[stock.code] ?? []}
            liveLabel={market.candleLabel}
            loadHistory={(period) => market.history(stock.code, period, quote)}
          />
          <AccountPanel account={account} quotes={state.quotes} onSelect={setSelected} onCancel={cancelOrder} />
        </section>

        <aside className="col-right">
          <OrderPanel
            stock={stock}
            quote={quote}
            picked={picked}
            maxBuyQuantity={(p) => trading.maxBuyQuantity(p)}
            sellableQuantity={trading.sellableQuantity(stock.code)}
            onSubmit={(side, type, price, qty) => placeOrder(stock, side, type, price, qty)}
          />
          <OrderBookPanel book={state.orderBooks[stock.code]} quote={quote} onPick={(price) => setPicked({ price })} />
        </aside>
      </main>
      <Toasts />
    </div>
  )
}
