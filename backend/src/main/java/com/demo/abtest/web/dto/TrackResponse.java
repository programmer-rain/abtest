package com.demo.abtest.web.dto;

/** /api/ab/track 的响应：接收条数 + 被幂等去重丢弃的条数 */
public record TrackResponse(int accepted, int duplicated) {
}
