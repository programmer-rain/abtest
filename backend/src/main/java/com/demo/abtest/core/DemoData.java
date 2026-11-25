package com.demo.abtest.core;

import com.demo.abtest.model.Experiment;
import com.demo.abtest.model.ExperimentStatus;
import com.demo.abtest.model.Layer;
import com.demo.abtest.model.Snapshot;
import com.demo.abtest.model.Variant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示数据工厂：构建初始实验配置。
 *
 * 两层结构，用于直观演示"同层互斥、跨层正交"：
 *
 *   ui_layer（盐 ui_v1）—— 层内两个实验互斥地瓜分 10000 个桶
 *     ├─ pay_btn       支付按钮改版  预留 [0,    5000)，动态组件类实验（下发组件名）
 *     └─ banner_style  首页横幅文案  预留 [5000, 10000)，参数驱动类实验（下发文案/颜色）
 *   algo_layer（盐 algo_v1）—— 与 UI 层正交，同一用户两层各自独立分桶
 *     └─ reco_feed     推荐卡片改版  预留 [0, 10000)
 */
public final class DemoData {

    private DemoData() {
    }

    public static Snapshot buildInitial() {
        Map<String, Object> payOld = new LinkedHashMap<String, Object>();
        payOld.put("componentKey", "PayBtnOld");
        Map<String, Object> payNew = new LinkedHashMap<String, Object>();
        payNew.put("componentKey", "PayBtnNew");

        Map<String, Object> bannerA = new LinkedHashMap<String, Object>();
        bannerA.put("title", "双十一全场五折起");
        bannerA.put("themeColor", "#8c8c8c");
        bannerA.put("btnText", "去逛逛");
        Map<String, Object> bannerB = new LinkedHashMap<String, Object>();
        bannerB.put("title", "今晚 0 点 限时半价");
        bannerB.put("themeColor", "#ff4d4f");
        bannerB.put("btnText", "马上抢");

        Map<String, Object> recoOld = new LinkedHashMap<String, Object>();
        recoOld.put("componentKey", "RecoCardOld");
        Map<String, Object> recoNew = new LinkedHashMap<String, Object>();
        recoNew.put("componentKey", "RecoCardNew");

        Experiment payBtn = new Experiment(
                "pay_btn", "ui_layer", "支付按钮改版",
                ExperimentStatus.RUNNING,
                0, 5000, 5000, // 预留 [0,5000)，当前全量放开
                Arrays.asList(
                        new Variant("control", 50, true, payOld),
                        new Variant("treatment", 50, false, payNew)),
                new LinkedHashMap<String, String>(), 1L);

        Experiment bannerStyle = new Experiment(
                "banner_style", "ui_layer", "首页横幅文案",
                ExperimentStatus.RUNNING,
                5000, 5000, 5000, // 预留 [5000,10000)
                Arrays.asList(
                        new Variant("control", 50, true, bannerA),
                        new Variant("treatment", 50, false, bannerB)),
                new LinkedHashMap<String, String>(), 1L);

        Experiment recoFeed = new Experiment(
                "reco_feed", "algo_layer", "推荐卡片改版",
                ExperimentStatus.RUNNING,
                0, 10000, 10000,
                Arrays.asList(
                        new Variant("control", 50, true, recoOld),
                        new Variant("treatment", 50, false, recoNew)),
                new LinkedHashMap<String, String>(), 1L);

        List<Layer> layers = new ArrayList<Layer>();
        layers.add(new Layer("ui_layer", "ui_v1", Arrays.asList(payBtn, bannerStyle)));
        layers.add(new Layer("algo_layer", "algo_v1", Arrays.asList(recoFeed)));
        return new Snapshot(1L, layers);
    }
}
