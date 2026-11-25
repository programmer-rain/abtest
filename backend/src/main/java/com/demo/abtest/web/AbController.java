package com.demo.abtest.web;

import com.demo.abtest.core.AssignService;
import com.demo.abtest.core.ExperimentHolder;
import com.demo.abtest.data.Simulator;
import com.demo.abtest.data.TrackStore;
import com.demo.abtest.model.Assignment;
import com.demo.abtest.model.Experiment;
import com.demo.abtest.model.Layer;
import com.demo.abtest.model.Snapshot;
import com.demo.abtest.model.Variant;
import com.demo.abtest.stats.Stats;
import com.demo.abtest.web.dto.AdminRequests.Simulate;
import com.demo.abtest.web.dto.AdminRequests.Status;
import com.demo.abtest.web.dto.AdminRequests.Traffic;
import com.demo.abtest.web.dto.AdminRequests.Whitelist;
import com.demo.abtest.web.dto.ExperimentView;
import com.demo.abtest.web.dto.OkResponse;
import com.demo.abtest.web.dto.StateResponse;
import com.demo.abtest.web.dto.StateResponse.AssignmentView;
import com.demo.abtest.web.dto.TrackRequest;
import com.demo.abtest.web.dto.TrackRequest.TrackEvent;
import com.demo.abtest.web.dto.TrackResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API 入口：分流查询 / 埋点上报 / 实验看板 / 管理面板。
 *
 * 教学说明：
 *  - 相比旧版手写 HttpServer 路由，Spring MVC 用注解声明路由与参数绑定，
 *    JSON 序列化（Jackson）、参数解码、异常处理全部交给框架；
 *  - 接口契约（路径/字段名/返回结构）与旧版逐字节保持一致，前端零改动。
 *
 * API 一览：
 *   GET  /api/ab/state?userId=&deviceId=     获取分组（带 ETag/304 缓存协商）
 *   POST /api/ab/track                        上报埋点（幂等去重）
 *   GET  /api/ab/stats?expKey=                实验指标 + SRM + Z 检验
 *   POST /api/ab/admin/traffic                热更放量比例（演示"扩量不跳组"）
 *   POST /api/ab/admin/status                 Kill Switch 一键暂停/恢复
 *   POST /api/ab/admin/whitelist              白名单强制分组
 *   POST /api/ab/admin/simulate               模拟 N 个虚拟用户灌流量
 *   POST /api/ab/admin/reset                  清空埋点数据
 */
@RestController
public class AbController {

    private final ExperimentHolder holder;
    private final AssignService assignService;
    private final TrackStore trackStore;
    private final Simulator simulator;

    /**
     * 进程标识：拼进 ETag。
     * 版本号每次重启都从 1 重新计数，若只用版本号做 ETag，
     * 重启后浏览器旧缓存的 W/"1" 会和新服务的 W/"1" 撞车，
     * 被误判"配置没变"返回 304，喂回旧格式的缓存数据。
     */
    private final String bootId = Long.toHexString(System.nanoTime());

    public AbController(ExperimentHolder holder, AssignService assignService,
                        TrackStore trackStore, Simulator simulator) {
        this.holder = holder;
        this.assignService = assignService;
        this.trackStore = trackStore;
        this.simulator = simulator;
    }

    // ==================== 1. 分流查询 ====================

    /**
     * 前端启动时调用一次，拿到全部实验的命中结果。
     * 带 ETag/304：配置版本没变时只回一个状态码，省带宽。
     */
    @GetMapping("/api/ab/state")
    public ResponseEntity<StateResponse> state(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "deviceId", required = false) String deviceId,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {

        Snapshot snapshot = holder.get();
        String etag = "W/\"" + bootId + "-" + snapshot.getVersion() + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }

