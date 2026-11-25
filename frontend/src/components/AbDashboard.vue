<!--
  AbDashboard：实验看板（教学简化版，生产用 ECharts/Superset 等）。

  展示顺序就是看实验数据的正确姿势：
  1. 先看 SRM —— 分流有没有坏（卡方检验，p < 0.001 报警）；
  2. 再看样本量 —— 够不够最小样本量，不够别下结论；
  3. 最后看显著性 —— 双比例 Z 检验的 p 值与提升幅度。
-->
<script setup lang="ts">
import { onMounted, ref } from 'vue'

interface VariantStat {
  variant: string
  exposureUv: number
  clickUv: number
  ctr: number
}
interface ExpStats {
  expKey: string
  name: string
  status: string
  trafficPercent: number
  variants: VariantStat[]
  totalEvents: number
  duplicatedEvents: number
  srm?: { chi2: number; pValue: number; alarm: boolean }
  zTest?: { zScore: number; pValue: number; liftPercent: number; significant: boolean }
  requiredSampleSizePerGroup?: number
}

const EXPERIMENTS = ['pay_btn', 'banner_style', 'reco_feed']
const stats = ref<Record<string, ExpStats>>({})
const loading = ref(false)

async function refresh() {
  loading.value = true
  try {
    const results = await Promise.all(
      EXPERIMENTS.map(async (expKey) => {
        const res = await fetch(`/api/ab/stats?expKey=${expKey}`)
        return (await res.json()) as ExpStats
      }),
    )
    const next: Record<string, ExpStats> = {}
    for (const item of results) {
      next[item.expKey] = item
    }
    stats.value = next
  } finally {
    loading.value = false
  }
}

function pct(v: number): string {
  return (v * 100).toFixed(2) + '%'
}
function pText(v: number): string {
  return v < 0.0001 ? '< 0.0001' : v.toFixed(4)
}

onMounted(refresh)
defineExpose({ refresh })
</script>

<template>
  <div class="dashboard">
    <div class="toolbar">
      <button class="primary" :disabled="loading" @click="refresh">
        {{ loading ? '加载中…' : '刷新数据' }}
      </button>
      <span class="hint">
        操作提示：先在上方"管理面板"点【开始模拟】灌入流量，再刷新看板
      </span>
    </div>

    <div v-if="Object.keys(stats).length === 0 && !loading" class="empty">
      暂无数据
    </div>

    <div class="cards">
      <section v-for="expKey in EXPERIMENTS" :key="expKey" class="stat-card">
        <template v-if="stats[expKey]">
          <header>
            <h4>{{ stats[expKey].name }}</h4>
            <span class="status">{{ stats[expKey].status }} · 放量 {{ stats[expKey].trafficPercent }}%</span>
          </header>

          <!-- 1. SRM 健康检查 -->
          <div v-if="stats[expKey].srm" class="check" :class="stats[expKey].srm!.alarm ? 'bad' : 'good'">
            <strong>{{ stats[expKey].srm!.alarm ? 'SRM 报警：分流有偏，数据作废！' : 'SRM 检查通过：分流健康' }}</strong>
            <span>卡方={{ stats[expKey].srm!.chi2.toFixed(2) }}，p={{ pText(stats[expKey].srm!.pValue) }}</span>
          </div>

          <!-- 2. 分组指标 -->
          <table>
            <thead>
              <tr><th>变体</th><th>曝光UV</th><th>点击UV</th><th>CTR</th></tr>
            </thead>
            <tbody>
              <tr v-for="v in stats[expKey].variants" :key="v.variant" :class="v.variant">
                <td>{{ v.variant }}</td>
                <td>{{ v.exposureUv }}</td>
                <td>{{ v.clickUv }}</td>
                <td>{{ pct(v.ctr) }}</td>
              </tr>
            </tbody>
          </table>

          <!-- 3. 显著性检验 -->
          <div v-if="stats[expKey].zTest" class="ztest">
            <span class="lift" :class="{ sig: stats[expKey].zTest!.significant }">
              相对提升 {{ stats[expKey].zTest!.liftPercent.toFixed(1) }}%
            </span>
            <span>Z={{ stats[expKey].zTest!.zScore.toFixed(2) }}，p={{ pText(stats[expKey].zTest!.pValue) }}</span>
            <span class="badge" :class="stats[expKey].zTest!.significant ? 'sig' : 'not-sig'">
              {{ stats[expKey].zTest!.significant ? '显著（p < 0.05）' : '不显著' }}
            </span>
          </div>

          <p v-if="stats[expKey].requiredSampleSizePerGroup" class="sample">
            最小样本量（每组）：{{ stats[expKey].requiredSampleSizePerGroup!.toLocaleString() }}
          </p>
        </template>
      </section>
    </div>

    <p v-if="Object.values(stats)[0]" class="dedup-note">
      埋点管道：共接收 {{ Object.values(stats)[0].totalEvents }} 条事件，
      幂等去重丢弃 {{ Object.values(stats)[0].duplicatedEvents }} 条
      （同一用户同一实验同一事件只计一次 → 曝光不虚高）
    </p>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
button.primary {
  padding: 6px 16px;
  background: #1677ff;
  border: 1px solid #1677ff;
  border-radius: 4px;
  color: #fff;
  cursor: pointer;
}
button.primary:disabled {
  opacity: 0.6;
}
.hint {
  font-size: 12px;
  color: #999;
}
.empty {
  color: #bbb;
  text-align: center;
  padding: 30px 0;
}
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
}
.stat-card {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 14px 16px;
}
.stat-card header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 10px;
}
.stat-card h4 {
  margin: 0;
  font-size: 14px;
}
.status {
  font-size: 12px;
  color: #999;
}
.check {
  border-radius: 4px;
  padding: 8px 10px;
  font-size: 12px;
  margin-bottom: 10px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.check.good {
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  color: #389e0d;
}
.check.bad {
  background: #fff1f0;
  border: 1px solid #ffa39e;
  color: #cf1322;
}
table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  margin-bottom: 10px;
}
th,
td {
  text-align: left;
  padding: 6px 8px;
  border-bottom: 1px solid #f5f5f5;
}
th {
  color: #999;
  font-weight: 400;
}
tr.treatment td {
  color: #fa541c;
}
.ztest {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  flex-wrap: wrap;
}
.lift {
  font-weight: 600;
}
.lift.sig {
  color: #fa541c;
}
.badge {
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
}
.badge.sig {
  background: #fff2e8;
  color: #fa541c;
}
.badge.not-sig {
  background: #f5f5f5;
  color: #999;
}
.sample {
  margin: 8px 0 0;
  font-size: 12px;
  color: #999;
}
.dedup-note {
  margin-top: 14px;
  font-size: 12px;
  color: #999;
}
</style>
