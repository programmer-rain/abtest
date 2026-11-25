# ABTest 系统教学演示项目

配套文章《别再拿 Math.random() 做分流了：从零搭一套可信的 ABTest 系统》的可运行 demo。
一条链路打通：**稳定分流 → 分层正交 → 配置热更新 → 动态渲染 → 曝光埋点 → 统计检验**。

> 设计目标：教学优先。后端采用 **Spring Boot 3 + JDK 17 + Maven**（Apache-2.0 许可），
> 核心分流/统计引擎是纯 JDK 代码、与框架零耦合（MurmurHash3 为公有领域算法的独立实现）；
> 前端只用 Vue3 + Vite + TypeScript。所有依赖均为开源协议，可自由用于教学与演示。

---

## 一、快速开始

环境要求：**JDK 17+**、**Maven 3.6+**、**Node 18+**（仅构建前端时需要）。

```bash
# 1. 构建前端（产物输出到 frontend/dist，由后端单端口托管）
cd frontend
npm install
npm run build

# 2. 启动（Windows 双击 start.bat；macOS/Linux 执行 ./start.sh）
#    首次运行会自动用 Maven 构建后端 jar（需联网下载依赖）
# 3. 打开浏览器访问
#    http://localhost:8080
```

手动构建/启动后端：

```bash
cd backend
mvn -DskipTests package
java -jar target/abtest-backend-1.0.0.jar   # 在 backend 目录下执行，静态目录默认取 ../frontend/dist，可用 -Ddist=xxx 覆盖
```

想用 Vite 热更新开发前端时：

```bash
cd frontend
npm run dev      # http://localhost:5173，/api 自动代理到 8080
```

---

## 二、推荐教学演示流程（约 15 分钟）

| 步骤 | 操作 | 讲解知识点 |
|---|---|---|
| 1 | 打开首页，看「② 分流明细」表格 | 万分位分桶；同层互斥（ui_layer 两个实验只命中一个）；跨层正交（两层桶号互相独立） |
| 2 | 点右上角「换一个用户」 | 不同用户哈希到不同桶、命中不同变体；但同一用户刷新页面分组永远不变（localStorage 粘性） |
| 3 | 在「③ 管理面板」点【开始模拟】 | 5000 个虚拟用户走完整"分流→曝光→点击"链路；曝光按可见性采样、点击按内置真实点击率掷骰子 |
| 4 | 在「④ 实验看板」点【刷新数据】 | **先看 SRM**（卡方检验分流是否健康）→ 再看样本量 → 最后看 Z 检验：`pay_btn` 提升大→显著，`banner_style` 提升小→不显著 |
| 5 | 把 `pay_btn` 放量调到 20% 再应用 | 配置热更新：版本号 +1、快照原子替换；**老用户桶位不动，不跳组**；部分用户变为"未命中→降级对照组" |
| 6 | 点【一键暂停】 | Kill Switch：状态机 RUNNING→PAUSED，全量回落对照组，无需回滚代码 |
| 7 | 白名单里填当前用户 ID，设为 treatment | 白名单优先级最高，无视分桶强制进组（QA 验收/定向灰度） |
| 8 | 打开浏览器控制台 Network，观察 `/api/ab/track` | 曝光批量上报 + 后端幂等去重（看板底部显示"去重丢弃 N 条"） |

---

## 三、代码地图：博客知识点 → 源码位置

### 后端（`backend/src/main/java/com/demo/abtest/`，Spring Boot 3 + JDK 17 + Maven）

| 文件 | 对应知识点 |
|---|---|
| `AbTestApplication.java` | 启动入口 + `@Bean` 装配：核心引擎保持纯 JDK，不依赖 Spring API，可独立单测 |
| `core/MurmurHash3.java` | 稳定哈希算法（公有领域算法的独立实现，雪崩效应是正交的数学基础） |
| `core/Bucketer.java` | 万分位分桶；负数取模的三个坑（`Math.floorMod` / `& 0x7fffffff` / `toUnsignedLong`） |
| `model/Experiment.java` | 桶位三件套：`rangeStart`（层内预留区间）+ `capacity`（容量）+ `traffic`（当前放量）；**扩量只改阈值不重新哈希** |
| `model/ExperimentStatus.java` | 实验状态机：白名单式状态流转表，防止非法操作 |
| `model/Snapshot.java` | 不可变配置快照（Copy-On-Write 的"写时复制"对象） |
| `core/AssignService.java` | 分流引擎：白名单 > 状态过滤 > 桶位区间 > 权重前缀和；层内互斥 `break` 语义 |
| `core/ExperimentHolder.java` | `AtomicReference` 原子替换快照；放量/Kill Switch/白名单三种热更新 |
| `core/DemoData.java` | 两层三实验的初始配置（同层互斥 + 跨层正交的最小可演示结构） |
| `data/TrackStore.java` | 埋点幂等去重（模拟 Kafka+Flink 的去重聚合）；UV/CTR 口径 |
| `data/Simulator.java` | 流量模拟器：内置"真实点击率"，让统计检验有显著/不显著的对比效果 |
| `stats/Stats.java` | 统计四件套：SRM 卡方、双比例 Z 检验、Welch's t、最小样本量公式（含特殊函数独立实现） |
| `web/AbController.java` | Spring MVC 路由：9 个 API + ETag/304 缓存协商（ETag 含进程标识，防重启后版本号撞车） |
| `web/GlobalExceptionHandler.java` | `@RestControllerAdvice` 统一异常处理（404/业务异常/请求体解析） |
| `web/dto/*.java` | JDK 17 `record` 请求/响应 DTO：不可变、无样板代码 |
| `config/WebConfig.java` | CORS 放行 + 前端静态资源托管（模拟网关/Nginx 职责） |

