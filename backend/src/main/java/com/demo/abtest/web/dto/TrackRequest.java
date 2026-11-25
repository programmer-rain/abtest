package com.demo.abtest.web.dto;

import java.util.List;

/** /api/ab/track 的请求体：批量埋点事件 */
public record TrackRequest(List<TrackEvent> events) {

    /** 单条埋点：实验 + 变体 + 用户 + 事件类型（expose / click） */
    public record TrackEvent(String expKey, String variant, String userId, String event) {
    }
}
