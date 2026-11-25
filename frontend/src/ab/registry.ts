/**
 * 变体组件注册表：动态渲染的安全红线。
 *
 * 教学说明：
 *  - ❌ 绝不能 defineAsyncComponent(() => import(后端下发的任意路径))——等于开放注入；
 *  - ✅ 后端只下发"组件名"，真实路径写死在构建期的注册表里；
 *  - keyof typeof REGISTRY 提供编译期联合类型约束，未知组件名一律降级不崩页。
 */
import { defineAsyncComponent, type Component } from 'vue'

const REGISTRY = {
  PayBtnOld: () => import('../components/variants/PayBtnOld.vue'),
  PayBtnNew: () => import('../components/variants/PayBtnNew.vue'),
  RecoCardOld: () => import('../components/variants/RecoCardOld.vue'),
  RecoCardNew: () => import('../components/variants/RecoCardNew.vue'),
} as const

export type VariantComponentName = keyof typeof REGISTRY

/** 把后端下发的组件名解析成异步组件；未知名字返回 null 走降级 */
export function resolveVariantComponent(name: unknown): Component | null {
  if (typeof name !== 'string' || !(name in REGISTRY)) {
    return null
  }
  return defineAsyncComponent({
    loader: REGISTRY[name as VariantComponentName],
    timeout: 3000, // 超时走 Suspense fallback，不卡首屏
  })
}
