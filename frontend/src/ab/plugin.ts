/**
 * Vue 插件：把 AB 状态通过 provide/inject 注入组件树。
 *
 * 教学说明：
 *  - 业务组件不直接 import 全局变量，而是 inject 状态，
 *    方便测试时替换（也避免业务手动篡改分组污染埋点）。
 */
import type { App, InjectionKey } from 'vue'
import type { ABState } from './types'
import { abState } from './client'

export const AB_KEY: InjectionKey<ABState> = Symbol('ab-state')

export const abPlugin = {
  install(app: App): void {
    app.provide(AB_KEY, abState)
  },
}
