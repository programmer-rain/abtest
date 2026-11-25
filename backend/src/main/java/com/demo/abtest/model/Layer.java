package com.demo.abtest.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 流量层（Layer）：一组互斥实验的集合。
 *
 * 教学说明（分层正交是整个系统的灵魂）：
 *  - 同层互斥：一个用户在一层里最多命中一个实验（桶区间互不重叠）；
 *  - 跨层正交：不同层用不同 salt 哈希，同一用户在每层的桶号互相独立，
 *    于是"在 UI 层命中 A 的人"在算法层仍然是均匀分布的，实验之间不互相污染；
 *  - 层盐（salt）就是正交的全部秘密：MurmurHash 的雪崩效应保证换盐即"重新洗牌"。
 */
public final class Layer {

    private final String layerKey;
    private final String salt;
    private final List<Experiment> experiments;

    public Layer(String layerKey, String salt, List<Experiment> experiments) {
        this.layerKey = layerKey;
        this.salt = salt;
        this.experiments = Collections.unmodifiableList(new ArrayList<Experiment>(experiments));
    }

    /** 复制并替换实验列表（快照原子替换用） */
    public Layer withExperiments(List<Experiment> newExperiments) {
        return new Layer(layerKey, salt, newExperiments);
    }

    public String getLayerKey() {
        return layerKey;
    }

    public String getSalt() {
        return salt;
    }

    public List<Experiment> getExperiments() {
        return experiments;
    }
}
