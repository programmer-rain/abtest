/**
 * 埋点上报：批量聚合 + sendBeacon + 降级兜底。
 *
 * 教学说明：
 *  - 曝光/点击先入队，满 10 条或 3 秒定时批量上报，减少请求数；
 *  - 优先用 navigator.sendBeacon：页面关闭/切后台也能把数据发出去，不阻塞主线程；
 *  - 不支持 sendBeacon 时降级为 fetch(..., keepalive: true)；
 *  - 曝光在会话内用 Set 去重，防止滚动来回触发导致曝光虚高。
 */
import type { TrackEvent } from './types'

interface TrackItem {
  expKey: string
  variant: string
  userId: string
  event: TrackEvent
  configVersion: number
  ts: number
}

const queue: TrackItem[] = []
const exposed = new Set<string>()
let timer: number | null = null

/** 曝光上报（会话内同一实验同一变体只报一次） */
export function reportExposure(
  expKey: string,
  variant: string,
  userId: string,
  configVersion: number,
): void {
  const dedupKey = `${expKey}:${variant}`
  if (exposed.has(dedupKey)) {
    return
  }
  exposed.add(dedupKey)
  enqueue(expKey, variant, userId, 'expose', configVersion)
}

/** 点击上报（不去重限制，交给后端幂等） */
export function reportClick(
  expKey: string,
  variant: string,
  userId: string,
  configVersion: number,
): void {
  enqueue(expKey, variant, userId, 'click', configVersion)
}

function enqueue(
  expKey: string,
  variant: string,
  userId: string,
  event: TrackEvent,
  configVersion: number,
): void {
  queue.push({ expKey, variant, userId, event, configVersion, ts: Date.now() })
  // 满 10 条立即发，否则 3 秒后定时发
  if (queue.length >= 10) {
    flush()
  } else if (timer === null) {
    timer = window.setTimeout(flush, 3000)
  }
}

/** 批量发送当前队列 */
export function flush(): void {
  if (timer !== null) {
    clearTimeout(timer)
    timer = null
  }
  if (queue.length === 0) {
    return
  }
  const events = queue.splice(0, queue.length)
  const body = JSON.stringify({ events })

  if (typeof navigator.sendBeacon === 'function') {
    const blob = new Blob([body], { type: 'application/json' })
    const ok = navigator.sendBeacon('/api/ab/track', blob)
    if (ok) {
      return
    }
  }
  // 降级：keepalive 保证页面卸载时请求不被取消
  fetch('/api/ab/track', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body,
    keepalive: true,
  }).catch(() => {
    /* 网络失败：教学 demo 里丢弃；生产应落 localStorage 离线补发 */
  })
}

// 页面隐藏/关闭前兜底上报
window.addEventListener('pagehide', flush)
