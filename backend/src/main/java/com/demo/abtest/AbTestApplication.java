package com.demo.abtest;

import com.demo.abtest.core.AssignService;
import com.demo.abtest.core.DemoData;
import com.demo.abtest.core.ExperimentHolder;
import com.demo.abtest.data.Simulator;
import com.demo.abtest.data.TrackStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot 启动入口 + Bean 装配。
 *
 * 教学说明：
 *  - 核心引擎（分流/热更/埋点/统计）全部是纯 JDK 代码，不依赖任何 Spring API；
 *    这里用 @Bean 把它们注册成单例——引擎可以脱离框架独立单测，也能整体搬到别的技术栈；
 *  - 生产项目里通常改用 @Service/@Component 注解扫描，装配原理完全一样。
 */
@SpringBootApplication
public class AbTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(AbTestApplication.class, args);
        System.out.println("" +
        "╔════════════════════════════════════════════════════════════╗\n" +
        "║                                                            ║\n" +
        "║   (♥◠‿◠)ﾉﾞ  Rain Boot 启动成功!  ლ(´ڡ`ლ)ﾞ                  ║\n" +
        "║                                                            ║\n" +
        "║   ┌────────────────────────────────────────────────────┐   ║\n" +
        "║   │  📱 微信公众号 : Rain的Java大神之路                    │  ║\n" +
        "║   │  🌐 技术博客   : javadashen.com                     │   ║\n" +
        "║   │  📖 专注Java技术分享与实战经验                         │  ║\n" +
        "║   └────────────────────────────────────────────────────┘  ║\n" +
        "║                                                           ║\n" +
        "║   ☕ Code is poetry, Java is art.                         ║\n" +
        "║   ✨ May the source be with you!                          ║\n" +
        "║                                                            ║\n" +
        "╚════════════════════════════════════════════════════════════╝");
    }

    /** 配置持有者：启动时用"两层三实验"构建初始不可变快照 */
    @Bean
    public ExperimentHolder experimentHolder() {
        return new ExperimentHolder(DemoData.buildInitial());
    }

    /** 分流引擎：无状态，全局单例 */
    @Bean
    public AssignService assignService() {
        return new AssignService();
    }

    /** 埋点存储：内存版"Kafka + Flink + ClickHouse" */
    @Bean
    public TrackStore trackStore() {
        return new TrackStore();
    }

    /** 流量模拟器：一键灌入 N 个虚拟用户走完整链路 */
    @Bean
    public Simulator simulator(AssignService assignService, TrackStore trackStore) {
        return new Simulator(assignService, trackStore);
    }
}
