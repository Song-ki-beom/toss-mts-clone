const nf = new Intl.NumberFormat('ko-KR')

export const formatPrice = (v: number) => nf.format(v)

export const formatWon = (v: number) => `${nf.format(v)}원`

export const formatSigned = (v: number) => (v > 0 ? '+' : '') + nf.format(v)

export const formatRate = (r: number) => `${r > 0 ? '+' : ''}${r.toFixed(2)}%`

/** "+1,200원 (1.71%)" */
export const formatChange = (change: number, rate: number) => `${formatSigned(change)}원 (${Math.abs(rate).toFixed(2)}%)`

export const formatTime = (millis: number) =>
  new Date(millis).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })

/** 상승 up, 하락 down, 보합 flat — CSS 클래스 이름으로 쓴다 */
export const tone = (v: number) => (v > 0 ? 'up' : v < 0 ? 'down' : 'flat')