        Map<String, AssignmentView> assignments = new LinkedHashMap<>();
        for (Assignment a : assignService.assign(snapshot, userId, deviceId)) {
            assignments.put(a.getExpKey(), new AssignmentView(
                    a.getVariantKey(), a.getLayerKey(), a.getBucket(),
                    a.isWhitelisted(), a.getConfigVersion(), a.getPayload()));
        }
        // 各层桶号一并下发：前端据此解释"为什么没命中某实验"（同层互斥/未放量/已暂停）
        Map<String, Integer> buckets = assignService.layerBuckets(snapshot, userId, deviceId);
        return ResponseEntity.ok().eTag(etag)
                .body(new StateResponse(snapshot.getVersion(), assignments, buckets));
    }

    // ==================== 2. 埋点上报 ====================

    /**
     * 批量埋点。返回接收条数与被去重丢弃的条数——
     * 幂等是曝光数据不虚高的第一道防线。
     */
    @PostMapping("/api/ab/track")
    public ResponseEntity<?> track(@RequestBody(required = false) TrackRequest request) {
        if (request == null || request.events() == null) {
            return ResponseEntity.badRequest().body(OkResponse.error("缺少 events 数组"));
        }
        int accepted = 0;
        int duplicated = 0;
        for (TrackEvent e : request.events()) {
            if (e == null) {
                continue;
            }
            boolean ok = trackStore.ingest(e.expKey(), e.variant(), e.userId(), e.event());
            if (ok) {
                accepted++;
            } else {
                duplicated++;
            }
        }
        return ResponseEntity.ok(new TrackResponse(accepted, duplicated));
    }

    // ==================== 3. 实验看板 ====================

    /** 指标看板：UV / CTR / SRM 卡方 / 双比例 Z 检验 / 最小样本量 */
    @GetMapping("/api/ab/stats")
    public ResponseEntity<?> stats(@RequestParam(value = "expKey", required = false) String expKey) {
        Experiment exp = holder.get().getExperiment(expKey);
        if (exp == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(OkResponse.error("实验不存在: " + expKey));
        }

        List<Map<String, Object>> rows = trackStore.aggregate(expKey);
        Map<String, Map<String, Object>> byVariant = new HashMap<>();
        for (Map<String, Object> row : rows) {
            byVariant.put((String) row.get("variant"), row);
        }

        // 找对照组与实验组（取权重最大的非对照变体作为主实验组）
        Variant control = exp.controlVariant();
        Variant treatment = null;
        for (Variant v : exp.getVariants()) {
            if (!v.isControl() && (treatment == null || v.getWeight() > treatment.getWeight())) {
                treatment = v;
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("expKey", expKey);
        resp.put("name", exp.getName());
        resp.put("status", exp.getStatus().getDesc());
        resp.put("trafficPercent", exp.getCapacity() == 0 ? 0
                : exp.getTraffic() * 100 / exp.getCapacity());
        resp.put("variants", rows);
        resp.put("totalEvents", trackStore.getTotalEvents());
        resp.put("duplicatedEvents", trackStore.getDuplicatedEvents());

        if (treatment != null) {
            Map<String, Object> cRow = byVariant.get(control.getKey());
            Map<String, Object> tRow = byVariant.get(treatment.getKey());
            long cUv = cRow == null ? 0 : (Long) cRow.get("exposureUv");
            long tUv = tRow == null ? 0 : (Long) tRow.get("exposureUv");
            long cClk = cRow == null ? 0 : (Long) cRow.get("clickUv");
            long tClk = tRow == null ? 0 : (Long) tRow.get("clickUv");

            // SRM：期望按变体权重比例进组
            int totalWeight = 0;
            for (Variant v : exp.getVariants()) {
                totalWeight += v.getWeight();
            }
            double[] srm = Stats.chiSquareSrm(
                    new long[]{cUv, tUv},
                    new double[]{(double) control.getWeight() / totalWeight,
                            (double) treatment.getWeight() / totalWeight});
            Map<String, Object> srmInfo = new LinkedHashMap<>();
            srmInfo.put("chi2", round(srm[0]));
            srmInfo.put("pValue", srm[1]);
            srmInfo.put("alarm", srm[1] < 0.001 && (cUv + tUv) > 200);
            resp.put("srm", srmInfo);

            // 双比例 Z 检验：实验组转化率是否显著提升
            double[] z = Stats.twoProportionZTest(cClk, cUv, tClk, tUv);
            Map<String, Object> zInfo = new LinkedHashMap<>();
            zInfo.put("zScore", round(z[0]));
            zInfo.put("pValue", round(z[1]));
            zInfo.put("liftPercent", round(z[2]));
            zInfo.put("significant", z[1] < 0.05 && (cUv + tUv) > 0);
            resp.put("zTest", zInfo);

            // 最小样本量：基线取对照组 CTR，MDE 取 10% 相对提升
            double baseline = cUv == 0 ? 0.03 : (double) cClk / cUv;
            resp.put("requiredSampleSizePerGroup",
                    baseline > 0 ? Stats.requiredSampleSize(baseline, 0.10) : -1);
        }
        return ResponseEntity.ok(resp);
    }

    // ==================== 4. 管理面板 ====================

    /** 热更放量：扩量只改阈值不重新哈希，老用户不跳组 */
    @PostMapping("/api/ab/admin/traffic")
    public OkResponse traffic(@RequestBody Traffic req) {
        Snapshot s = holder.updateTraffic(req.expKey(), req.percent());
        return OkResponse.ok("实验 " + req.expKey() + " 放量调整为 " + req.percent()
                + "%（新版本 v" + s.getVersion() + "，老用户不跳组）");
    }

    /** Kill Switch / 恢复 */
    @PostMapping("/api/ab/admin/status")
    public OkResponse status(@RequestBody Status req) {
        boolean running = Boolean.TRUE.equals(req.running());
        Snapshot s = holder.setStatus(req.expKey(), running);
        return OkResponse.ok("实验 " + req.expKey()
                + (running ? " 已恢复运行" : " 已一键暂停（Kill Switch，全量回落对照组）")
                + "（新版本 v" + s.getVersion() + "）");
    }

    /** 白名单强制分组（variant 传空串表示移除） */
    @PostMapping("/api/ab/admin/whitelist")
    public OkResponse whitelist(@RequestBody Whitelist req) {
        String variant = (req.variant() == null || req.variant().isEmpty()) ? null : req.variant();
        Snapshot s = holder.setWhitelist(req.expKey(), req.userId(), variant);
        return OkResponse.ok("白名单已更新（新版本 v" + s.getVersion() + "）");
    }

    /** 模拟 N 个虚拟用户走完整"分流→曝光→点击"链路 */
    @PostMapping("/api/ab/admin/simulate")
    public OkResponse simulate(@RequestBody(required = false) Simulate req) {
        int users = (req == null || req.users() == null) ? 1000 : req.users();
        users = Math.max(1, Math.min(users, 200000));
        int events = simulator.simulate(holder.get(), users);
        return OkResponse.ok("已模拟 " + users + " 个用户，写入 " + events + " 条事件");
    }

    /** 清空埋点数据 */
    @PostMapping("/api/ab/admin/reset")
    public OkResponse reset() {
        trackStore.reset();
        return OkResponse.ok("埋点数据已清空");
    }

    /**
     * 实验配置列表：管理面板用它渲染状态徽标、放量比例，
     * 并结合 state 下发的各层桶号解释"当前用户为什么没命中"。
     */
    @GetMapping("/api/ab/admin/experiments")
    public List<ExperimentView> experiments() {
        List<ExperimentView> views = new ArrayList<>();
        for (Layer layer : holder.get().getLayers()) {
            for (Experiment exp : layer.getExperiments()) {
                int percent = exp.getCapacity() == 0 ? 0
                        : exp.getTraffic() * 100 / exp.getCapacity();
                views.add(new ExperimentView(exp.getExpKey(), exp.getName(),
                        layer.getLayerKey(), exp.getStatus().name(), exp.getStatus().getDesc(),
                        exp.isRunning(), percent, exp.getRangeStart(), exp.getCapacity(),
                        exp.getTraffic()));
            }
        }
        return views;
    }

    // ==================== 工具 ====================

    private static double round(double v) {
        return Math.round(v * 10000) / 10000.0;
    }
}
