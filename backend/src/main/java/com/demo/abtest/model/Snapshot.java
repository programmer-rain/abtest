package com.demo.abtest.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置快照：某一时刻全部实验配置的【不可变】视图。
 *
 * 教学说明（Copy-On-Write 思想）：
 *  - 分流接口是"万级 QPS 读、极少写"的场景，加锁是浪费；
 *  - 每次配置变更都构建一个全新快照，然后用 AtomicReference 原子替换引用；
 *  - 读线程永远看到一个完整一致的快照，绝不会读到"改了一半"的配置。
 */
public final class Snapshot {

    public static final Snapshot EMPTY = new Snapshot(0, Collections.<Layer>emptyList());

    private final long version;
    private final List<Layer> layers;
    private final Map<String, Experiment> byExpKey;

    public Snapshot(long version, List<Layer> layers) {
        this.version = version;
        this.layers = Collections.unmodifiableList(new ArrayList<Layer>(layers));
        Map<String, Experiment> index = new LinkedHashMap<String, Experiment>();
        for (Layer layer : layers) {
            for (Experiment exp : layer.getExperiments()) {
                index.put(exp.getExpKey(), exp);
            }
        }
        this.byExpKey = Collections.unmodifiableMap(index);
    }

    public long getVersion() {
        return version;
    }

    public List<Layer> getLayers() {
        return layers;
    }

    public Experiment getExperiment(String expKey) {
        return byExpKey.get(expKey);
    }

    public Map<String, Experiment> getAllExperiments() {
        return byExpKey;
    }
}
