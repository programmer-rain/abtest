package com.demo.abtest.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 变体（实验里的一个版本，如对照组 control / 实验组 treatment）。
 *
 * 教学说明：
 *  - weight：权重，决定变体在实验放量流量内的占比（如 50:50）；
 *  - payload：下发给前端的配置（组件名/文案/颜色等），前端"参数驱动渲染"全靠它；
 *  - 对象不可变（字段 final + 构造后不暴露写方法），这是快照能无锁并发读的前提。
 */
public final class Variant {

    private final String key;
    private final int weight;
    private final boolean control;
    private final Map<String, Object> payload;

    public Variant(String key, int weight, boolean control, Map<String, Object> payload) {
        this.key = key;
        this.weight = weight;
        this.control = control;
        this.payload = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(payload));
    }

    public String getKey() {
        return key;
    }

    public int getWeight() {
        return weight;
    }

    public boolean isControl() {
        return control;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }
}
