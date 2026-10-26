const nf = new Intl.NumberFormat('ko-KR')

export const formatPrice = (v: number) => nf.format(v)

export const formatWon = (v: number) => `${nf.format(v)}원`

export const formatSigned = (v: number) => (v > 0 ? '+' : '') + nf.format(v)

export const formatRate = (r: number) => `${r > 0 ? '+' : ''}${r.toFixed(2)}%`

/** "+1,200원 (1.71%)" */
export const formatChange = (change: number, rate: number) => `${formatSigned(change)}원 (${Math.abs(rate).toFixed(2)}%)`

export const formatTime = (millis: number) =>
  new Date(millis).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })

/** 차트 날짜 축 표기. 일·주봉은 "26.10.26", 월봉은 "2026.10", 연봉은 "2026" */
export function formatAxisDate(millis: number, unit: 'DAY' | 'WEEK' | 'MONTH' | 'YEAR'): string {
  const d = new Date(millis)
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  if (unit === 'YEAR') return `${yyyy}`
  if (unit === 'MONTH') return `${yyyy}.${mm}`
  return `${String(yyyy).slice(2)}.${mm}.${dd}`
}

/** 상승 up, 하락 down, 보합 flat — CSS 클래스 이름으로 쓴다 */
export const tone = (v: number) => (v > 0 ? 'up' : v < 0 ? 'down' : 'flat')
