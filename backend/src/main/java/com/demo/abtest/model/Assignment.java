package com.demo.abtest.model;

import java.util.Collections;
import java.util.Map;

/**
 * 分流结果：某用户在某实验里命中的变体。
 *
 * 教学说明：
 *  - bucket 一并返回，方便教学演示时直观看到"用户在每一层落在哪个桶"；
 *  - configVersion 随结果下发：曝光埋点要带上它，
 *    分析时按"曝光时刻的配置快照"归因，而不是用当前配置——否则中途改流量会归因错乱。
 */
public final class Assignment {

    private final String expKey;
    private final String layerKey;
    private final String variantKey;
    private final int bucket;
    private final boolean whitelisted;
    private final long configVersion;
    private final Map<String, Object> payload;

    public Assignment(String expKey, String layerKey, String variantKey, int bucket,
                      boolean whitelisted, long configVersion, Map<String, Object> payload) {
        this.expKey = expKey;
        this.layerKey = layerKey;
        this.variantKey = variantKey;
        this.bucket = bucket;
        this.whitelisted = whitelisted;
        this.configVersion = configVersion;
        this.payload = payload == null
                ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(payload);
    }

    public String getExpKey() {
        return expKey;
    }

    public String getLayerKey() {
        return layerKey;
    }

    public String getVariantKey() {
        return variantKey;
    }

    public int getBucket() {
        return bucket;
    }

    public boolean isWhitelisted() {
        return whitelisted;
    }

    public long getConfigVersion() {
        return configVersion;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }
}
