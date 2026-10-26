import { afterEach, describe, expect, it, vi } from 'vitest'
import { LocalFeed, RemoteFeed, createFeed } from './feed'
import type { MarketState } from './models'

class FakeSocket {
  static last: FakeSocket | null = null
  readyState = 1
  onopen: ((ev: unknown) => void) | null = null
  onmessage: ((ev: { data: unknown }) => void) | null = null
  onclose: ((ev: unknown) => void) | null = null
  onerror: ((ev: unknown) => void) | null = null
  closed = false
  constructor(public url: string) {
    FakeSocket.last = this
  }
  close() {
    this.closed = true
  }
}

const state = (price: number): MarketState => ({
  quotes: { '005930': { code: '005930', price, prevClose: 70_000, open: 70_000, high: price, low: price, volume: 1 } },
  candles: {},
  orderBooks: {},
})

describe('RemoteFeed', () => {
  afterEach(() => vi.useRealTimers())

  it('http 주소를 ws 주소로 바꿔 /ws/market 에 연결한다', () => {
    const feed = new RemoteFeed('https://relay.example.com/', { createSocket: (u) => new FakeSocket(u) })
    feed.start()
    expect(FakeSocket.last!.url).toBe('wss://relay.example.com/ws/market')
    feed.stop()
    expect(FakeSocket.last!.closed).toBe(true)
  })

  it('연결 중에 멈추면 연결이 열린 뒤에 닫는다', () => {
    const feed = new RemoteFeed('http://localhost:8080', { createSocket: (u) => new FakeSocket(u) })
    feed.start()
    const socket = FakeSocket.last!
    socket.readyState = 0
    feed.stop()
    expect(socket.closed).toBe(false)
    socket.onopen!({})
    expect(socket.closed).toBe(true)
  })

  it('스냅샷과 이후 시세 메시지를 반영하고 구독자에게 알린다', () => {
    const feed = new RemoteFeed('http://localhost:8080', { createSocket: (u) => new FakeSocket(u) })
    let calls = 0
    feed.subscribe(() => calls++)
    feed.start()

    FakeSocket.last!.onmessage!({
      data: JSON.stringify({
        type: 'snapshot',
        snapshot: { stocks: [{ code: '005930', name: '삼성전자', market: 'KOSPI' }], state: state(71_000), status: 'KIS 모의 · 실시간', candleLabel: '1분봉' },
      }),
    })
    expect(feed.stocks).toHaveLength(1)
    expect(feed.candleLabel).toBe('1분봉')
    expect(feed.getStatus()).toBe('서버 · KIS 모의 · 실시간')
    expect(feed.getState().quotes['005930'].price).toBe(71_000)

    FakeSocket.last!.onmessage!({ data: JSON.stringify({ type: 'state', state: state(71_500), status: 'KIS 모의 · 실시간' }) })
    expect(feed.getState().quotes['005930'].price).toBe(71_500)
    expect(calls).toBe(2)
  })

  it('연결이 끊기면 점점 늘어나는 간격으로 다시 연결한다', () => {
    vi.useFakeTimers()
    const feed = new RemoteFeed('http://localhost:8080', { createSocket: (u) => new FakeSocket(u), retryBaseMs: 1_000 })
    feed.start()
    const first = FakeSocket.last!
    first.onclose!({})
    expect(feed.getStatus()).toContain('1초 뒤 다시 연결')

    vi.advanceTimersByTime(1_000)
    const second = FakeSocket.last!
    expect(second).not.toBe(first)
    second.onclose!({})
    expect(feed.getStatus()).toContain('2초 뒤 다시 연결')
    feed.stop()
  })

  it('기간별 봉을 REST로 받고, 오류면 서버 메시지로 실패한다', async () => {
    const fetchImpl = vi.fn(async (url: string) =>
      url.includes('period=DAY')
        ? new Response(JSON.stringify([{ open: 1, high: 2, low: 1, close: 2, volume: 3, time: 0 }]), { status: 200 })
        : new Response(JSON.stringify({ error: '아직 시세를 불러오는 중이에요' }), { status: 503 }),
    ) as unknown as typeof fetch
    const feed = new RemoteFeed('http://localhost:8080', { fetchImpl })
    const quote = state(1).quotes['005930']

    await expect(feed.history('005930', 'DAY', quote)).resolves.toHaveLength(1)
    expect((fetchImpl as unknown as ReturnType<typeof vi.fn>).mock.calls[0][0]).toBe('http://localhost:8080/api/stocks/005930/history?period=DAY')
    await expect(feed.history('005930', 'YEAR', quote)).rejects.toThrow('아직 시세를 불러오는 중이에요')
  })
})

describe('createFeed', () => {
  it('서버 주소가 없으면 브라우저 모의 시세를 쓴다', () => {
    expect(createFeed(undefined)).toBeInstanceOf(LocalFeed)
    expect(createFeed('http://localhost:8080')).toBeInstanceOf(RemoteFeed)
  })
})
