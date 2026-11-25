<!-- 骨架屏：异步组件加载期间的占位，避免布局抖动（CLS=0） -->
<template>
  <div class="skeleton" aria-hidden="true">
    <div class="skeleton-line" style="width: 60%"></div>
    <div class="skeleton-line" style="width: 90%"></div>
    <div class="skeleton-line" style="width: 40%"></div>
  </div>
</template>

<style scoped>
.skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 0;
}
.skeleton-line {
  height: 14px;
  border-radius: 4px;
  background: linear-gradient(90deg, #f2f2f2 25%, #e6e6e6 37%, #f2f2f2 63%);
  background-size: 400% 100%;
  animation: shimmer 1.4s ease infinite;
}
@keyframes shimmer {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}
</style>
