package com.demo.abtest.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 实验状态机：严谨的状态流转，防止运营误操作。
 *
 * 教学说明：
 *  - 状态流转用"白名单表"显式声明，而不是散落在业务代码里的 if-else；
 *  - Kill Switch（一键止血）走的就是 RUNNING -> PAUSED 这条边，
 *    暂停后分流引擎会把全量用户回落对照组。
 */
public enum ExperimentStatus {

    DRAFT(0, "草稿"),
    RUNNING(1, "运行中"),
    PAUSED(2, "已暂停"),
    FINISHED(3, "已结束"),
    DELETED(4, "已删除");

    private final int code;
    private final String desc;

    /** 合法状态流转表 */
    private static final Map<ExperimentStatus, Set<ExperimentStatus>> TRANSITIONS;

    static {
        Map<ExperimentStatus, Set<ExperimentStatus>> m = new EnumMap<ExperimentStatus, Set<ExperimentStatus>>(ExperimentStatus.class);
        m.put(DRAFT, EnumSet.of(RUNNING, DELETED));
        m.put(RUNNING, EnumSet.of(PAUSED, FINISHED));
        m.put(PAUSED, EnumSet.of(RUNNING, FINISHED));
        m.put(FINISHED, EnumSet.noneOf(ExperimentStatus.class));
        m.put(DELETED, EnumSet.noneOf(ExperimentStatus.class));
        TRANSITIONS = Collections.unmodifiableMap(m);
    }

    ExperimentStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public boolean canTransitTo(ExperimentStatus target) {
        Set<ExperimentStatus> allowed = TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }
}
