<!--
  AdminPanel：模拟运营后台。

  覆盖博客里的四个"管理侧"能力：
  1. 放量调整 —— 演示"扩量只挪阈值，老用户不跳组"；
  2. Kill Switch —— 一键暂停，全量回落对照组；
  3. 白名单 —— 强制某用户命中指定变体（QA 验收/回滚）；
  4. 流量模拟 —— 一键灌入 N 个虚拟用户，让看板有数据可看。
-->
<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { abState } from '../ab/client'

const emit = defineEmits<{ (e: 'changed'): void }>()
const message = ref('')

const experiments = [
  { expKey: 'pay_btn', name: '支付按钮改版' },
  { expKey: 'banner_style', name: '首页横幅文案' },
  { expKey: 'reco_feed', name: '推荐卡片改版' },
]

/** 后端 /api/ab/admin/experiments 返回的实验配置 */
interface ExperimentConfig {
  expKey: string
  name: string
  layerKey: string
  status: string
  statusDesc: string
  running: boolean
  trafficPercent: number
  rangeStart: number
  capacity: number
  traffic: number
}

const configs = ref<Record<string, ExperimentConfig>>({})

async function loadConfigs(): Promise<void> {
  try {
    const res = await fetch('/api/ab/admin/experiments')
    const list = (await res.json()) as ExperimentConfig[]
    const map: Record<string, ExperimentConfig> = {}
    for (const c of list) {
      map[c.expKey] = c
    }
    configs.value = map
  } catch {
    /* 拉取失败保持旧配置，不影响面板其余操作 */
  }
}
onMounted(loadConfigs)

const form = reactive({
  percent: 100,
  whitelistUser: '',
  whitelistVariant: 'treatment',
  simUsers: 5000,
})

async function post(url: string, body: unknown): Promise<void> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  const data = await res.json()
  message.value = data.message ?? JSON.stringify(data)
  await loadConfigs() // 配置可能变了，刷新状态徽标
  emit('changed')
}

function cfg(expKey: string): ExperimentConfig | undefined {
  return configs.value[expKey]
}

function badgeCls(c: ExperimentConfig): string {
  if (c.status === 'RUNNING') return 'badge-run'
  if (c.status === 'PAUSED') return 'badge-pause'
  return 'badge-other'
}

/**
 * 命中提示：结合各层桶号（state 下发）+ 桶区间（experiments 下发），
 * 把"为什么没命中"讲清楚——同层互斥 / 未放量 / 已暂停，一目了然。
 */
function hitHint(expKey: string): { cls: string; text: string } {
  const c = configs.value[expKey]
  if (!c) return { cls: 'hint-none', text: '实验配置加载中…' }

  const a = abState.assignments[expKey]
  if (a) {
    return {
      cls: 'hint-hit',
      text: `已命中：${a.variant}${a.whitelisted ? '（白名单强制）' : ''}，桶号 ${a.bucket}`,
    }
  }

  const bucket = abState.buckets[c.layerKey]
  if (bucket === undefined) {
    return { cls: 'hint-none', text: '无桶号：缺少 userId / deviceId' }
  }

  const inReserved = bucket >= c.rangeStart && bucket < c.rangeStart + c.capacity
  if (!inReserved) {
    const owner = Object.values(configs.value).find(
      (o) => o.layerKey === c.layerKey && bucket >= o.rangeStart && bucket < o.rangeStart + o.capacity,
    )
    const ownerText = owner
      ? `，落在同层实验「${owner.name}」的预留区间 [${owner.rangeStart}, ${owner.rangeStart + owner.capacity})`
      : ''
    return {
      cls: 'hint-miss',
      text: `未命中：你在 ${c.layerKey} 的桶号是 ${bucket}${ownerText}，同层互斥被排除`,
    }
  }
  if (!c.running) {
    return {
      cls: 'hint-paused',
      text: `已暂停：桶号 ${bucket} 在预留区间内，但实验处于暂停状态，全量回落对照组`,
    }
  }
  if (bucket >= c.rangeStart + c.traffic) {
    return {
      cls: 'hint-ramp',
      text: `未放量：桶号 ${bucket} 在预留区间内，但当前只放量 ${c.trafficPercent}%，暂回落对照组（扩量不跳组）`,
    }
  }
  return { cls: 'hint-none', text: '未命中（异常情况，请检查后端）' }
}

function updateTraffic(expKey: string) {
  post('/api/ab/admin/traffic', { expKey, percent: form.percent })
}
function setStatus(expKey: string, running: boolean) {
  post('/api/ab/admin/status', { expKey, running })
}
function setWhitelist(expKey: string) {
  if (!form.whitelistUser.trim()) {
    message.value = '请先填写要加白名单的用户 ID'
    return
  }
  post('/api/ab/admin/whitelist', {
    expKey,
    userId: form.whitelistUser.trim(),
    variant: form.whitelistVariant,
  })
}
function simulate() {
  post('/api/ab/admin/simulate', { users: form.simUsers })
}
function resetData() {
  post('/api/ab/admin/reset', {})
}
</script>

