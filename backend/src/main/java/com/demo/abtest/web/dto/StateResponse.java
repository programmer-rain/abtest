package com.demo.abtest.web.dto;

import java.util.Map;

/**
 * /api/ab/state 的响应：配置版本号 + 全部实验的命中结果。
 *
 * 教学说明：JDK 17 record 天生不可变，字段顺序即 JSON 字段顺序，
 * 比手写 POJO + getter 少写一大半样板代码。
 */
public record StateResponse(long version, Map<String, AssignmentView> assignments,
                            Map<String, Integer> buckets) {

    /** 单个实验的命中详情（字段与旧版 JSON 契约完全一致） */
    public record AssignmentView(String variant, String layer, int bucket,
                                 boolean whitelisted, long configVersion,
                                 Map<String, Object> payload) {
    }
}
