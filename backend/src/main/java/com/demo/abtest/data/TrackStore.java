package com.demo.abtest.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 埋点存储（教学简化版"Kafka + Flink + ClickHouse"）。
 *
 * 教学说明：
 *  - 生产链路是 sendBeacon → 网关 → Kafka 削峰 → Flink 去重聚合 → ClickHouse；
 *    这里用内存结构等价模拟，重点演示两个关键点：
 *      1.【双层去重】同一 (用户, 实验, 事件, 变体) 只计一次，防止曝光虚高；
 *      2.【指标口径】UV 按用户去重，CTR = 点击UV / 曝光UV。
 */
public final class TrackStore {

    /** 事件：expKey|variantKey|userId|eventType 去重键 */
    private final Set<String> dedup = ConcurrentHashMap.newKeySet();

    /** 每个实验每个变体的计数器 */
    private final Map<String, VariantCounter> counters = new ConcurrentHashMap<String, VariantCounter>();

    private final AtomicLong totalEvents = new AtomicLong();
    private final AtomicLong duplicatedEvents = new AtomicLong();

    public static final class VariantCounter {
        public final Set<String> exposureUsers = ConcurrentHashMap.newKeySet();
        public final Set<String> clickUsers = ConcurrentHashMap.newKeySet();
    }

    /**
     * 写入一条事件。
     *
     * @return true=接收，false=被去重丢弃
     */
    public boolean ingest(String expKey, String variantKey, String userId, String eventType) {
        if (expKey == null || variantKey == null || userId == null || eventType == null) {
            return false;
        }
        totalEvents.incrementAndGet();
        String dedupKey = expKey + "|" + variantKey + "|" + userId + "|" + eventType;
        if (!dedup.add(dedupKey)) {
            duplicatedEvents.incrementAndGet();
            return false; // 幂等：重复曝光/重复点击不重复计数
        }
        VariantCounter c = counters.computeIfAbsent(expKey + "|" + variantKey,
                k -> new VariantCounter());
        if ("expose".equals(eventType)) {
            c.exposureUsers.add(userId);
        } else if ("click".equals(eventType)) {
            c.clickUsers.add(userId);
        }
        return true;
    }

    /** 汇总某实验所有变体的指标 */
    public List<Map<String, Object>> aggregate(String expKey) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, VariantCounter> e : counters.entrySet()) {
            String[] parts = e.getKey().split("\\|", 2);
            if (!parts[0].equals(expKey)) {
                continue;
            }
            VariantCounter c = e.getValue();
            long exposure = c.exposureUsers.size();
            long click = c.clickUsers.size();
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("variant", parts[1]);
            row.put("exposureUv", exposure);
            row.put("clickUv", click);
            row.put("ctr", exposure == 0 ? 0.0 : (double) click / exposure);
            rows.add(row);
        }
        return rows;
    }

    public long getTotalEvents() {
        return totalEvents.get();
    }

    public long getDuplicatedEvents() {
        return duplicatedEvents.get();
    }

    public void reset() {
        dedup.clear();
        counters.clear();
        totalEvents.set(0);
        duplicatedEvents.set(0);
    }
}
