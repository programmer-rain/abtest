/**
 * AB SDK 类型定义。
 *
 * 教学说明：
 *  - 用 TS 把"实验契约"显式声明出来，业务侧拿 variant / payload 都有类型提示，
 *    后端拼错字段名在编译期就能发现（配合从后端生成的类型更稳）。
 */

/** 单个实验的分组结果（对应后端 /api/ab/state 返回的 assignments[expKey]） */
export interface Assignment {
  /** 命中的变体：control / treatment */
  variant: string
  /** 所属流量层 */
  layer: string
  /** 用户在该层落到的桶号（0~9999），教学演示用 */
  bucket: number
  /** 是否白名单强制分组 */
  whitelisted: boolean
  /** 曝光归因要带的配置版本号 */
  configVersion: number
  /** 下发给前端的配置（组件名/文案/颜色等） */
  payload: Record<string, unknown>
}

/** 全部分组状态 */
export interface ABState {
  version: number
  assignments: Record<string, Assignment>
  /** 用户在每个层的桶号（layerKey → 0~9999），用于解释"为什么没命中某实验" */
  buckets: Record<string, number>
}

/** 埋点事件类型 */
export type TrackEvent = 'expose' | 'click'

/** 实验 key 联合类型：写错实验名编译期报错 */
export type ExperimentKey = 'pay_btn' | 'banner_style' | 'reco_feed'
