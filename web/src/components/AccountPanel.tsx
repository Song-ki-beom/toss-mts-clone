import { useState } from 'react'
import { ORDER_TYPE_LABEL, SIDE_LABEL, STATUS_LABEL, type Account, type Quote } from '../domain/models'
import { Fees } from '../domain/tradingEngine'
import { formatChange, formatPrice, formatTime, formatWon, tone } from '../domain/format'
import { Avatar, Card, Segmented } from './common'

/** 홈의 "내 투자" 요약과 같은 계산 (모바일 앱 AccountSummary.kt) */
export function summarize(account: Account, quotes: Record<string, Quote>) {
  const holdings = Object.values(account.holdings)
  const reserved = account.orders
    .filter((o) => o.status === 'PENDING' && o.side === 'BUY')
    .reduce((s, o) => s + Fees.buyCost(o.price, o.quantity), 0)
  const evalAmount = holdings.reduce((s, h) => s + (quotes[h.code]?.price ?? h.avgPrice) * h.quantity, 0)
  const buyAmount = holdings.reduce((s, h) => s + h.avgPrice * h.quantity, 0)
  const pnl = evalAmount - buyAmount
  return {
    cash: account.cash,
    reserved,
    evalAmount,
    buyAmount,
    pnl,
    pnlRate: buyAmount > 0 ? (pnl * 100) / buyAmount : 0,
    total: account.cash + reserved + evalAmount,
  }
}

export function AssetSummary({ account, quotes }: { account: Account; quotes: Record<string, Quote> }) {
  const s = summarize(account, quotes)
  return (
    <Card className="asset-card">
      <span className="muted">내 투자</span>
      <strong className="asset-total">{formatWon(s.total)}</strong>
      <span className={tone(s.pnl)}>{s.buyAmount === 0 ? '아직 투자한 주식이 없어요' : formatChange(s.pnl, s.pnlRate)}</span>
      <dl className="asset-rows">
        <div>
          <dt>주문 가능 금액</dt>
          <dd>{formatWon(s.cash)}</dd>
        </div>
        {s.reserved > 0 && (
          <div>
            <dt>주문 대기 금액</dt>
            <dd>{formatWon(s.reserved)}</dd>
          </div>
        )}
        <div>
          <dt>주식 평가 금액</dt>
          <dd>{formatWon(s.evalAmount)}</dd>
        </div>
      </dl>
    </Card>
  )
}

type Tab = 'HOLDINGS' | 'ORDERS'
const TABS = ['HOLDINGS', 'ORDERS'] as const

export function AccountPanel({ account, quotes, onSelect, onCancel }: {
  account: Account
  quotes: Record<string, Quote>
  onSelect: (code: string) => void
  onCancel: (orderId: number) => void
}) {
  const [tab, setTab] = useState<Tab>('HOLDINGS')
  const holdings = Object.values(account.holdings)
  const labels: Record<Tab, string> = { HOLDINGS: `내 주식 ${holdings.length}`, ORDERS: `주문 내역 ${account.orders.length}` }

  return (
    <Card title={<Segmented options={TABS} value={tab} onChange={setTab} labels={labels} />} className="account-panel">
      {tab === 'HOLDINGS' ? (
        holdings.length === 0 ? (
          <p className="empty">보유한 주식이 없어요. 오른쪽 주문창에서 구매해 보세요.</p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>종목</th>
                <th>보유</th>
                <th>평균단가</th>
                <th>현재가</th>
                <th>평가 금액</th>
                <th>평가 손익</th>
              </tr>
            </thead>
            <tbody>
              {holdings.map((h) => {
                const price = quotes[h.code]?.price ?? h.avgPrice
                const pnl = (price - h.avgPrice) * h.quantity
                return (
                  <tr key={h.code} onClick={() => onSelect(h.code)} className="clickable">
                    <td>
                      <span className="cell-name">
                        <Avatar name={h.name} size={28} />
                        {h.name}
                      </span>
                    </td>
                    <td>{formatPrice(h.quantity)}주</td>
                    <td>{formatPrice(h.avgPrice)}</td>
                    <td>{formatPrice(price)}</td>
                    <td>{formatWon(price * h.quantity)}</td>
                    <td className={tone(pnl)}>{formatChange(pnl, ((price - h.avgPrice) * 100) / h.avgPrice)}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )
      ) : account.orders.length === 0 ? (
        <p className="empty">아직 주문한 내역이 없어요.</p>
      ) : (
        <table className="table">
          <thead>
            <tr>
              <th>시각</th>
              <th>종목</th>
              <th>구분</th>
              <th>가격</th>
              <th>수량</th>
              <th>상태</th>
            </tr>
          </thead>
          <tbody>
            {account.orders.map((o) => (
              <tr key={o.id}>
                <td className="muted">{formatTime(o.time)}</td>
                <td>{o.name}</td>
                <td className={o.side === 'BUY' ? 'up' : 'down'}>
                  {ORDER_TYPE_LABEL[o.type]} {SIDE_LABEL[o.side]}
                </td>
                <td>{formatWon(o.filledPrice ?? o.price)}</td>
                <td>{formatPrice(o.quantity)}주</td>
                <td>
                  <span className={`status ${o.status.toLowerCase()}`}>{STATUS_LABEL[o.status]}</span>
                  {o.status === 'PENDING' && (
                    <button className="cancel" onClick={() => onCancel(o.id)}>
                      취소
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Card>
  )
}
