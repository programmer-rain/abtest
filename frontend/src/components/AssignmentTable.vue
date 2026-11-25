<!--
  AssignmentTable：当前用户的分流明细。

  教学说明：
  - 展示每个实验命中的层、桶号、变体、配置版本，直观看到：
    · 同层互斥：ui_layer 里 pay_btn 与 banner_style 只会命中一个；
    · 跨层正交：ui_layer 与 algo_layer 的桶号互相独立；
  - 桶号进度条把 0~9999 的落点画出来，一眼看懂"分桶"是怎么回事。
-->
<script setup lang="ts">
import { computed, inject } from 'vue'
import { AB_KEY } from '../ab/plugin'

const state = inject(AB_KEY)!

const EXPERIMENTS = [
  { expKey: 'pay_btn', name: '支付按钮改版', layer: 'ui_layer' },
  { expKey: 'banner_style', name: '首页横幅文案', layer: 'ui_layer' },
  { expKey: 'reco_feed', name: '推荐卡片改版', layer: 'algo_layer' },
]

const rows = computed(() =>
  EXPERIMENTS.map((e) => {
    const a = state.assignments[e.expKey]
    return { ...e, assignment: a ?? null }
  }),
)
</script>

<template>
  <table class="assign-table">
    <thead>
      <tr>
        <th>实验</th>
        <th>流量层</th>
        <th>桶号（0~9999）</th>
        <th>变体</th>
        <th>配置版本</th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="row in rows" :key="row.expKey">
        <td>
          {{ row.name }}
          <code class="exp-code">{{ row.expKey }}</code>
        </td>
        <td>
          <span class="layer-badge" :class="row.layer">{{ row.layer }}</span>
        </td>
        <td>
          <template v-if="row.assignment">
            <div class="bucket-bar">
              <div
                class="bucket-dot"
                :style="{ left: (row.assignment.bucket / 9999) * 100 + '%' }"
              ></div>
            </div>
            <span class="bucket-num">#{{ row.assignment.bucket }}</span>
          </template>
          <span v-else class="miss">未命中</span>
        </td>
        <td>
          <span
            v-if="row.assignment"
            class="variant-badge"
            :class="row.assignment.variant"
          >
            {{ row.assignment.variant }}
            <i v-if="row.assignment.whitelisted">（白名单）</i>
          </span>
          <span v-else class="miss">—</span>
        </td>
        <td>
          <code v-if="row.assignment">v{{ row.assignment.configVersion }}</code>
          <span v-else class="miss">—</span>
        </td>
      </tr>
    </tbody>
  </table>
</template>

<style scoped>
.assign-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.assign-table th,
.assign-table td {
  text-align: left;
  padding: 10px 12px;
  border-bottom: 1px solid #f0f0f0;
}
.assign-table th {
  color: #888;
  font-weight: 500;
  background: #fafafa;
}
.exp-code {
  margin-left: 6px;
  font-size: 12px;
  color: #999;
}
.layer-badge {
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
}
.layer-badge.ui_layer {
  background: #e6f4ff;
  color: #1677ff;
}
.layer-badge.algo_layer {
  background: #f6ffed;
  color: #52c41a;
}
.bucket-bar {
  position: relative;
  height: 6px;
  width: 120px;
  border-radius: 3px;
  background: #f0f0f0;
  margin-bottom: 4px;
}
.bucket-dot {
  position: absolute;
  top: -3px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #1677ff;
  transform: translateX(-50%);
}
.bucket-num {
  font-size: 12px;
  color: #666;
}
.variant-badge {
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
}
.variant-badge.control {
  background: #f5f5f5;
  color: #666;
}
.variant-badge.treatment {
  background: #fff2e8;
  color: #fa541c;
}
.variant-badge i {
  font-style: normal;
  color: #faad14;
}
.miss {
  color: #bbb;
}
</style>
