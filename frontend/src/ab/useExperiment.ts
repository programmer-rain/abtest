/**
 * useExperiment：前端 AB 的核心 Hook，业务侧一句话接入。
 *
 * 封装了四件事：
 *  1. 分组读取 + 对照组兜底（未命中/超时/下线一律降级，绝不白屏）；
 *  2. payload 类型安全读取（getParam 带默认值）；
 *  3. 真实可见曝光：IntersectionObserver 看到 50% 才算曝光，会话内去重；
 *  4. 点击埋点（自动带上 variant 和 configVersion，归因不错乱）。
 */
import {
  computed,
  inject,
  onBeforeUnmount,
  watch,
  type ComputedRef,
  type Ref,
} from 'vue'
import { AB_KEY } from './plugin'
import { getUserId } from './client'
import { reportClick, reportExposure } from './tracker'
import type { Assignment, ExperimentKey } from './types'

export interface UseExperimentResult {
  /** 命中的变体名（未命中返回兜底对照组） */
  variant: ComputedRef<string>
  /** 下发的配置 */
  payload: ComputedRef<Record<string, unknown>>
  /** 完整分组信息（含桶号、层、版本号），教学展示用 */
  assignment: ComputedRef<Assignment | null>
  /** 类型安全的参数读取 */
  getParam: <T>(key: string, defaultValue: T) => T
  /** 绑定曝光监听：传入组件根元素 ref */
  bindExposure: (el: Ref<HTMLElement | null>) => void
  /** 点击埋点 */
  trackClick: () => void
}

export function useExperiment(
  expKey: ExperimentKey,
  fallbackVariant = 'control',
): UseExperimentResult {
  const state = inject(AB_KEY)
  if (!state) {
    throw new Error('[AB] 未安装 abPlugin，请在 main.ts 中 app.use(abPlugin)')
  }

  const assignment = computed<Assignment | null>(
    () => state.assignments[expKey] ?? null,
  )
  const variant = computed(() => assignment.value?.variant ?? fallbackVariant)
  const payload = computed<Record<string, unknown>>(
    () => assignment.value?.payload ?? {},
  )

  function getParam<T>(key: string, defaultValue: T): T {
    const v = payload.value[key]
    return v === undefined || v === null ? defaultValue : (v as T)
  }

  function bindExposure(el: Ref<HTMLElement | null>): void {
    let io: IntersectionObserver | null = null

    const observe = (node: HTMLElement | null) => {
      if (io) {
        io.disconnect()
        io = null
      }
      if (!node) {
        return
      }
      io = new IntersectionObserver(
        (entries) => {
          for (const entry of entries) {
            // 可见面积 >= 50% 才算真实曝光，且 tracker 内部会话级去重
            if (entry.isIntersecting && entry.intersectionRatio >= 0.5) {
              reportExposure(
                expKey,
                variant.value,
                getUserId(),
                assignment.value?.configVersion ?? 0,
              )
              io?.disconnect()
              break
            }
          }
        },
        { threshold: 0.5 },
      )
      io.observe(node)
    }

    watch(el, observe, { immediate: true, flush: 'post' })
    onBeforeUnmount(() => io?.disconnect())
  }

  function trackClick(): void {
    reportClick(
      expKey,
      variant.value,
      getUserId(),
      assignment.value?.configVersion ?? 0,
    )
  }

  return { variant, payload, assignment, getParam, bindExposure, trackClick }
}
