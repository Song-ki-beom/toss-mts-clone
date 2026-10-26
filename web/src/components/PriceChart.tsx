import { useEffect, useMemo, useRef, useState } from 'react'
import { change, changeRate, type Candle, type Quote } from '../domain/models'
import { PERIOD_LABEL, PERIOD_SPECS, PERIODS, periodReturn, withLivePrice, type HistoryPeriod, type Period } from '../domain/history'
import { formatAxisDate, formatPrice, formatRate, tone } from '../domain/format'
import { Card, Segmented, useWidth } from './common'

type Mode = 'LINE' | 'CANDLE'
const MODES = ['LINE', 'CANDLE'] as const
const MODE_LABEL: Record<Mode, string> = { LINE: '라인', CANDLE: '캔들' }
const PLOT_H = 280
const AXIS_H = 26
const PAD_Y = 16
const AXIS_W = 64

interface Props {
  code: string
  quote: Quote
  /** 실시간 탭에 쓰는 5초봉 */
  liveCandles: Candle[]
  liveLabel: string
  /** 일·주·월·년봉을 불러온다 (브라우저 모의 시세 또는 중계 서버) */
  loadHistory: (period: HistoryPeriod) => Promise<Candle[]>
}

type HistoryState = { key: string; candles: Candle[] | null; error: string | null }

export function PriceChart({ code, quote, liveCandles, liveLabel, loadHistory }: Props) {
  const [mode, setMode] = useState<Mode>('LINE')
  const [period, setPeriod] = useState<Period>('LIVE')
  const [ref, width] = useWidth<HTMLDivElement>()
  const [history, setHistory] = useState<HistoryState>({ key: '', candles: null, error: null })

  // 불러오는 함수는 렌더마다 새로 만들어지므로 최신 것을 ref로 들고, 종목·기간이 바뀔 때만 요청한다
  const loaderRef = useRef(loadHistory)
  loaderRef.current = loadHistory
  const key = `${code}:${period}`
  useEffect(() => {
    if (period === 'LIVE') return
    let cancelled = false
    setHistory({ key, candles: null, error: null })
    loaderRef.current(period).then(
      (candles) => !cancelled && setHistory({ key, candles, error: null }),
      (e: Error) => !cancelled && setHistory({ key, candles: null, error: e.message }),
    )
    return () => {
      cancelled = true
    }
  }, [key, period])

  // 마지막 봉에만 실시간 가격을 반영한다
  const loaded = history.key === key ? history : { key, candles: null, error: null }
  const candles = useMemo(() => {
    if (period === 'LIVE') return liveCandles
    return loaded.candles ? withLivePrice(loaded.candles, quote.price) : null
  }, [period, liveCandles, loaded.candles, quote.price])

  const ret = period === 'LIVE' ? changeRate(quote) : candles ? periodReturn(candles) : 0
  const up = period === 'LIVE' ? change(quote) >= 0 : ret >= 0
  const caption = period === 'LIVE' ? `${liveLabel} · 어제보다` : `${PERIOD_SPECS[period].caption} 동안`

  return (
    <Card title="차트" action={<Segmented options={MODES} value={mode} onChange={setMode} labels={MODE_LABEL} />} className="chart-card">
      <div className="chart-toolbar">
        <Segmented options={PERIODS} value={period} onChange={setPeriod} labels={PERIOD_LABEL} />
        <span className="chart-return">
          <span className="muted">{caption} </span>
          <strong className={tone(ret)}>{formatRate(ret)}</strong>
        </span>
      </div>
      <div ref={ref} className="chart-box">
        {width === 0 ? null : loaded.error && period !== 'LIVE' ? (
          <p className="empty">차트를 불러오지 못했어요. ({loaded.error})</p>
        ) : candles === null ? (
          <p className="empty">차트를 불러오는 중이에요</p>
        ) : candles.length > 1 ? (
          <Chart candles={candles} width={width} mode={mode} up={up} unit={period === 'LIVE' ? null : period} />
        ) : (
          <p className="empty">차트 데이터가 없어요.</p>
        )}
      </div>
    </Card>
  )
}

