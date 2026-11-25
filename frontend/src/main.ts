/**
 * 应用入口。
 *
 * 关键点：先 await initAb() 再挂载 → 首帧渲染时分组已就绪，杜绝"先 A 后 B"的闪烁。
 * initAb 内部带超时兜底，网络慢也不会卡住页面。
 */
import { createApp } from 'vue'
import App from './App.vue'
import { abPlugin } from './ab/plugin'
import { initAb } from './ab/client'
import './style.css'

initAb().then(() => {
  createApp(App).use(abPlugin).mount('#app')
})
