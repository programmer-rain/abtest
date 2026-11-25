/**
 * AB 状态客户端：首屏防闪烁（FOUC）的关键。
 *
 * 教学说明（防闪烁三件套）：
 *  ① localStorage 粘性缓存：二次访问同步读出上次的分组，网络失败也不跳组；
 *  ② 后台拉取最新分组并静默校正（SWR 思想）；
 *  ③ 拉取带超时兜底：超时就用缓存/对照组，绝不阻塞页面渲染。
 *
 * 生产环境更彻底的做法是 SSR/边缘节点直出 window.__AB_STATE__，
 * 这里用 localStorage 模拟同样的效果。
 */
import { reactive } from 'vue'
import type { ABState } from './types'

const STATE_KEY = 'ab_demo_state'
const USER_KEY = 'ab_demo_user'

/** 全局响应式状态，通过 Vue 插件 provide 给所有组件 */
export const abState = reactive<ABState>({ version: 0, assignments: {}, buckets: {} })

/** 当前演示用户 ID（localStorage 持久化，模拟真实登录态） */
export function getUserId(): string {
  let uid = localStorage.getItem(USER_KEY)
  if (!uid) {
    uid = 'demo_user_' + Math.random().toString(36).slice(2, 10)
    localStorage.setItem(USER_KEY, uid)
  }
  return uid
}

/** 换一个用户：演示"不同用户哈希到不同桶、分组互不相同" */
export function resetUserId(): string {
  localStorage.removeItem(USER_KEY)
  return getUserId()
}

/**
 * 初始化：先用缓存同步渲染，再后台校正。
 * 在 main.ts 里 await 它（带超时），保证挂载时分组已就绪 → 不闪烁。
 */
export async function initAb(timeoutMs = 500): Promise<void> {
  // ① 粘性缓存：同一用户同一实验永远是同一个 variant
  const cached = localStorage.getItem(STATE_KEY)
  if (cached) {
    try {
      Object.assign(abState, JSON.parse(cached) as ABState)
    } catch {
      /* 缓存损坏就忽略 */
    }
  }

  // ② + ③ 后台拉取 + 超时兜底
  try {
    const fresh = await Promise.race([fetchState(), timeoutReject(timeoutMs)])
    applyState(fresh)
  } catch {
    /* 超时/失败：保持缓存或走对照组兜底，页面照常渲染 */
  }
}

/** 配置变更后主动刷新（管理面板调流量/暂停后调用） */
export async function refreshAb(): Promise<void> {
  try {
    applyState(await fetchState())
  } catch {
    /* 忽略：保持旧状态 */
  }
}

function applyState(fresh: ABState): void {
  abState.version = fresh.version
  abState.assignments = fresh.assignments
  // 兼容旧版 localStorage 缓存（没有 buckets 字段）
  abState.buckets = fresh.buckets ?? {}
  try {
    localStorage.setItem(STATE_KEY, JSON.stringify(fresh))
  } catch {
    /* 存储满等异常不影响主流程 */
  }
}

async function fetchState(): Promise<ABState> {
  const userId = getUserId()
  const res = await fetch(
    `/api/ab/state?userId=${encodeURIComponent(userId)}&deviceId=${encodeURIComponent(userId)}`,
  )
  if (!res.ok) {
    throw new Error('state http ' + res.status)
  }
  return (await res.json()) as ABState
}

function timeoutReject(ms: number): Promise<never> {
  return new Promise((_, reject) => {
    setTimeout(() => reject(new Error('ab init timeout')), ms)
  })
}
