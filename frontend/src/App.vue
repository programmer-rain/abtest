<!--
  演示主页面：一个"电商下单页"的微缩场景。

  页面结构对应博客四大块：
  1. 实验渲染区 —— 动态组件（pay_btn / reco_feed）+ 参数驱动（banner_style）；
  2. 分流明细 —— 桶号/层/版本可视化，直观理解分层正交；
  3. 管理面板 —— 放量热更、Kill Switch、白名单、流量模拟；
  4. 实验看板 —— SRM、Z 检验、最小样本量。
-->
<script setup lang="ts">
import { ref } from 'vue'
import ExperimentSlot from './components/ExperimentSlot.vue'
import AssignmentTable from './components/AssignmentTable.vue'
import AdminPanel from './components/AdminPanel.vue'
import AbDashboard from './components/AbDashboard.vue'
import { useExperiment } from './ab/useExperiment'
import { abState, getUserId, refreshAb, resetUserId } from './ab/client'

// banner_style 是"参数驱动"实验：不换组件，纯靠下发的文案/颜色渲染
const {
  variant: bannerVariant,
  getParam: bannerParam,
  bindExposure: bindBanner,
  trackClick: trackBannerClick,
} = useExperiment('banner_style')

const bannerRef = ref<HTMLElement | null>(null)
bindBanner(bannerRef)

const dashboard = ref<InstanceType<typeof AbDashboard> | null>(null)
const userId = ref(getUserId())

async function onAdminChanged() {
  await refreshAb() // 配置版本变了，重新拉分组
  dashboard.value?.refresh()
}

function switchUser() {
  userId.value = resetUserId()
  refreshAb()
}
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1>ABTest 教学演示</h1>
        <p class="subtitle">
          稳定分流 · 分层正交 · 动态渲染 · 统计检验 —— 一条链路全部打通
        </p>
      </div>
      <div class="user-box">
        <span class="user-id">当前用户：<code>{{ userId }}</code></span>
        <span class="version">配置版本 v{{ abState.version }}</span>
        <button @click="switchUser">换一个用户</button>
      </div>
    </header>

    <!-- 1. 实验渲染区 -->
    <section class="card">
      <h2>① 实验渲染区（前端动态交付，不发版）</h2>
      <div class="demo-grid">
        <div class="demo-col">
          <h3>支付按钮 <code>pay_btn</code> <em>动态组件类</em></h3>
          <ExperimentSlot exp-key="pay_btn" fallback-component="PayBtnOld" />
        </div>

        <div class="demo-col">
          <h3>首页横幅 <code>banner_style</code> <em>参数驱动类</em></h3>
          <div
            ref="bannerRef"
            class="banner"
            :style="{ background: bannerParam('themeColor', '#8c8c8c') }"
          >
            <div class="banner-text">
              <strong>{{ bannerParam('title', '欢迎光临') }}</strong>
              <span>variant = {{ bannerVariant }}</span>
            </div>
            <button class="banner-btn" @click="trackBannerClick">
              {{ bannerParam('btnText', '查看') }}
            </button>
          </div>

          <h3 class="mt">推荐卡片 <code>reco_feed</code> <em>跨层正交</em></h3>
          <ExperimentSlot exp-key="reco_feed" fallback-component="RecoCardOld" />
        </div>
      </div>
    </section>

    <!-- 2. 分流明细 -->
    <section class="card">
      <h2>② 分流明细（同层互斥 · 跨层正交）</h2>
      <p class="card-tip">
        同一个用户在 ui_layer 的两个实验里只会命中一个（桶区间互斥），
        而 algo_layer 用不同的盐独立分桶（跨层正交）。点右上角"换一个用户"看看不同人的落点。
      </p>
      <AssignmentTable />
    </section>

    <!-- 3. 管理面板 -->
    <section class="card">
      <h2>③ 运营管理面板（配置热更新 · Kill Switch · 白名单）</h2>
      <AdminPanel @changed="onAdminChanged" />
    </section>

    <!-- 4. 实验看板 -->
    <section class="card">
      <h2>④ 实验看板（先查 SRM，再看显著性）</h2>
      <AbDashboard ref="dashboard" />
    </section>

    <footer class="page-footer">
      教学演示项目 · 后端纯 JDK 零依赖 · 前端 Vue3 + TypeScript + Vite · 全部开源协议
    </footer>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
}
h1 {
  margin: 0;
  font-size: 24px;
}
.subtitle {
  margin: 6px 0 0;
  color: #888;
  font-size: 13px;
}
.user-box {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  flex-wrap: wrap;
}
.user-id code {
  background: #f5f5f5;
  padding: 2px 8px;
  border-radius: 4px;
}
.version {
  color: #1677ff;
  background: #e6f4ff;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
}
.user-box button {
  padding: 4px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
}
.card {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  padding: 20px 24px;
  margin-bottom: 20px;
}
.card h2 {
  margin: 0 0 14px;
  font-size: 17px;
}
.card-tip {
  font-size: 13px;
  color: #999;
  margin: 0 0 12px;
}
.demo-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}
@media (max-width: 768px) {
  .demo-grid {
    grid-template-columns: 1fr;
  }
}
.demo-col h3 {
  font-size: 14px;
  margin: 0 0 10px;
}
.demo-col h3 code {
  margin-left: 6px;
  font-size: 12px;
  color: #999;
}
.demo-col h3 em {
  font-style: normal;
  font-size: 12px;
  color: #1677ff;
  background: #e6f4ff;
  border-radius: 10px;
  padding: 1px 8px;
  margin-left: 6px;
}
.mt {
  margin-top: 20px;
}
.banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-radius: 8px;
  padding: 16px 20px;
  color: #fff;
  transition: background 0.3s ease;
}
.banner-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.banner-text span {
  font-size: 12px;
  opacity: 0.8;
}
.banner-btn {
  border: none;
  border-radius: 16px;
  padding: 8px 18px;
  background: rgba(255, 255, 255, 0.95);
  color: #333;
  font-weight: 600;
  cursor: pointer;
}
.page-footer {
  text-align: center;
  color: #bbb;
  font-size: 12px;
  padding: 10px 0 30px;
}
</style>
