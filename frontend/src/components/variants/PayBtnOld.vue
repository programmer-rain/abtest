<!-- 支付按钮 · 对照组（老版本）：普通灰色按钮 -->
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
    <button class="pay-btn pay-btn--old" @click="trackClick">
      提交订单
    </button>
    <p class="variant-tag">对照组 control · 老版按钮</p>
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
.pay-btn--old {
  padding: 12px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  background: #fafafa;
  color: #333;
  font-size: 15px;
  cursor: pointer;
}
.pay-btn--old:hover {
  border-color: #999;
}
.variant-tag {
  margin: 0;
  font-size: 12px;
  color: #999;
}
</style>
