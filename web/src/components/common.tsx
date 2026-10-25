import { useEffect, useRef, useState, type ReactNode } from 'react'

const AVATAR_COLORS = ['#3182F6', '#F04452', '#00B386', '#FF9F00', '#8B5CF6', '#14B8C4', '#E5508B', '#4E5968']

function hash(text: string): number {
  let h = 0
  for (const ch of text) h = (h * 31 + ch.charCodeAt(0)) | 0
  return Math.abs(h)
}

/** 종목 로고 대신 쓰는 이니셜 원 */
export function Avatar({ name, size = 36 }: { name: string; size?: number }) {
  const color = AVATAR_COLORS[hash(name) % AVATAR_COLORS.length]
  return (
    <span
      className="avatar"
      style={{ width: size, height: size, color, background: `${color}24`, fontSize: size * 0.4 }}
      aria-hidden
    >
      {name.slice(0, 1)}
    </span>
  )
}

export function Card({ title, action, children, className = '' }: { title?: ReactNode; action?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={`card ${className}`}>
      {(title || action) && (
        <div className="card-head">
          {title && <h2>{title}</h2>}
          {action}
        </div>
      )}
      {children}
    </section>
  )
}

/** 둥근 알약 모양 토글 */
export function Segmented<T extends string>({
  options, value, onChange, labels, tone,
}: {
  options: readonly T[]
  value: T
  onChange: (v: T) => void
  labels: Record<T, string>
  tone?: (v: T) => string
}) {
  return (
    <div className="segmented" role="tablist">
      {options.map((o) => (
        <button
          key={o}
          role="tab"
          aria-selected={o === value}
          className={`${o === value ? 'active' : ''} ${o === value && tone ? tone(o) : ''}`}
          onClick={() => onChange(o)}
        >
          {labels[o]}
        </button>
      ))}
    </div>
  )
}

/** 값이 오르면 빨강, 내리면 파랑으로 잠깐 배경을 깜빡인다 */
export function useFlash(value: number): string {
  const prev = useRef(value)
  const [flash, setFlash] = useState<{ dir: string; n: number }>({ dir: '', n: 0 })
  useEffect(() => {
    if (value !== prev.current) {
      setFlash((f) => ({ dir: value > prev.current ? 'flash-up' : 'flash-down', n: f.n + 1 }))
      prev.current = value
    }
  }, [value])
  // n을 번갈아 붙여 같은 방향으로 연속 변해도 애니메이션이 다시 시작되게 한다
  return flash.dir ? `${flash.dir} ${flash.n % 2 ? 'a' : 'b'}` : ''
}

/** 요소의 실제 폭을 따라가는 훅 (차트를 화면 크기에 맞추기 위해) */
export function useWidth<T extends HTMLElement>() {
  const ref = useRef<T>(null)
  const [width, setWidth] = useState(0)
  useEffect(() => {
    if (!ref.current) return
    const ro = new ResizeObserver(([entry]) => setWidth(Math.floor(entry.contentRect.width)))
    ro.observe(ref.current)
    return () => ro.disconnect()
  }, [])
  return [ref, width] as const
}