### 前端（`frontend/src/`，Vue3 + TS + Vite）

| 文件 | 对应知识点 |
|---|---|
| `ab/types.ts` | 实验契约类型化：`ExperimentKey` 联合类型，写错实验名编译期报错 |
| `ab/client.ts` | 首屏防闪烁（FOUC）三件套：localStorage 粘性 + 后台校正（SWR）+ 超时兜底 |
| `ab/plugin.ts` | `provide/inject` 注入状态，业务侧无法手动篡改分组 |
| `ab/useExperiment.ts` | 核心 Hook：对照组兜底、`getParam` 默认值、IntersectionObserver 真实可见曝光、点击埋点自动带版本号 |
| `ab/registry.ts` | 组件注册表白名单 + `keyof typeof` 编译期约束：**防动态组件注入的安全红线** |
| `ab/tracker.ts` | 批量上报 + `sendBeacon` + `keepalive` 降级 + `pagehide` 兜底 + 会话级曝光去重 |
| `components/ExperimentSlot.vue` | 实验插槽：一行接入；未命中/未知组件/超时一律降级对照组；异步组件实例缓存 |
| `components/variants/*.vue` | 四个变体组件（构建期自动分包，验证按需加载）+ 骨架屏 |
| `components/AssignmentTable.vue` | 分流明细可视化：桶号进度条直观展示分桶 |
| `components/AdminPanel.vue` | 模拟运营后台：放量热更 / Kill Switch / 白名单 / 流量模拟；实时状态徽标 + "为什么没命中"提示（同层互斥/未放量/已暂停） |
| `components/AbDashboard.vue` | 实验看板：SRM → 样本量 → 显著性的正确查看顺序 |
| `App.vue` | 参数驱动实验示例（`banner_style` 纯靠下发文案/颜色渲染，不换组件） |

---

## 四、API 一览

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/ab/state?userId=&deviceId=` | 获取全部分组 + 各层桶号 `buckets`（支持 `If-None-Match` → 304） |
| POST | `/api/ab/track` | 批量埋点 `{events:[{expKey,variant,userId,event}]}`，幂等去重 |
| GET | `/api/ab/stats?expKey=` | 指标 + SRM 卡方 + 双比例 Z 检验 + 最小样本量 |
| GET | `/api/ab/admin/experiments` | 实验配置列表（状态/放量/桶区间），管理面板徽标与命中提示的数据源 |
| POST | `/api/ab/admin/traffic` | `{expKey, percent}` 热更放量（0~100） |
| POST | `/api/ab/admin/status` | `{expKey, running}` Kill Switch / 恢复 |
| POST | `/api/ab/admin/whitelist` | `{expKey, userId, variant}` 白名单强制分组 |
| POST | `/api/ab/admin/simulate` | `{users}` 模拟 N 个虚拟用户 |
| POST | `/api/ab/admin/reset` | 清空埋点数据 |

---

## 五、开源许可说明

| 组成 | 许可 | 说明 |
|---|---|---|
| 本项目全部源码 | MIT | 独立编写，见根目录 `LICENSE`，可自由用于教学与演示 |
| MurmurHash3 算法 | Public Domain | Austin Appleby 发布的公有领域算法，本项目为独立实现 |
| Spring Boot 3 | Apache-2.0 | 后端框架（内嵌 Tomcat + Spring MVC + Jackson） |
| Vue 3 | MIT | 前端框架 |
| Vite / @vitejs/plugin-vue | MIT | 构建工具 |
| TypeScript | Apache-2.0 | 开发依赖 |

**未使用任何闭源组件、字体、图片或受限素材。**

---

## 六、从 demo 到生产：扩展方向

1. **存储与管道**：`TrackStore` 换成 Kafka + Flink + ClickHouse，`DemoData` 换成 MySQL + Redis Pub/Sub；
2. **快照粘性**：补 `ab_assignment` 落库，首次决策持久化，改流量也不跳组；
3. **登录态归并**：`deviceId → userId` 粘性映射表（Redis，24h TTL）；
4. **SSR 直出**：网关注入 `window.__AB_STATE__`，替代 localStorage 方案，首屏 0 请求；
5. **统计增强**：Bonferroni 多指标校正、CUPED 方差缩减、mSPRT 序贯检验；
6. **健康巡检**：SRM 告警接入监控，自动熔断异常实验。
