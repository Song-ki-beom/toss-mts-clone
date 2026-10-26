import { MarketSimulator } from './marketSimulator'
import { buildHistory, type HistoryPeriod } from './history'
import { DEFAULT_WATCHLIST, type Candle, type MarketState, type Quote, type Stock } from './models'

/** 화면이 쓰는 시세 공급원. 브라우저 안 모의 시세(LocalFeed)와 중계 서버(RemoteFeed)가 있다 */
export interface MarketFeed {
  readonly stocks: Stock[]
  readonly candleLabel: string
  getState(): MarketState
  getStatus(): string
  subscribe(listener: () => void): () => void
  start(): void
  stop(): void
  history(code: string, period: HistoryPeriod, quote: Quote): Promise<Candle[]>
}

/** 서버 없이 브라우저 안에서 도는 모의 시세 */
export class LocalFeed implements MarketFeed {
  private readonly sim: MarketSimulator

  constructor(sim = new MarketSimulator()) {
    this.sim = sim
  }

  get stocks() { return this.sim.stocks }
  get candleLabel() { return this.sim.candleLabel }
  getState = () => this.sim.getState()
  getStatus = () => '모의 시세 · 실시간'
  subscribe = (listener: () => void) => this.sim.subscribe(listener)
  start() { this.sim.start() }
  stop() { this.sim.stop() }
  history(code: string, period: HistoryPeriod, quote: Quote) {
    return Promise.resolve(buildHistory(code, period, quote))
  }
}

// 서버 메시지 형식 (core 모듈 remote/MarketMessage.kt 와 같다)
interface MarketSnapshot {
  stocks: Stock[]
  state: MarketState
  status: string
  candleLabel: string
}
type ServerMessage =
  | { type: 'snapshot'; snapshot: MarketSnapshot }
  | { type: 'state'; state: MarketState; status: string }

interface SocketLike {
  /** 0 = 연결 중, 1 = 열림 (브라우저 WebSocket.readyState) */
  readyState?: number
  onopen: ((ev: unknown) => void) | null
  onmessage: ((ev: { data: unknown }) => void) | null
  onclose: ((ev: unknown) => void) | null
  onerror: ((ev: unknown) => void) | null
  close(): void
}

export interface RemoteFeedOptions {
  createSocket?: (url: string) => SocketLike
  fetchImpl?: typeof fetch
  /** 재연결 대기 시간 (ms). 실패할 때마다 두 배, 최대 30초 */
  retryBaseMs?: number
}

const EMPTY_STATE: MarketState = { quotes: {}, candles: {}, orderBooks: {} }

/** Ktor 중계 서버에 붙는 시세 공급원. WebSocket으로 실시간 시세, REST로 기간별 봉을 받는다 */
export class RemoteFeed implements MarketFeed {
  stocks: Stock[] = DEFAULT_WATCHLIST
  candleLabel = '봉당 5초'
  private state: MarketState = EMPTY_STATE
  private status = '서버 연결 중'
  private listeners = new Set<() => void>()
  private socket: SocketLike | null = null
  private retryMs: number
  private retryTimer: ReturnType<typeof setTimeout> | null = null
  private running = false
  private readonly createSocket: (url: string) => SocketLike
  private readonly fetchImpl: typeof fetch
  private readonly retryBaseMs: number

  constructor(private readonly baseUrl: string, options: RemoteFeedOptions = {}) {
    this.baseUrl = baseUrl.replace(/\/+$/, '')
    this.createSocket = options.createSocket ?? ((url) => new WebSocket(url) as unknown as SocketLike)
    this.fetchImpl = options.fetchImpl ?? ((...args) => fetch(...args))
    this.retryBaseMs = options.retryBaseMs ?? 1_000
    this.retryMs = this.retryBaseMs
  }

  getState = () => this.state
  getStatus = () => this.status

  subscribe = (listener: () => void) => {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  start() {
    if (this.running) return
    this.running = true
    this.connect()
  }

  stop() {
    this.running = false
    if (this.retryTimer) clearTimeout(this.retryTimer)
    this.retryTimer = null
    const socket = this.socket
    this.socket = null
    if (!socket) return
    socket.onmessage = null
    // 연결 맺는 도중에 끊으면 서버(Netty)가 업그레이드 처리 중 오류를 남기므로, 열린 뒤에 닫는다
    if (socket.readyState === 0) socket.onopen = () => socket.close()
    else socket.close()
  }

  /** 서버가 현재 시세를 알고 있으므로 quote는 쓰지 않는다 */
  async history(code: string, period: HistoryPeriod, _quote?: Quote): Promise<Candle[]> {
    const res = await this.fetchImpl(`${this.baseUrl}/api/stocks/${code}/history?period=${period}`)
    if (!res.ok) {
      const body = await res.json().catch(() => ({}))
      throw new Error((body as { error?: string }).error ?? `HTTP ${res.status}`)
    }
    return (await res.json()) as Candle[]
  }

  /** 서버 메시지 하나를 반영한다 (테스트에서 직접 부를 수 있다) */
  handleMessage(text: string) {
    const msg = JSON.parse(text) as ServerMessage
    if (msg.type === 'snapshot') {
      this.stocks = msg.snapshot.stocks
      this.candleLabel = msg.snapshot.candleLabel
      this.state = msg.snapshot.state
      this.status = `서버 · ${msg.snapshot.status}`
    } else if (msg.type === 'state') {
      this.state = msg.state
      this.status = `서버 · ${msg.status}`
    } else {
      return
    }
    this.emit()
  }

  private connect() {
    const url = this.baseUrl.replace(/^http/, 'ws') + '/ws/market'
    const socket = this.createSocket(url)
    this.socket = socket
    socket.onopen = () => {
      this.retryMs = this.retryBaseMs
    }
    socket.onmessage = (ev) => {
      if (typeof ev.data === 'string') this.handleMessage(ev.data)
    }
    socket.onerror = () => socket.close()
    socket.onclose = () => {
      if (this.socket !== socket || !this.running) return
      this.socket = null
      this.setStatus(`서버 연결 끊김 · ${Math.round(this.retryMs / 1000)}초 뒤 다시 연결`)
      this.retryTimer = setTimeout(() => this.connect(), this.retryMs)
      this.retryMs = Math.min(this.retryMs * 2, 30_000)
    }
  }

  private setStatus(status: string) {
    this.status = status
    this.emit()
  }

  private emit() {
    this.listeners.forEach((l) => l())
  }
}

/** VITE_API_URL 이 있으면 중계 서버, 없으면 브라우저 안 모의 시세 */
export function createFeed(apiUrl: string | undefined = import.meta.env.VITE_API_URL): MarketFeed {
  return apiUrl ? new RemoteFeed(apiUrl) : new LocalFeed()
}