<template>
  <div class="admin">
    <div class="admin-grid">
      <section v-for="exp in experiments" :key="exp.expKey" class="admin-card">
        <h4>
          {{ exp.name }} <code>{{ exp.expKey }}</code>
          <template v-if="cfg(exp.expKey)">
            <span class="badge" :class="badgeCls(cfg(exp.expKey)!)">
              {{ cfg(exp.expKey)!.statusDesc }}
            </span>
            <span class="badge badge-traffic">
              放量 {{ cfg(exp.expKey)!.trafficPercent }}%
            </span>
          </template>
        </h4>

        <p class="hint" :class="hitHint(exp.expKey).cls">{{ hitHint(exp.expKey).text }}</p>

        <label class="row">
          <span>放量比例</span>
          <input v-model.number="form.percent" type="range" min="0" max="100" />
          <b>{{ form.percent }}%</b>
          <button @click="updateTraffic(exp.expKey)">应用</button>
        </label>

        <div class="row">
          <span>状态控制</span>
          <button class="danger" @click="setStatus(exp.expKey, false)">
            一键暂停
          </button>
          <button @click="setStatus(exp.expKey, true)">恢复运行</button>
        </div>

        <div class="row">
          <span>白名单</span>
          <input
            v-model="form.whitelistUser"
            type="text"
            placeholder="用户ID"
            class="text-input"
          />
          <select v-model="form.whitelistVariant">
            <option value="control">control</option>
            <option value="treatment">treatment</option>
          </select>
          <button @click="setWhitelist(exp.expKey)">设置</button>
        </div>
      </section>

      <section class="admin-card tools">
        <h4>流量模拟</h4>
        <div class="row">
          <span>虚拟用户数</span>
          <input v-model.number="form.simUsers" type="number" min="100" step="1000" class="text-input" />
          <button class="primary" @click="simulate">开始模拟</button>
        </div>
        <p class="tip">
          模拟器内置了"真实点击率"：实验组略高于对照组，灌完流量就能在下方看板看到
          SRM 检查与 Z 检验结果。
        </p>
        <div class="row">
          <button @click="resetData">清空埋点数据</button>
        </div>
      </section>
    </div>

    <p v-if="message" class="message">{{ message }}</p>
  </div>
</template>

<style scoped>
.admin-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
}
.admin-card {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 14px 16px;
}
.admin-card h4 {
  margin: 0 0 12px;
  font-size: 14px;
}
.admin-card h4 code {
  margin-left: 6px;
  font-size: 12px;
  color: #999;
}
.badge {
  font-size: 12px;
  font-weight: normal;
  padding: 1px 8px;
  border-radius: 10px;
  margin-left: 6px;
  white-space: nowrap;
}
.badge-run {
  background: #f6ffed;
  color: #52c41a;
  border: 1px solid #b7eb8f;
}
.badge-pause {
  background: #fff1f0;
  color: #ff4d4f;
  border: 1px solid #ffa39e;
}
.badge-other {
  background: #f5f5f5;
  color: #999;
  border: 1px solid #e0e0e0;
}
.badge-traffic {
  background: #e6f4ff;
  color: #1677ff;
  border: 1px solid #91caff;
}
.hint {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.6;
  padding: 6px 10px;
  border-radius: 6px;
}
.hint-hit {
  color: #389e0d;
  background: #f6ffed;
}
.hint-miss {
  color: #8c8c8c;
  background: #fafafa;
}
.hint-paused {
  color: #cf1322;
  background: #fff1f0;
}
.hint-ramp {
  color: #d46b08;
  background: #fff7e6;
}
.hint-none {
  color: #8c8c8c;
  background: #fafafa;
}
.row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 13px;
  flex-wrap: wrap;
}
.row > span {
  color: #888;
  width: 64px;
  flex-shrink: 0;
}
button {
  padding: 4px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  font-size: 13px;
}
button:hover {
  border-color: #1677ff;
  color: #1677ff;
}
button.primary {
  background: #1677ff;
  border-color: #1677ff;
  color: #fff;
}
button.danger {
  border-color: #ff4d4f;
  color: #ff4d4f;
}
.text-input {
  padding: 4px 8px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  width: 110px;
}
select {
  padding: 4px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
}
.tip {
  font-size: 12px;
  color: #999;
  margin: 4px 0 10px;
}
.message {
  margin: 14px 0 0;
  font-size: 13px;
  color: #52c41a;
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  padding: 8px 12px;
  border-radius: 4px;
}
</style>
