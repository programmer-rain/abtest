<!-- 推荐卡片 · 实验组（新版本）：图文卡片 + 标签 -->
<script setup lang="ts">
const items = [
  { title: '无线蓝牙耳机', price: '¥ 129', tag: '爆款', emoji: '🎧' },
  { title: '便携榨汁杯', price: '¥ 59', tag: '新品', emoji: '🥤' },
  { title: '桌面加湿器', price: '¥ 89', tag: '热卖', emoji: '💧' },
]
</script>

<template>
  <div class="reco reco--new">
    <h4>为你推荐 <span class="variant-tag">实验组 · 新版卡片</span></h4>
    <div class="grid">
      <div v-for="item in items" :key="item.title" class="card">
        <div class="thumb">{{ item.emoji }}</div>
        <div class="info">
          <p class="title">{{ item.title }}</p>
          <p class="meta">
            <span class="tag">{{ item.tag }}</span>
            <span class="price">{{ item.price }}</span>
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.reco--new {
  border: 1px solid #ffe0d6;
  border-radius: 8px;
  padding: 12px 16px;
  background: #fffaf8;
}
h4 {
  margin: 0 0 8px;
  font-size: 14px;
  color: #333;
}
.variant-tag {
  font-weight: 400;
  font-size: 12px;
  color: #999;
}
.grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.card {
  display: flex;
  gap: 10px;
  align-items: center;
  background: #fff;
  border-radius: 6px;
  padding: 8px;
}
.thumb {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  background: #fff1ec;
  border-radius: 6px;
}
.info {
  flex: 1;
}
.title {
  margin: 0;
  font-size: 13px;
  color: #333;
}
.meta {
  margin: 2px 0 0;
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 12px;
}
.tag {
  color: #ff4d4f;
  border: 1px solid #ffccc7;
  border-radius: 3px;
  padding: 0 4px;
}
.price {
  color: #ff4d4f;
  font-weight: 600;
}
</style>
