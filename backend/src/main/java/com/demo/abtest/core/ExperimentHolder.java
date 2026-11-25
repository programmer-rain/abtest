package com.demo.abtest.core;

import com.demo.abtest.model.Experiment;
import com.demo.abtest.model.ExperimentStatus;
import com.demo.abtest.model.Layer;
import com.demo.abtest.model.Snapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 配置持有者：不可变快照 + 原子替换（Copy-On-Write）。
 *
 * 教学说明：
 *  - 读路径：{@link #get()} 无锁、O(1)，读线程永远看到完整一致的快照；
 *  - 写路径：任何修改（调流量/暂停/白名单）都是"构建新快照 → 原子替换引用"，
 *    版本号单调递增，旧快照随 GC 回收；
 *  - 对应到生产环境：写路径由运营后台 + Redis Pub/Sub 广播触发，
 *    这里简化为 HTTP 管理接口直接调用，原理完全一致。
 */
public final class ExperimentHolder {

    private final AtomicReference<Snapshot> ref;
    private final AtomicLong versionGen;

    public ExperimentHolder(Snapshot initial) {
        this.ref = new AtomicReference<Snapshot>(initial);
        this.versionGen = new AtomicLong(initial.getVersion());
    }

    /** 读路径：无锁 */
    public Snapshot get() {
        return ref.get();
    }

    /**
     * 调整实验放量（占预留容量的 0~100%）。
     * 扩量只改阈值，不重新哈希 → 老用户桶位不动，天然不跳组。
     */
    public synchronized Snapshot updateTraffic(String expKey, final int percent) {
        return mutateExperiment(expKey, new ExpMutator() {
            @Override
            public Experiment apply(Experiment exp, long newVersion) {
                int p = Math.max(0, Math.min(100, percent));
                int traffic = exp.getCapacity() * p / 100;
                return exp.with(exp.getStatus(), traffic, exp.getWhitelist(), newVersion);
            }
        });
    }

    /** Kill Switch / 恢复：一键暂停（全量回落对照组）或恢复运行 */
    public synchronized Snapshot setStatus(String expKey, boolean running) {
        return mutateExperiment(expKey, new ExpMutator() {
            @Override
            public Experiment apply(Experiment exp, long newVersion) {
                ExperimentStatus target = running ? ExperimentStatus.RUNNING : ExperimentStatus.PAUSED;
                if (!exp.getStatus().canTransitTo(target)) {
                    throw new IllegalStateException(
                            "非法状态流转: " + exp.getStatus() + " -> " + target);
                }
                return exp.with(target, exp.getTraffic(), exp.getWhitelist(), newVersion);
            }
        });
    }

    /** 白名单维护：强制某用户命中指定变体（传 null 变体表示移除） */
    public synchronized Snapshot setWhitelist(String expKey, String userId, String variantKey) {
        return mutateExperiment(expKey, new ExpMutator() {
            @Override
            public Experiment apply(Experiment exp, long newVersion) {
                Map<String, String> wl = new LinkedHashMap<String, String>(exp.getWhitelist());
                if (variantKey == null) {
                    wl.remove(userId);
                } else {
                    wl.put(userId, variantKey);
                }
                return exp.with(exp.getStatus(), exp.getTraffic(), wl, newVersion);
            }
        });
    }

    private interface ExpMutator {
        Experiment apply(Experiment exp, long newVersion);
    }

    /** 通用的"找到实验 → 应用变更 → 重建整棵快照树 → 原子替换"流程 */
    private Snapshot mutateExperiment(String expKey, ExpMutator mutator) {
        Snapshot old = ref.get();
        Experiment target = old.getExperiment(expKey);
        if (target == null) {
            throw new IllegalArgumentException("实验不存在: " + expKey);
        }
        long newVersion = versionGen.incrementAndGet();
        Experiment updated = mutator.apply(target, newVersion);

        List<Layer> newLayers = new ArrayList<Layer>();
        for (Layer layer : old.getLayers()) {
            List<Experiment> exps = new ArrayList<Experiment>();
            for (Experiment e : layer.getExperiments()) {
                exps.add(e.getExpKey().equals(expKey) ? updated : e);
            }
            newLayers.add(layer.withExperiments(exps));
        }
        Snapshot next = new Snapshot(newVersion, newLayers);
        ref.set(next); // 原子替换：读写不打架
        return next;
    }
}
