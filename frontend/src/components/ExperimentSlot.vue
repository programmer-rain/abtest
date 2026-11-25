<!--
  ExperimentSlot：实验插槽组件，业务侧一行接入。

  教学说明：
  - 根据后端下发的 payload.componentKey 从【注册表白名单】里取组件；
  - 未命中实验 / 组件名未知 / 加载超时 → 一律渲染 fallback（对照组），绝不白屏；
  - 曝光用 IntersectionObserver：真实可见 50% 才上报，会话内去重。
-->
<script setup lang="ts">
import { computed, ref, type Component } from 'vue'
import { useExperiment } from '../ab/useExperiment'
import {
  resolveVariantComponent,
  type VariantComponentName,
} from '../ab/registry'
import Skeleton from './variants/Skeleton.vue'
import type { ExperimentKey } from '../ab/types'

const props = defineProps<{
  expKey: ExperimentKey
  /** 降级组件（对照组）：未命中/异常时渲染它 */
  fallbackComponent: VariantComponentName
}>()

const { assignment, variant, bindExposure } = useExperiment(props.expKey)

const root = ref<HTMLElement | null>(null)
bindExposure(root)

// 缓存异步组件实例：避免 computed 重算时反复创建实例导致重复请求
const cache = new Map<string, Component>()
const Comp = computed<Component>(() => {
  const name = assignment.value?.payload.componentKey ?? props.fallbackComponent
  const key = String(name)
  if (!cache.has(key)) {
    const resolved = resolveVariantComponent(name)
    if (resolved) {
      cache.set(key, resolved)
    }
  }
  return cache.get(key) ?? cache.get(props.fallbackComponent)!
})
</script>

<template>
  <div ref="root" class="experiment-slot">
    <Suspense>
      <component :is="Comp" />
      <template #fallback>
        <Skeleton />
      </template>
    </Suspense>
    <p v-if="!assignment" class="slot-note">
      未命中实验（不在放量区间或实验已暂停）→ 自动降级渲染对照组 · 当前 variant={{ variant }}
    </p>
  </div>
</template>

<style scoped>
.experiment-slot {
  min-height: 60px;
}
.slot-note {
  margin: 8px 0 0;
  font-size: 12px;
  color: #faad14;
  background: #fffbe6;
  border: 1px solid #ffe58f;
  border-radius: 4px;
  padding: 6px 10px;
}
</style>