function Chart({ candles, width, mode, up, unit }: { candles: Candle[]; width: number; mode: Mode; up: boolean; unit: HistoryPeriod | null }) {
  const useHighLow = mode === 'CANDLE'
  const max = Math.max(...candles.map((c) => (useHighLow ? c.high : c.close)))
  const min = Math.min(...candles.map((c) => (useHighLow ? c.low : c.close)))
  const range = Math.max(max - min, 1)
  const plotW = width - AXIS_W
  const y = (p: number) => PAD_Y + (1 - (p - min) / range) * (PLOT_H - PAD_Y * 2)
  const slot = plotW / candles.length
  const cx = (i: number) => slot * i + slot / 2
  const height = PLOT_H + (unit ? AXIS_H : 0)

  return (
    <svg width={width} height={height} role="img" aria-label={mode === 'LINE' ? '가격 라인 차트' : '캔들 차트'}>
      <g className="axis">
        {[max, (max + min) / 2, min].map((t, i) => (
          <g key={i}>
            <line x1={0} x2={plotW} y1={y(t)} y2={y(t)} className="grid" />
            <text x={plotW + 8} y={y(t) + 4}>{formatPrice(Math.round(t))}</text>
          </g>
        ))}
        {unit && <DateAxis candles={candles} cx={cx} unit={unit} />}
      </g>
      {mode === 'LINE' ? <Line candles={candles} cx={cx} y={y} up={up} /> : <Candles candles={candles} cx={cx} y={y} slot={slot} />}
    </svg>
  )
}

/** 차트 아래 날짜 4개 */
function DateAxis({ candles, cx, unit }: { candles: Candle[]; cx: (i: number) => number; unit: HistoryPeriod }) {
  const n = candles.length
  const idx = [...new Set([0, Math.round((n - 1) / 3), Math.round(((n - 1) * 2) / 3), n - 1])]
  return (
    <>
      {idx.map((i, k) => (
        <text
          key={i}
          x={cx(i)}
          y={PLOT_H + 18}
          textAnchor={k === 0 ? 'start' : k === idx.length - 1 ? 'end' : 'middle'}
        >
          {candles[i].time ? formatAxisDate(candles[i].time!, unit) : ''}
        </text>
      ))}
    </>
  )
}

function Line({ candles, cx, y, up }: { candles: Candle[]; cx: (i: number) => number; y: (p: number) => number; up: boolean }) {
  const pts = candles.map((c, i) => [cx(i), y(c.close)] as const)
  const line = pts.map(([px, py], i) => `${i ? 'L' : 'M'}${px.toFixed(1)},${py.toFixed(1)}`).join(' ')
  const area = `${line} L${pts[pts.length - 1][0].toFixed(1)},${PLOT_H} L${pts[0][0].toFixed(1)},${PLOT_H} Z`
  const color = up ? 'var(--up)' : 'var(--down)'
  const [lx, ly] = pts[pts.length - 1]
  const gradientId = up ? 'area-up' : 'area-down'
  return (
    <>
      <defs>
        <linearGradient id={gradientId} x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity={0.18} />
          <stop offset="100%" stopColor={color} stopOpacity={0} />
        </linearGradient>
      </defs>
      <path d={area} fill={`url(#${gradientId})`} />
      <path d={line} fill="none" stroke={color} strokeWidth={2.5} strokeLinejoin="round" strokeLinecap="round" />
      <circle cx={lx} cy={ly} r={4.5} fill={color} />
    </>
  )
}

function Candles({ candles, cx, y, slot }: { candles: Candle[]; cx: (i: number) => number; y: (p: number) => number; slot: number }) {
  const body = Math.max(slot * 0.6, 1)
  return (
    <>
      {candles.map((c, i) => {
        const color = c.close >= c.open ? 'var(--up)' : 'var(--down)'
        const top = y(Math.max(c.open, c.close))
        const bottom = y(Math.min(c.open, c.close))
        return (
          <g key={i}>
            <line x1={cx(i)} x2={cx(i)} y1={y(c.high)} y2={y(c.low)} stroke={color} strokeWidth={1} />
            <rect x={cx(i) - body / 2} y={top} width={body} height={Math.max(bottom - top, 1)} fill={color} rx={1} />
          </g>
        )
      })}
    </>
  )
}
