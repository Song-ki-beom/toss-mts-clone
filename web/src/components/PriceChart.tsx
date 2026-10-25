import { useState } from 'react'
import type { Candle } from '../domain/models'
import { formatPrice } from '../domain/format'
import { Card, Segmented, useWidth } from './common'

type Mode = 'LINE' | 'CANDLE'
const MODES = ['LINE', 'CANDLE'] as const
const MODE_LABEL: Record<Mode, string> = { LINE: '라인', CANDLE: '캔들' }
const HEIGHT = 300
const PAD_Y = 16
const AXIS_W = 64

export function PriceChart({ candles, label, up }: { candles: Candle[]; label: string; up: boolean }) {
  const [mode, setMode] = useState<Mode>('LINE')
  const [ref, width] = useWidth<HTMLDivElement>()

  return (
    <Card title="차트" action={<Segmented options={MODES} value={mode} onChange={setMode} labels={MODE_LABEL} />} className="chart-card">
      <div className="muted small chart-label">{label}</div>
      <div ref={ref} className="chart-box">
        {width > 0 && candles.length > 1 ? (
          mode === 'LINE' ? <LineChart candles={candles} width={width} up={up} /> : <CandleChart candles={candles} width={width} />
        ) : (
          <p className="empty">차트 데이터가 없어요.</p>
        )}
      </div>
    </Card>
  )
}

function scale(candles: Candle[], width: number, useHighLow: boolean) {
  const max = Math.max(...candles.map((c) => (useHighLow ? c.high : c.close)))
  const min = Math.min(...candles.map((c) => (useHighLow ? c.low : c.close)))
  const range = Math.max(max - min, 1)
  const plotW = width - AXIS_W
  const y = (p: number) => PAD_Y + (1 - (p - min) / range) * (HEIGHT - PAD_Y * 2)
  return { max, min, plotW, y }
}

function Axis({ max, min, plotW, y }: ReturnType<typeof scale>) {
  const ticks = [max, (max + min) / 2, min]
  return (
    <g className="axis">
      {ticks.map((t, i) => (
        <g key={i}>
          <line x1={0} x2={plotW} y1={y(t)} y2={y(t)} className="grid" />
          <text x={plotW + 8} y={y(t) + 4}>{formatPrice(Math.round(t))}</text>
        </g>
      ))}
    </g>
  )
}

function LineChart({ candles, width, up }: { candles: Candle[]; width: number; up: boolean }) {
  const s = scale(candles, width, false)
  const step = s.plotW / (candles.length - 1)
  const pts = candles.map((c, i) => [i * step, s.y(c.close)] as const)
  const line = pts.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(1)},${y.toFixed(1)}`).join(' ')
  const area = `${line} L${s.plotW},${HEIGHT} L0,${HEIGHT} Z`
  const color = up ? 'var(--up)' : 'var(--down)'
  const [lx, ly] = pts[pts.length - 1]
  return (
    <svg width={width} height={HEIGHT} role="img" aria-label="가격 라인 차트">
      <defs>
        <linearGradient id="area" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity={0.18} />
          <stop offset="100%" stopColor={color} stopOpacity={0} />
        </linearGradient>
      </defs>
      <Axis {...s} />
      <path d={area} fill="url(#area)" />
      <path d={line} fill="none" stroke={color} strokeWidth={2.5} strokeLinejoin="round" strokeLinecap="round" />
      <circle cx={lx} cy={ly} r={4.5} fill={color} />
    </svg>
  )
}

function CandleChart({ candles, width }: { candles: Candle[]; width: number }) {
  const s = scale(candles, width, true)
  const slot = s.plotW / candles.length
  const body = Math.max(slot * 0.6, 1)
  return (
    <svg width={width} height={HEIGHT} role="img" aria-label="캔들 차트">
      <Axis {...s} />
      {candles.map((c, i) => {
        const color = c.close >= c.open ? 'var(--up)' : 'var(--down)'
        const cx = slot * i + slot / 2
        const top = s.y(Math.max(c.open, c.close))
        const bottom = s.y(Math.min(c.open, c.close))
        return (
          <g key={i}>
            <line x1={cx} x2={cx} y1={s.y(c.high)} y2={s.y(c.low)} stroke={color} strokeWidth={1} />
            <rect x={cx - body / 2} y={top} width={body} height={Math.max(bottom - top, 1)} fill={color} rx={1} />
          </g>
        )
      })}
    </svg>
  )
}
