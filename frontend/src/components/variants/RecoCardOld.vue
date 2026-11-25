<!-- 推荐卡片 · 对照组（老版本）：纯文字列表 -->
<script setup lang="ts">
const items = [
  { title: '无线蓝牙耳机', price: '¥ 129' },
  { title: '便携榨汁杯', price: '¥ 59' },
  { title: '桌面加湿器', price: '¥ 89' },
]
</script>

<template>
  <div class="reco reco--old">
    <h4>为你推荐 <span class="variant-tag">对照组 · 老版卡片</span></h4>
    <ul>
      <li v-for="item in items" :key="item.title">
        <span>{{ item.title }}</span>
        <em>{{ item.price }}</em>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.reco--old {
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 12px 16px;
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
ul {
  margin: 0;
  padding: 0;
  list-style: none;
}
li {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 13px;
  color: #555;
  border-bottom: 1px dashed #f0f0f0;
}
li:last-child {
  border-bottom: none;
}
em {
  font-style: normal;
  color: #999;
}
</style>
