<!-- 支付按钮 · 实验组（新版本）：高亮渐变色 + 利益点文案 -->
<script setup lang="ts">
import { useExperiment } from '../../ab/useExperiment'

const { trackClick } = useExperiment('pay_btn')
</script>

<template>
  <div class="pay-area">
    <div class="order-summary">
      <span>订单金额</span>
      <strong>¥ 299.00</strong>
    </div>
    <button class="pay-btn pay-btn--new" @click="trackClick">
      立即支付 · 随机立减
    </button>
    <p class="variant-tag">实验组 treatment · 新版按钮</p>
  </div>
</template>

<style scoped>
.pay-area {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.order-summary {
  display: flex;
  justify-content: space-between;
  color: #666;
  font-size: 14px;
}
.pay-btn--new {
  padding: 12px;
  border: none;
  border-radius: 24px;
  background: linear-gradient(90deg, #ff6034, #ee0a24);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(238, 10, 36, 0.3);
  transition: transform 0.15s ease;
}
.pay-btn--new:hover {
  transform: translateY(-1px);
}
.variant-tag {
  margin: 0;
  font-size: 12px;
  color: #999;
}
</style>
