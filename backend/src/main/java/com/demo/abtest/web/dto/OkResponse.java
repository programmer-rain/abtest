package com.demo.abtest.web.dto;

/** 通用结果响应：管理接口成功/失败统一返回 {ok, message} */
public record OkResponse(boolean ok, String message) {

    public static OkResponse ok(String message) {
        return new OkResponse(true, message);
    }

    public static OkResponse error(String message) {
        return new OkResponse(false, message);
    }
}
