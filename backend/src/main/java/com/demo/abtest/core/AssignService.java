package com.demo.abtest.core;

import com.demo.abtest.model.Assignment;
import com.demo.abtest.model.Experiment;
import com.demo.abtest.model.Layer;
import com.demo.abtest.model.Snapshot;
import com.demo.abtest.model.Variant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分流引擎：整个系统的命门。
 *
 * 决策优先级（顺序不能乱）：
 *   ① 白名单（QA 验收/定向灰度/一键回滚）
 *   ② 实验状态过滤（暂停/结束的实验直接跳过）
 *   ③ 桶位区间过滤：不在预留区间 → 看下一个实验；在预留区间但未放量 → 该用户被"占用"，
 *      同样不再看后面的实验（层内互斥），前端降级对照组
 *   ④ 变体按权重前缀和命中
 *
 * 教学说明：
 *  - 同层多个实验靠【预留桶位区间互不重叠】实现互斥：命中一个即 break，绝不继续匹配；
 *  - 全程纯内存计算，无任何 IO，单次分流是微秒级。
 */
public final class AssignService {

    /**
     * 一次请求算完所有层，批量返回全部命中结果。
     *
     * @param userId   登录用户 ID，可为 null（未登录）
     * @param deviceId 设备 ID，未登录时的兜底分流主体
     */
    public List<Assignment> assign(Snapshot snapshot, String userId, String deviceId) {
        List<Assignment> result = new ArrayList<Assignment>();
        // 分流主体：登录用户优先，未登录退化为设备 ID（禁止用 IP 分流：不稳定且侵犯隐私）
        String unit = (userId != null && userId.length() > 0) ? userId : deviceId;
        if (unit == null || unit.length() == 0) {
            return result;
        }

        for (Layer layer : snapshot.getLayers()) {
            // 关键：哈希输入带层盐。同一用户在不同层的桶号互相独立 → 跨层正交
            int bucket = Bucketer.bucket(unit, layer.getSalt());

            for (Experiment exp : layer.getExperiments()) {
                // 不在本实验预留区间 → 看同层下一个实验
                if (!exp.inRange(bucket)) {
                    continue;
                }

                // ① 白名单最高优先（不受放量与状态限制，验收/回滚专用）
                String forced = exp.whitelistVariant(unit);
                if (forced != null) {
                    Variant v = exp.findVariant(forced);
                    if (v != null) {
                        result.add(toAssignment(exp, v, bucket, true));
                        break; // 层内互斥，命中即止
                    }
                }

                // ② 状态过滤：暂停/结束的实验不参与分流（前端降级对照组）
                if (!exp.isRunning()) {
                    break; // 桶位已被该实验占用，不能流给同层后面的实验
                }

                // ③ 在预留区间但未放量：用户被占用，等后续放量，当前降级对照组
                if (!exp.inTraffic(bucket)) {
                    break;
                }

                // ④ 权重前缀和命中变体
                Variant hit = routeByWeight(exp, bucket);
                result.add(toAssignment(exp, hit, bucket, false));
                break; // 层内互斥：一个用户在一层只进一个实验
            }
        }
        return result;
    }

    /**
     * 计算用户在各层的桶号（无论是否命中实验都返回）。
     *
     * 教学说明：
     *  - "为什么我没命中这个实验"是运营后台最高频的疑问。把各层桶号下发给前端，
     *    前端就能结合桶区间给出明确解释（同层互斥 / 未放量 / 已暂停），
     *    而不是让用户对着"页面没变化"瞎猜分流坏了。
     */
    public Map<String, Integer> layerBuckets(Snapshot snapshot, String userId, String deviceId) {
        Map<String, Integer> buckets = new LinkedHashMap<String, Integer>();
        String unit = (userId != null && userId.length() > 0) ? userId : deviceId;
        if (unit == null || unit.length() == 0) {
            return buckets;
        }
        for (Layer layer : snapshot.getLayers()) {
            buckets.put(layer.getLayerKey(), Bucketer.bucket(unit, layer.getSalt()));
        }
        return buckets;
    }

    /**
     * 把放量区间 [rangeStart, rangeStart + traffic) 按权重切给各变体。
     * 例：traffic=5000，control:treatment = 50:50 → 前 2500 桶给 control，后 2500 桶给 treatment。
     */
    private Variant routeByWeight(Experiment exp, int bucket) {
        int totalWeight = 0;
        for (Variant v : exp.getVariants()) {
            totalWeight += v.getWeight();
        }
        int offset = bucket - exp.getRangeStart();
        long scaled = (long) offset * totalWeight;
        int acc = 0;
        for (Variant v : exp.getVariants()) {
            acc += v.getWeight();
            // offset < traffic * (acc / totalWeight) 的整数安全写法（避免浮点误差）
            if (scaled < (long) exp.getTraffic() * acc) {
                return v;
            }
        }
        return exp.controlVariant(); // 理论走不到，兜底对照组
    }

    private Assignment toAssignment(Experiment exp, Variant v, int bucket, boolean whitelisted) {
        return new Assignment(exp.getExpKey(), exp.getLayerKey(), v.getKey(), bucket,
                whitelisted, exp.getConfigVersion(), v.getPayload());
    }
}
