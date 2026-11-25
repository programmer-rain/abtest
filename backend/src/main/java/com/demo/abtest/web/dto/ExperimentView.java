package com.demo.abtest.web.dto;

/**
 * /api/ab/admin/experiments 的响应元素：实验配置的"体检单"。
 *
 * 教学说明：
 *  - 管理面板需要展示每个实验的实时状态（运行中/已暂停）与放量比例，
 *    还要解释"当前用户为什么没命中"——这要求把桶区间（rangeStart/capacity/traffic）
 *    一并下发，前端结合各层桶号就能给出精确原因；
 *  - 这是新增接口，不影响原有 API 契约。
 */
public record ExperimentView(String expKey,
                              String name,
                              String layerKey,
                              String status,
                              String statusDesc,
                              boolean running,
                              int trafficPercent,
                              int rangeStart,
                              int capacity,
                              int traffic) {
}
