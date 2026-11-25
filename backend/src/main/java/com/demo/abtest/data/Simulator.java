package com.demo.abtest.data;

import com.demo.abtest.core.AssignService;
import com.demo.abtest.model.Assignment;
import com.demo.abtest.model.Snapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 流量模拟器：一键生成 N 个虚拟用户，走完整的"分流 → 曝光 → 点击"链路。
 *
 * 教学说明：
 *  - 给每个实验的对照组/实验组设定了【真实点击率】（实验组略高），
 *    这样在看板上能直观看到 UV 分布、CTR 差异、Z 检验 p 值、SRM 是否健康；
 *  - 所有事件同样经过 TrackStore 的去重逻辑，和真实上报链路完全一致。
 */
public final class Simulator {

    /** 每个实验每个变体的"真实"点击率（演示用的人造数据） */
    private static final Map<String, Double> TRUE_CTR = new HashMap<String, Double>();

    static {
        TRUE_CTR.put("pay_btn|control", 0.032);
        TRUE_CTR.put("pay_btn|treatment", 0.040);   // 相对提升 25%，显著
        TRUE_CTR.put("banner_style|control", 0.050);
        TRUE_CTR.put("banner_style|treatment", 0.052); // 提升 4%，样本少时不显著
        TRUE_CTR.put("reco_feed|control", 0.080);
        TRUE_CTR.put("reco_feed|treatment", 0.088);
    }

    private final AssignService assignService;
    private final TrackStore trackStore;
    private final AtomicLong userSeq = new AtomicLong(0);
    private final Random random = new Random(42);

    public Simulator(AssignService assignService, TrackStore trackStore) {
        this.assignService = assignService;
        this.trackStore = trackStore;
    }

    /**
     * @param users 模拟用户数
     * @return 实际写入的事件数
     */
    public int simulate(Snapshot snapshot, int users) {
        int events = 0;
        for (int i = 0; i < users; i++) {
            String uid = "sim_user_" + userSeq.incrementAndGet();
            List<Assignment> assignments = assignService.assign(snapshot, uid, null);
            for (Assignment a : assignments) {
                // 曝光：进组用户 90% 会真实看到（模拟部分用户没滚到对应楼层）
                if (random.nextDouble() < 0.9) {
                    if (trackStore.ingest(a.getExpKey(), a.getVariantKey(), uid, "expose")) {
                        events++;
                        // 点击：按该变体的真实点击率掷骰子
                        double ctr = TRUE_CTR.get(a.getExpKey() + "|" + a.getVariantKey());
                        if (ctr > 0 && random.nextDouble() < ctr) {
                            if (trackStore.ingest(a.getExpKey(), a.getVariantKey(), uid, "click")) {
                                events++;
                            }
                        }
                    }
                }
            }
        }
        return events;
    }
}
