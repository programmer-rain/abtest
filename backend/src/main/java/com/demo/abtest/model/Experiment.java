package com.demo.abtest.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 实验：一个变量的多个版本 + 层内桶位区间 + 放量阈值 + 白名单。
 *
 * 教学说明（桶位三件套）：
 *  - rangeStart / capacity：实验在所属层里【预留】的桶位区间
 *    [rangeStart, rangeStart + capacity)。同层实验的预留区间互不重叠 → 互斥；
 *  - traffic：当前放量桶数（0 <= traffic <= capacity）。
 *    只有桶号落在 [rangeStart, rangeStart + traffic) 的用户才真正进实验。
 *    【扩量只改 traffic，不重新哈希，老用户天然不跳组】——分流一致性的关键设计；
 *  - 变体按权重切分放量区间：如 50:50 时，前半给 control，后半给 treatment；
 *  - whitelist：白名单强制分组（QA 验收、定向灰度），优先级最高；
 *  - configVersion：配置版本号，每次修改 +1，前端缓存失效与曝光归因都靠它。
 */
public final class Experiment {

    private final String expKey;
    private final String layerKey;
    private final String name;
    private final ExperimentStatus status;
    private final int rangeStart;
    private final int capacity;
    private final int traffic;
    private final List<Variant> variants;
    private final Map<String, String> whitelist;
    private final long configVersion;

    public Experiment(String expKey, String layerKey, String name, ExperimentStatus status,
                      int rangeStart, int capacity, int traffic, List<Variant> variants,
                      Map<String, String> whitelist, long configVersion) {
        this.expKey = expKey;
        this.layerKey = layerKey;
        this.name = name;
        this.status = status;
        this.rangeStart = rangeStart;
        this.capacity = capacity;
        this.traffic = traffic;
        this.variants = Collections.unmodifiableList(new ArrayList<Variant>(variants));
        this.whitelist = Collections.unmodifiableMap(new LinkedHashMap<String, String>(whitelist));
        this.configVersion = configVersion;
    }

    /** 复制并修改部分字段（不可变对象的"更新"姿势：永远返回新实例） */
    public Experiment with(ExperimentStatus newStatus, int newTraffic,
                           Map<String, String> newWhitelist, long newVersion) {
        return new Experiment(expKey, layerKey, name, newStatus, rangeStart, capacity,
                newTraffic, variants, newWhitelist, newVersion);
    }

    public boolean isRunning() {
        return status == ExperimentStatus.RUNNING;
    }

    /** 桶号是否落在"预留区间"内（决定该用户有没有资格参与本实验） */
    public boolean inRange(int bucket) {
        return bucket >= rangeStart && bucket < rangeStart + capacity;
    }

    /** 桶号是否落在"已放量"区间内（决定该用户现在是否真正进实验） */
    public boolean inTraffic(int bucket) {
        return bucket >= rangeStart && bucket < rangeStart + traffic;
    }

    /** 白名单命中返回强制变体，否则返回 null */
    public String whitelistVariant(String unitId) {
        return whitelist.get(unitId);
    }

    public Variant findVariant(String variantKey) {
        for (Variant v : variants) {
            if (v.getKey().equals(variantKey)) {
                return v;
            }
        }
        return null;
    }

    public Variant controlVariant() {
        for (Variant v : variants) {
            if (v.isControl()) {
                return v;
            }
        }
        return variants.get(0);
    }

    public String getExpKey() {
        return expKey;
    }

    public String getLayerKey() {
        return layerKey;
    }

    public String getName() {
        return name;
    }

    public ExperimentStatus getStatus() {
        return status;
    }

    public int getRangeStart() {
        return rangeStart;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getTraffic() {
        return traffic;
    }

    public List<Variant> getVariants() {
        return variants;
    }

    public Map<String, String> getWhitelist() {
        return whitelist;
    }

    public long getConfigVersion() {
        return configVersion;
    }
}
