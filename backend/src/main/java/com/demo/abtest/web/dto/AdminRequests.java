package com.demo.abtest.web.dto;

/**
 * 管理面板的四个请求体，集中放在这里方便对照教学。
 *
 * 教学说明：包装类型（Integer/Boolean）而不是基本类型——
 * 字段缺失时 Jackson 绑定为 null，由业务层决定兜底策略，
 * 避免"缺字段直接反序列化成 0/false"这种隐蔽 bug。
 */
public final class AdminRequests {

    private AdminRequests() {
    }

    /** 热更放量：percent 为占预留容量的 0~100% */
    public record Traffic(String expKey, Integer percent) {
    }

    /** Kill Switch：running=false 一键暂停，全量回落对照组 */
    public record Status(String expKey, Boolean running) {
    }

    /** 白名单：variant 传空串表示移除该用户 */
    public record Whitelist(String expKey, String userId, String variant) {
    }

    /** 流量模拟：users 缺省 1000，上限 200000 */
    public record Simulate(Integer users) {
    }
}
