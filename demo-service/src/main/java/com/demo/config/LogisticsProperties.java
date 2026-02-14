package com.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 物流模块配置绑定类。
 *
 * 绑定前缀：logistics
 * 示例配置：
 * logistics:
 *   provider: mock
 *   mock:
 *     enabled: true
 *     fast-forward: false
 *   delivery-tracker:
 *     base-url: https://api.tracker.delivery
 *     api-key: xxx
 */
@Data
@Component
@ConfigurationProperties(prefix = "logistics")
public class LogisticsProperties {

    /**
     * 当前启用的物流 provider。
     * 可选值：mock / delivery-tracker
     * 默认值：mock（保证零成本可演示）
     */
    private String provider = "mock";

    /**
     * mock provider 子配置
     */
    private Mock mock = new Mock();

    /**
     * 轨迹同步策略子配置（预留给定时同步任务）
     */
    private Sync sync = new Sync();

    /**
     * 第三方 delivery-tracker 子配置
     */
    private DeliveryTracker deliveryTracker = new DeliveryTracker();

    @Data
    public static class Mock {
        /**
         * 是否启用 mock provider（保留开关便于灰度）
         */
        private boolean enabled = true;
        /**
         * 是否使用“快进模式”。
         * true：1/3/5/7 分钟快速推进轨迹，方便演示
         * false：按常规节奏推进轨迹
         */
        private boolean fastForward = false;

        /**
         * 固定“当前时间”（可选）。
         *
         * 作用：
         * - 回归时把 now 固定住，保证轨迹完全可复现
         * - 演示时保持为空，使用真实当前时间，轨迹会自然推进
         *
         * 格式建议：yyyy-MM-dd HH:mm:ss（例如：2026-02-12 10:30:00）
         */
        private String fixedNow;

        /**
         * 快进模式下的节点分钟计划（默认 0/2/4/6）。
         * 含义：相对于发货时间（或 mock 基线时间）的分钟偏移。
         */
        private List<Integer> fastForwardMinutes = Arrays.asList(0, 2, 4, 6);

        /**
         * 常规模式下的节点分钟计划（默认 0/30/120/240）。
         */
        private List<Integer> normalMinutes = Arrays.asList(0, 30, 120, 240);
    }

    @Data
    public static class Sync {
        /**
         * 轨迹同步间隔（分钟），当前主要用于后续定时任务配置
         */
        private int intervalMinutes = 30;
    }

    @Data
    public static class DeliveryTracker {
        /**
         * 是否启用 delivery-tracker provider。
         * 关闭时即便 logistics.provider 配成 delivery-tracker，也会自动回退 mock。
         */
        private boolean enabled = false;

        /**
         * 第三方 API 基础地址
         */
        private String baseUrl = "https://api.tracker.delivery";
        /**
         * 第三方 API Key（空值时 provider 自动降级返回空轨迹）
         */
        private String apiKey;
    }
}
